// gen-covers.mjs — 程序化生成 10 张品牌风 SVG 封面（1200×630）
// 每张：主题渐变底 + 几何主图形 + 衬线大标题 + 站名署名
import fs from 'node:fs'

const OUT = new URL('../../backend/uploads', import.meta.url).pathname.replace(/^\([A-Z]:)/, '$1').replace(/^//, '') || 'backend/uploads'

const covers = [
  {
    file: 'cover-mysql-deep-pagination.svg',
    title: 'MySQL 深分页优化实战',
    sub: '从 12 秒到 80 毫秒',
    date: '2021-05-20',
    bg: ['#0f172a', '#1e3a5f'],
    accent: '#38bdf8',
    motif: 'db'
  },
  {
    file: 'cover-redis-lock.svg',
    title: 'Redis 分布式锁进化史',
    sub: '从 SETNX 到看门狗',
    date: '2021-11-03',
    bg: ['#111827', '#312e81'],
    accent: '#f472b6',
    motif: 'lock'
  },
  {
    file: 'cover-dali-erhai.svg',
    title: '大理丽江七日',
    sub: '环洱海骑行的风与光',
    date: '2022-02-14',
    bg: ['#0c4a6e', '#0e7490'],
    accent: '#67e8f9',
    motif: 'lake'
  },
  {
    file: 'cover-spring-tx.svg',
    title: 'Spring 事务失效的八个场景',
    sub: '@Transactional 的暗礁清单',
    date: '2022-09-28',
    bg: ['#1a2e05', '#365314'],
    accent: '#a3e635',
    motif: 'shield'
  },
  {
    file: 'cover-kyoto-autumn.svg',
    title: '京都红叶季五日',
    sub: '从鸭川到岚山的慢路线',
    date: '2023-04-06',
    bg: ['#450a0a', '#7c2d12'],
    accent: '#fb923c',
    motif: 'maple'
  },
  {
    file: 'cover-turning-thirty.svg',
    title: '与不够优秀的自己和解',
    sub: '三十岁前后的自我对话',
    date: '2023-08-19',
    bg: ['#1e1b4b', '#4c1d95'],
    accent: '#c4b5fd',
    motif: 'path'
  },
  {
    file: 'cover-jwt-leak.svg',
    title: 'JWT 令牌泄露之后',
    sub: '无状态认证的止损设计',
    date: '2024-03-15',
    bg: ['#052e16', '#064e3b'],
    accent: '#34d399',
    motif: 'jwt'
  },
  {
    file: 'cover-qinghai-cycling.svg',
    title: '青海湖环骑 360 公里',
    sub: '四天，海拔 3200 米的风',
    date: '2024-08-02',
    bg: ['#082f49', '#164e63'],
    accent: '#7dd3fc',
    motif: 'bike'
  },
  {
    file: 'cover-writing-clarity.svg',
    title: '写作教会我的事',
    sub: '把模糊的想法逼成清晰的文字',
    date: '2025-04-22',
    bg: ['#292524', '#44403c'],
    accent: '#fcd34d',
    motif: 'pen'
  },
  {
    file: 'cover-compose-fullstack.svg',
    title: 'Docker Compose 一键全栈',
    sub: '从本地到公网的服务器实践',
    date: '2025-10-18',
    bg: ['#0c0a09', '#1c1917'],
    accent: '#22d3ee',
    motif: 'docker'
  },
  {
    file: 'cover-v2-renovation.svg',
    title: 'smart-blog v2 改造记',
    sub: '从课程设计到全栈工程',
    date: '2026-11-12',
    bg: ['#1e1b4b', '#312e81'],
    accent: '#818cf8',
    motif: 'docker'
  },
  {
    file: 'cover-ai-summary.svg',
    title: 'AI 摘要：异步与降级',
    sub: '给博客接入 OpenAI 兼容接口',
    date: '2026-11-13',
    bg: ['#0f172a', '#164e63'],
    accent: '#2dd4bf',
    motif: 'jwt'
  },
  {
    file: 'cover-view-buffer.svg',
    title: '浏览量缓冲落库',
    sub: 'Redis 计数与定时任务',
    date: '2026-11-13',
    bg: ['#052e16', '#14532d'],
    accent: '#4ade80',
    motif: 'db'
  }
]

function motifSvg (motif, accent) {
  const a = `fill="none" stroke="${accent}" stroke-width="3" stroke-linecap="round"`
  switch (motif) {
    case 'db':
      return `<g ${a} opacity="0.9"><ellipse cx="600" cy="330" rx="150" ry="34"/><path d="M450 330 v90 a150 34 0 0 0 300 0 v-90"/><path d="M450 375 a150 34 0 0 0 300 0" opacity="0.5"/><path d="M560 420 l60 60 m0 -60 l-60 60" stroke-width="5"/><path d="M680 480 h130 m0 0 l-24 -18 m24 18 l-24 18" stroke-width="5"/></g>`
    case 'lock':
      return `<g ${a} opacity="0.9"><rect x="540" y="340" width="120" height="90" rx="10"/><path d="M560 340 v-30 a40 40 0 0 1 80 0 v30"/><circle cx="600" cy="378" r="9" fill="${accent}" stroke="none"/><path d="M600 387 v20"/><circle cx="800" cy="380" r="60" opacity="0.5"/><circle cx="800" cy="380" r="86" opacity="0.25"/></g>`
    case 'lake':
      return `<g ${a} opacity="0.9"><path d="M300 430 l180 -150 180 150" stroke-width="4"/><path d="M540 430 l120 -110 120 110" opacity="0.6"/><path d="M300 470 q60 -14 120 0 t120 0 t120 0 t120 0" stroke-width="3"/><circle cx="870" cy="250" r="34" opacity="0.8"/><path d="M360 330 l40 -34 40 34" opacity="0.5"/></g>`
    case 'shield':
      return `<g ${a} opacity="0.9"><path d="M600 260 l110 34 v96 c0 60 -50 100 -110 122 c-60 -22 -110 -62 -110 -122 v-96 z"/><path d="M560 380 l30 30 58 -66" stroke-width="5"/></g>`
    case 'maple':
      return `<g ${a} opacity="0.9"><path d="M600 260 c-14 34 -10 52 -38 78 c26 -6 40 0 56 24 c16 -24 30 -30 56 -24 c-28 -26 -24 -44 -38 -78 c-10 24 -26 24 -36 0 z" fill="${accent}" fill-opacity="0.35"/><path d="M600 300 v120" stroke-width="3"/><path d="M420 430 q80 -20 180 -14 t180 14" stroke-width="3" opacity="0.6"/><circle cx="380" cy="270" r="20" fill="${accent}" fill-opacity="0.3" stroke="none"/><circle cx="820" cy="250" r="14" fill="${accent}" fill-opacity="0.3" stroke="none"/></g>`
    case 'path':
      return `<g ${a} opacity="0.9"><path d="M340 460 q80 -130 180 -110 t180 -90" stroke-width="4"/><circle cx="340" cy="460" r="12" fill="${accent}" stroke="none"/><circle cx="520" cy="350" r="9" opacity="0.7"/><circle cx="700" cy="260" r="12" fill="${accent}" stroke="none"/><path d="M700 260 l120 -40" stroke-dasharray="6 8" opacity="0.6"/></g>`
    case 'jwt':
      return `<g ${a} opacity="0.9"><circle cx="600" cy="360" r="80"/><circle cx="600" cy="360" r="46" opacity="0.5"/><circle cx="600" cy="360" r="10" fill="${accent}" stroke="none"/><path d="M600 280 v-40 m0 240 v-40 m-80 -80 h-40 m240 80 h-40" stroke-dasharray="4 6" opacity="0.7"/><path d="M660 300 l90 -70" stroke-width="5"/><path d="M718 230 h44 v44" stroke-width="5"/></g>`
    case 'bike':
      return `<g ${a} opacity="0.9"><circle cx="480" cy="400" r="52"/><circle cx="720" cy="400" r="52"/><path d="M480 400 l90 -110 h90 l60 110 m-150 -110 h-90"/><path d="M570 290 l40 110" stroke-width="4"/><path d="M380 470 q220 30 440 0" opacity="0.5"/><path d="M300 300 q40 -60 100 -70" stroke-dasharray="5 7" opacity="0.5"/></g>`
    case 'pen':
      return `<g ${a} opacity="0.9"><path d="M520 440 l180 -180 m0 0 l40 -40 22 22 -40 40 z" stroke-width="4"/><path d="M520 440 l-30 8 8 -30" fill="${accent}" fill-opacity="0.4"/><path d="M430 260 h180 m-180 40 h120" opacity="0.45"/><path d="M430 500 h340" opacity="0.45"/></g>`
    case 'docker':
      return `<g ${a} opacity="0.9"><rect x="440" y="330" width="90" height="70" rx="6"/><rect x="540" y="330" width="90" height="70" rx="6"/><rect x="490" y="250" width="90" height="70" rx="6"/><path d="M420 410 q180 60 360 0" stroke-width="4"/><path d="M790 300 q26 22 0 44" opacity="0.7"/><path d="M820 280 q40 34 0 66" opacity="0.45"/></g>`
    default:
      return ''
  }
}

function coverSvg (c) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630">
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="${c.bg[0]}"/>
    <stop offset="1" stop-color="${c.bg[1]}"/>
  </linearGradient>
  <linearGradient id="ac" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="${c.accent}"/>
    <stop offset="1" stop-color="#a78bfa"/>
  </linearGradient>
</defs>
<rect width="1200" height="630" fill="url(#bg)"/>
<circle cx="1050" cy="110" r="180" fill="${c.accent}" fill-opacity="0.08"/>
<circle cx="120" cy="560" r="140" fill="${c.accent}" fill-opacity="0.06"/>
${motifSvg(c.motif, c.accent)}
<rect x="100" y="70" width="56" height="4" fill="url(#ac)" rx="2"/>
<text x="100" y="140" font-family="Georgia, 'Songti SC', serif" font-size="52" font-weight="bold" fill="#ffffff">${c.title}</text>
<text x="100" y="190" font-family="'Segoe UI', 'PingFang SC', sans-serif" font-size="24" fill="${c.accent}">${c.sub}</text>
<text x="100" y="560" font-family="'Segoe UI', sans-serif" font-size="18" fill="#ffffff" fill-opacity="0.75">智序 · SMART WORKSPACE</text>
<text x="100" y="590" font-family="'Segoe UI', sans-serif" font-size="15" fill="#ffffff" fill-opacity="0.45">${c.date} · smart-blog</text>
</svg>`
}

if (!fs.existsSync(OUT)) {
  fs.mkdirSync(OUT, { recursive: true })
}
for (const c of covers) {
  fs.writeFileSync(`${OUT}/${c.file}`, coverSvg(c))
  console.log('cover:', c.file)
}
console.log('covers done:', covers.length)
