// accept.mjs —— 全链路验收：登录/发文/前台可见/互动/审核/设置（对容器化全栈）
const base = process.argv[2] || 'http://localhost:8080'

async function api (path, options = {}) {
  const res = await fetch(base + path, options)
  return res.json()
}

const results = []
function check (name, ok, detail = '') {
  results.push(`${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' - ' + detail : ''}`)
}

// 1. 游客访问公开内容
const home = await api('/api/v1/home')
check('home aggregation', home.code === 0 && home.data.latest.length > 0, `latest=${home.data.latest.length}`)

const list = await api('/api/v1/articles?size=5')
check('article list', list.code === 0 && list.data.list.length > 0, `total=${list.data.total}`)

const detail = await api(`/api/v1/articles/${list.data.list[0].aid}`)
check('article detail ssr payload', detail.code === 0 && !!detail.data.html)

const search = await api('/api/v1/search?q=AI')
check('search', search.code === 0 && search.data.length > 0, `hits=${search.data.length}`)

// 2. 注册访客
const suffix = Date.now() % 100000
const reg = await api('/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'accept' + suffix, password: 'accept123', nickname: 'accept-user' })
})
check('visitor register', reg.code === 0)
const userToken = reg.data.accessToken

// 3. 匿名留言 -> 待审
const msg = await api('/api/v1/messages', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ nickname: 'accept-guest', content: 'acceptance test message' })
})
check('guest message submit', msg.code === 0 && msg.data.audited === true)

// 4. 点赞去重
const aid = list.data.list[0].aid
const like1 = await api(`/api/v1/articles/${aid}/like`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'accept-1' })
})
const like2 = await api(`/api/v1/articles/${aid}/like`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: 'accept-1' })
})
check('like dedupe', like1.data.first === true && like2.data.first === false)

// 5. 管理员审核留言
const adminLogin = await api('/api/v1/auth/admin-login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: '\u5c0f\u5f3a', password: '123' })
})
check('admin login', adminLogin.code === 0, `role=${adminLogin.data.user.role}`)
const adminToken = adminLogin.data.accessToken
const adminHeaders = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + adminToken }

const messages = await api('/api/v1/admin/messages?size=10', { headers: adminHeaders })
const pending = messages.data.list.find(m => m.status === '0')
if (pending) {
  const audit = await api(`/api/v1/admin/messages/${pending.id}/audit`, {
    method: 'PUT', headers: adminHeaders, body: JSON.stringify({ status: '1' })
  })
  check('message audit approve', audit.code === 0)
}

// 6. 权限边界：访客访问管理端
const forbidden = await fetch(base + '/api/v1/admin/dashboard', {
  headers: { Authorization: 'Bearer ' + userToken }
})
check('visitor blocked from admin', forbidden.status === 403)

// 7. 管理员发文 -> 前台可见
const publish = await api('/api/v1/admin/articles', {
  method: 'POST', headers: adminHeaders,
  body: JSON.stringify({ title: 'acceptance publish test', content: '# Accept\n\nbody', cid: home.data.latest[0].cid || 1, tagNames: 'acceptance', publishStatus: '1' })
})
check('admin publish article', publish.code === 0, 'aid=' + publish.data?.aid)

// 8. 站点设置修改生效
const settings = await api('/api/v1/admin/settings', { headers: adminHeaders })
check('settings readable', settings.code === 0 && Object.keys(settings.data).length > 0)

console.log(results.join('\n'))
const failed = results.filter(r => r.startsWith('FAIL')).length
console.log(failed === 0 ? '\nALL ACCEPTANCE CHECKS PASSED' : `\n${failed} CHECKS FAILED`)
process.exit(failed === 0 ? 0 : 1)
