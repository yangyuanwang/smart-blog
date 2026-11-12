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
      sid = crypto.randomUUID()
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

/** 401 刷新只尝试一次，避免无限循环 */
let refreshing: Promise<boolean> | null = null

async function tryRefresh (): Promise<boolean> {
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
    const status = (error as { statusCode?: number }).statusCode ?? 0
    const code = (error as ApiError).code ?? 0
    if (status === 401 || code === 1002) {
      if (await tryRefresh()) {
        return api<T>(path, options)
      }
    }
    throw error
  }
}
