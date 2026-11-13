<script setup lang="ts">
// 文章列表：搜索 + 栏目/标签筛选 + 分页 + 热门榜 + 标签云侧栏
const route = useRoute()
const router = useRouter()

const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const q = computed(() => String(route.query.q ?? ''))
const cid = computed(() => (route.query.cid ? Number(route.query.cid) : undefined))
const tag = computed(() => String(route.query.tag ?? ''))

const search = ref(q.value)
watch(() => route.query.q, (value) => {
  search.value = String(value ?? '')
})

function applyFilter (patch: Record<string, string | number | undefined>) {
  const query: Record<string, string> = {}
  const merged = { page: undefined, q: q.value, cid: cid.value, tag: tag.value, ...patch }
  for (const [key, value] of Object.entries(merged)) {
    if (value !== undefined && value !== '' && value !== null) {
      query[key] = String(value)
    }
  }
  router.push({ path: '/articles', query })
}

function submitSearch () {
  applyFilter({ q: search.value.trim() || undefined, page: undefined })
}

const { data, error, pending } = await useAsyncData(
  `articles-${page.value}-${q.value}-${cid.value ?? ''}-${tag.value}`,
  () => api<PagePayload>('/articles', {
    query: {
      page: page.value,
      ...(q.value ? { q: q.value } : {}),
      ...(cid.value ? { cid: cid.value } : {}),
      ...(tag.value ? { tag: tag.value } : {})
    }
  }),
  { watch: [page, q, cid, tag] }
)

const { data: sidebar } = await useAsyncData('article-sidebar', () => api<SidePayload>('/categories').then(async categories => ({
  categories,
  tags: await api<TagItem[]>('/tags'),
  hot: await api<HotItem[]>('/articles/hot?limit=5')
})))

interface PagePayload {
  list: ArticleCardItem[]
  total: number
  page: number
  size: number
}
interface ArticleCardItem {
  aid: number
  title: string
  digest: string
  categoryName: string
  authorName: string
  createTime: string
  isTop: number
  readingMinutes: number
}
interface SidePayload {
  categories: CategoryItem[]
  tags: TagItem[]
  hot: HotItem[]
}
interface CategoryItem { cid: number, cname: string, articleCount: number }
interface TagItem { tid: number, tname: string, articleCount: number }
interface HotItem { aid: number, title: string, visitCount: number }

const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / (data.value?.size ?? 6))))

function goPage (target: number) {
  applyFilter({ page: Math.min(Math.max(target, 1), totalPages.value) })
}

useSeoMeta({
  title: '文章',
  description: '全部技术文章：按栏目与标签筛选，支持全文搜索'
})
</script>

<template>
  <div class="grid gap-8 lg:grid-cols-[1fr_260px]">
    <section aria-labelledby="articles-heading">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h1 id="articles-heading" class="font-serif text-3xl font-bold">文章</h1>
        <form class="flex gap-2" role="search" @submit.prevent="submitSearch">
          <label for="article-search" class="sr-only">搜索文章</label>
          <input
            id="article-search"
            v-model="search"
            type="search"
            placeholder="搜索标题 / 摘要 / 正文"
            class="w-44 sm:w-64 rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app"
          >
          <button
            type="submit"
            class="px-4 py-2 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
          >
搜索
</button>
        </form>
      </div>

      <!-- 当前筛选条件 -->
      <div v-if="q || cid || tag" class="mt-3 flex flex-wrap items-center gap-2 text-sm text-muted">
        <span>筛选：</span>
        <span v-if="q" class="px-2 py-1 rounded-md bg-surface-2">“{{ q }}”</span>
        <span v-if="cid" class="px-2 py-1 rounded-md bg-surface-2">{{ data?.list?.[0]?.categoryName ?? `栏目 ${cid}` }}</span>
        <span v-if="tag" class="px-2 py-1 rounded-md bg-surface-2"># {{ tag }}</span>
        <button
          type="button"
          class="underline hover:text-main focus-visible:ring-2 ring-app rounded"
          @click="router.push('/articles')"
        >
清除
</button>
      </div>

      <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>
      <div v-else-if="error" class="mt-6 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted" role="alert">
        文章加载失败，请稍后重试
      </div>
      <div v-else-if="!data?.list?.length" class="mt-6 rounded-xl border border-app bg-surface px-4 py-14 text-center text-muted">
        没有匹配的文章，换个关键词试试
      </div>
      <!-- 首篇大卡（有封面时横向通栏）+ 其余网格 -->
      <template v-else>
        <div v-if="page === 1 && !q && !cid && !tag && data.list[0]" class="mt-6">
          <ArticleCard :article="data.list[0]" featured />
        </div>
        <ul class="mt-6 grid gap-4 sm:grid-cols-2">
          <li v-for="article in (page === 1 && !q && !cid && !tag ? data.list.slice(1) : data.list)" :key="article.aid">
            <ArticleCard :article="article" />
          </li>
        </ul>
      </template>

      <!-- 分页 -->
      <nav v-if="totalPages > 1" class="mt-8 flex items-center justify-center gap-2" aria-label="分页">
        <button
          type="button"
          class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm hover:bg-surface-2 disabled:opacity-40 focus-visible:ring-2 ring-app"
          :disabled="page <= 1"
          @click="goPage(page - 1)"
        >
上一页
</button>
        <span class="text-sm text-muted px-2">{{ page }} / {{ totalPages }}</span>
        <button
          type="button"
          class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm hover:bg-surface-2 disabled:opacity-40 focus-visible:ring-2 ring-app"
          :disabled="page >= totalPages"
          @click="goPage(page + 1)"
        >
下一页
</button>
      </nav>
    </section>

    <!-- 侧栏：栏目 / 标签 / 热门 -->
    <aside class="space-y-6 lg:sticky lg:top-20 self-start">
      <div class="rounded-xl border border-app bg-surface p-4">
        <h2 class="font-serif font-semibold">栏目</h2>
        <ul class="mt-3 space-y-1 text-sm">
          <li v-for="category in sidebar?.categories ?? []" :key="category.cid">
            <NuxtLink
              :to="{ path: '/articles', query: { cid: category.cid } }"
              class="flex justify-between px-2 py-1.5 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
              :class="cid === category.cid ? 'bg-surface-2 font-medium' : ''"
            >
              <span>{{ category.cname }}</span>
              <span class="text-muted">{{ category.articleCount }}</span>
            </NuxtLink>
          </li>
        </ul>
      </div>

      <div class="rounded-xl border border-app bg-surface p-4">
        <h2 class="font-serif font-semibold">标签</h2>
        <div class="mt-3 flex flex-wrap gap-2 text-sm">
          <NuxtLink
            v-for="item in (sidebar?.tags ?? []).filter(t => t.articleCount > 0)"
            :key="item.tid"
            :to="{ path: '/articles', query: { tag: item.tname } }"
            class="px-2.5 py-1 rounded-full border border-app hover:bg-surface-2 focus-visible:ring-2 ring-app"
            :class="tag === item.tname ? 'bg-surface-2 font-medium' : ''"
          >{{ item.tname }} <span class="text-muted">{{ item.articleCount }}</span></NuxtLink>
        </div>
      </div>

      <div class="rounded-xl border border-app bg-surface p-4">
        <h2 class="font-serif font-semibold">热门</h2>
        <ol class="mt-3 space-y-2 text-sm">
          <li v-for="(item, index) in sidebar?.hot ?? []" :key="item.aid" class="flex gap-2">
            <span class="text-muted w-4 shrink-0">{{ index + 1 }}.</span>
            <NuxtLink :to="`/article/${item.aid}`" class="hover:text-main line-clamp-1 focus-visible:ring-2 ring-app rounded">
              {{ item.title }}
            </NuxtLink>
          </li>
        </ol>
      </div>
    </aside>
  </div>
</template>
