// 热点新闻聚合：服务端拉取百度热点榜单并内存缓存 30 分钟。
// 多源回退：vvhan → 60s API（viki.moe）→ 上一份缓存 → 空数组。
// 外部源不可达时前端对应板块自动隐藏，绝不阻塞首页渲染。

interface HotItem {
  title: string
  url: string
  hot?: string | number
}

interface CacheEntry {
  items: HotItem[]
  fetchedAt: number
}

let cache: CacheEntry | null = null
let fetching: Promise<HotItem[]> | null = null

const TTL = 30 * 60 * 1000

function normalize (list: Array<Record<string, unknown>>, limit = 10): HotItem[] {
  return list.slice(0, limit).map((item) => ({
    title: String(item.title ?? ''),
    url: String(item.url ?? item.mobil_url ?? ''),
    hot: item.hot === undefined ? undefined : String(item.hot)
  })).filter(item => item.title && item.url)
}

async function fromVvhan (): Promise<HotItem[]> {
  const response = await $fetch<{ success: boolean, data: Array<Record<string, unknown>> }>(
    'https://api.vvhan.com/api/hotlist/baiduRD',
    { timeout: 8000 }
  )
  return response?.success && Array.isArray(response.data) ? normalize(response.data) : []
}

async function fromViki (): Promise<HotItem[]> {
  const response = await $fetch<{ code: number, data: Array<Record<string, unknown>> }>(
    'https://60s.viki.moe/v2/baidu/hot',
    { timeout: 8000 }
  )
  return response?.code === 200 && Array.isArray(response.data) ? normalize(response.data) : []
}

async function fetchHot (): Promise<HotItem[]> {
  const sources = [fromVvhan, fromViki]
  for (const source of sources) {
    try {
      const items = await source()
      if (items.length > 0) {
        return items
      }
    } catch {
      // 单个源失败继续尝试下一个
    }
  }
  return []
}

export default defineEventHandler(async () => {
  const now = Date.now()
  if (cache && now - cache.fetchedAt < TTL) {
    return { code: 0, data: cache.items, cached: true }
  }
  if (!fetching) {
    fetching = fetchHot()
      .then((items) => {
        // 全源失败时不覆盖旧缓存（宁可展示过期热点也不留空）
        if (items.length > 0 || !cache) {
          cache = { items, fetchedAt: Date.now() }
        }
        return items.length > 0 ? items : (cache?.items ?? [])
      })
      .catch(() => cache?.items ?? [])
      .finally(() => {
        fetching = null
      })
  }
  const items = await fetching
  return { code: 0, data: items, cached: Boolean(cache && now - cache.fetchedAt < TTL) }
})
