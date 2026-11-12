// RSS 订阅源：拉取最新公开文章生成 RSS 2.0 XML
// h3 工具（defineEventHandler/getRequestURL/setHeader/useRuntimeConfig）由 Nitro 自动导入

interface ArticleItem {
  aid: number
  title: string
  digest?: string
  createTime?: string
}

function escapeXml (value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export default defineEventHandler(async (event) => {
  const origin = getRequestURL(event).origin
  const apiBase = useRuntimeConfig().apiOrigin || 'http://localhost:8080'
  const response = await $fetch<{ code: number, data: { list: ArticleItem[] } }>(
    `${apiBase}/api/v1/articles`,
    { query: { page: 1, size: 20 } }
  ).catch(() => null)

  const list = response?.data?.list ?? []
  const items = list.map((article) => `
    <item>
      <title>${escapeXml(article.title)}</title>
      <link>${origin}/article/${article.aid}</link>
      <guid isPermaLink="true">${origin}/article/${article.aid}</guid>
      <description>${escapeXml(article.digest ?? '')}</description>
      <pubDate>${article.createTime ? new Date(article.createTime.replace(' ', 'T')).toUTCString() : new Date().toUTCString()}</pubDate>
    </item>`).join('\n')

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>智序 SMART WORKSPACE</title>
    <link>${origin}</link>
    <description>个人技术博客：文章、作品与思考</description>
    <language>zh-CN</language>
    ${items}
  </channel>
</rss>`

  setHeader(event, 'Content-Type', 'application/rss+xml; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=600')
  return xml
})
