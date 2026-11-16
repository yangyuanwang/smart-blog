// 统一 API 客户端：解剖后端 Result{code,message,data} 结构，
// 401 时尝试用 refresh 令牌换新后重放一次请求。
export interface ApiError extends Error {
  code: number
}

/** 持久化的浏览器会话标识：浏览量/点赞去重依赖 */
export function getSessionId (): string {
  if (import.meta.client) {
    let sid = localStorage.getItem('blog:sessionId')
    if (!sid) {
      // crypto.randomUUID 仅在 HTTPS/localhost 安全上下文存在；HTTP 环境用随机串兜底
      sid = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}-${Math.random().toString(36).slice(2, 10)}`
      localStorage.setItem('blog:sessionId', sid)
    }
    return sid
  }
  return ''
}

/** 读取本地存储的令牌（服务端渲染时返回空令牌） */
export function readTokens (): { access: string, refresh: string } {
  if (import.meta.client) {
    return {
      access: localStorage.getItem('blog:accessToken') ?? '',
      refresh: localStorage.getItem('blog:refreshToken') ?? ''
    }
  }
  return { access: '', refresh: '' }
}

export function saveTokens (access: string, refresh: string) {
  if (import.meta.client) {
    localStorage.setItem('blog:accessToken', access)
    localStorage.setItem('blog:refreshToken', refresh)
  }
}

export function clearTokens () {
  if (import.meta.client) {
    localStorage.removeItem('blog:accessToken')
    localStorage.removeItem('blog:refreshToken')
  }
}

/** 401 刷新只尝试一次，避免无限循环；后台路由守卫复用（composable 目录导出即可自动导入） */
let refreshing: Promise<boolean> | null = null

export async function tryRefresh (): Promise<boolean> {
  const { refresh } = readTokens()
  if (!refresh) {
    return false
  }
  refreshing = refreshing ?? (async () => {
    try {
      const res = await $fetch<{ code: number, data: { accessToken: string, refreshToken: string } }>(
        '/api/v1/auth/refresh',
        { method: 'POST', body: { refreshToken: refresh } }
      )
      if (res.code === 0 && res.data) {
        saveTokens(res.data.accessToken, res.data.refreshToken)
        return true
      }
      clearTokens()
      return false
    } catch {
      clearTokens()
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

/**
 * 请求 REST API 并返回业务数据；业务失败抛出带 code 的错误。
 *
 * @param path   API 路径（/api/v1 前缀可省略）
 * @param options fetch 选项（method/body/query/headers）
 */
export async function api<T> (path: string, options: Record<string, unknown> = {}): Promise<T> {
  const url = path.startsWith('/api/') ? path : `/api/v1${path}`
  const { access } = readTokens()
  const headers: Record<string, string> = { ...(options.headers as Record<string, string> | undefined) }
  if (access) {
    headers.Authorization = `Bearer ${access}`
  }
  try {
    const res = await $fetch<{ code: number, message: string, data: T }>(url, { ...options, headers })
    if (res.code !== 0) {
      const error = new Error(res.message) as ApiError
      error.code = res.code
      throw error
    }
    return res.data
  } catch (error) {
    const fetchErr = error as {
      statusCode?: number, status?: number,
      data?: { code?: number, message?: string },
      message?: string
    }
    const status = fetchErr.statusCode ?? fetchErr.status ?? 0
    const bodyCode = fetchErr.data?.code ?? 0
    const bodyMessage = fetchErr.data?.message

    // 401：尝试刷新令牌后重放一次
    if (status === 401 || bodyCode === 1002) {
      if (await tryRefresh()) {
        return api<T>(path, options)
      }
    }

    // 后端返回了 Result 错误体：直接透出中文业务提示
    if (bodyMessage) {
      const apiErr = new Error(bodyMessage) as ApiError
      apiErr.code = bodyCode ?? (status >= 500 ? 5000 : 1000)
      throw apiErr
    }

    // 网络层 / 网关层错误：统一中文提示，避免英文原始报错直达用户
    const friendly: Record<number, string> = {
      403: '没有权限执行此操作',
      404: '请求的内容不存在',
      429: '操作太频繁，请稍后再试'
    }
    const fallback = status === 0
      ? '网络异常，请检查网络后重试'
      : status >= 500
        ? '服务器开小差了，请稍后再试'
        : friendly[status] ?? '请求失败，请稍后再试'
    const apiErr = new Error(fallback) as ApiError
    apiErr.code = status === 401 ? 1002 : status >= 500 ? 5000 : 1000
    if (status === 401) {
      if (await tryRefresh()) {
        return api<T>(path, options)
      }
    }
    throw apiErr
  }
}
