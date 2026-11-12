// 后台路由守卫：无令牌时尝试 refresh，仍失败则跳转后台登录页
export default defineNuxtRouteMiddleware(async (to) => {
  if (import.meta.server) {
    return
  }
  const { access, refresh } = readTokens()
  if (access) {
    return
  }
  if (refresh) {
    const ok = await tryRefresh()
    if (ok) {
      return
    }
  }
  return navigateTo(`/admin/login?next=${encodeURIComponent(to.fullPath)}`)
})
