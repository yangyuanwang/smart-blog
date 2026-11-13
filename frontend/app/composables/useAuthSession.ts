// 前台登录态：跨页面共享的用户状态，导航栏据此显示身份与退出入口
export interface MeProfile {
  uid: number
  username: string
  nickname: string
  img?: string
  admin?: boolean
}

export function useAuthSession () {
  // useState 跨组件共享；SSR 阶段为 null，登录态仅在客户端判定（令牌存 localStorage）
  const user = useState<MeProfile | null>('auth-user', () => null)
  const ready = useState('auth-user-ready', () => false)

  async function refresh () {
    if (import.meta.server) {
      return
    }
    try {
      user.value = await api<MeProfile>('/me/profile')
    } catch {
      user.value = null
    } finally {
      ready.value = true
    }
  }

  async function logout () {
    try {
      const { refresh: refreshToken } = readTokens()
      if (refreshToken) {
        await api('/auth/logout', { method: 'POST', body: { refreshToken } }).catch(() => null)
      }
    } finally {
      clearTokens()
      user.value = null
      await navigateTo('/')
    }
  }

  return { user, ready, refresh, logout }
}
