// sitemap.xml：公开文章 + 固定页面
// h3 工具由 Nitro 自动导入

interface ArticleItem {
  aid: number
  updateTime?: string
  createTime?: string
}

export default defineEventHandler(async (event) => {
  const origin = getRequestURL(event).origin
  const apiBase = useRuntimeConfig().apiOrigin || 'http://localhost:8080'
  const response = await $fetch<{ code: number, data: { list: ArticleItem[] } }>(
    `${apiBase}/api/v1/articles`,
    { query: { page: 1, size: 200 } }
  ).catch(() => null)

  const staticPages = ['', '/articles', '/projects', '/messages', '/diaries', '/links', '/about']
  const articlePages = (response?.data?.list ?? []).map((article) => `
  <url>
    <loc>${origin}/article/${article.aid}</loc>
    <lastmod>${(article.updateTime || article.createTime || '').replace(' ', 'T')}</lastmod>
  </url>`)

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  ${staticPages.map(page => `<url><loc>${origin}${page}</loc></url>`).join('\n  ')}
  ${articlePages.join('\n  ')}
</urlset>`

  setHeader(event, 'Content-Type', 'application/xml; charset=utf-8')
  setHeader(event, 'Cache-Control', 'public, max-age=3600')
  return xml
})
