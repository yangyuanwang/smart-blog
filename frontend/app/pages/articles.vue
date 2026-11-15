<script setup lang="ts">
// 文章列表：标题说明 → 搜索 → 栏目/标签筛选 → 卡片列表 → 分页 → 热门（有数据才显示）
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

const { data: filterData } = await useAsyncData('article-filters', () => api<CategoryItem[]>('/categories').then(async categories => ({
  categories,
  tags: await api<TagItem[]>('/tags')
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
interface CategoryItem { cid: number, cname: string, articleCount: number }
interface TagItem { tid: number, tname: string, articleCount: number }

const categories = computed(() => filterData.value?.categories ?? [])
const tags = computed(() => (filterData.value?.tags ?? []).filter(t => t.articleCount > 0))

// 标签过多时折叠：默认展示前 12 个，可展开
const TAG_COLLAPSE = 12
const tagsExpanded = ref(false)
const visibleTags = computed(() =>
  tagsExpanded.value ? tags.value : tags.value.slice(0, TAG_COLLAPSE)
)
const hasHiddenTags = computed(() => tags.value.length > TAG_COLLAPSE)

// 当前栏目名（用于筛选提示）
const activeCategoryName = computed(() =>
  categories.value.find(c => c.cid === cid.value)?.cname
)

const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / (data.value?.size ?? 6))))

function goPage (target: number) {
  applyFilter({ page: Math.min(Math.max(target, 1), totalPages.value) })
}

// 热门文章：有数据才显示
const { data: hotData } = await useAsyncData('articles-hot', () => api<HotItem[]>('/articles/hot?limit=5'))
const hot = computed(() => hotData.value ?? [])
interface HotItem { aid: number, title: string, visitCount: number }

const hasActiveFilter = computed(() => !!(q.value || cid.value || tag.value))

useSeoMeta({
  title: '文章',
  description: '全部技术文章：按栏目与标签筛选，支持全文搜索'
})
</script>

<template>
  <div class="max-w-4xl mx-auto">
    <!-- 标题与说明 -->
    <h1 class="font-serif text-3xl font-bold">文章</h1>
    <p class="mt-2 text-sm text-muted">按栏目与标签浏览，或直接搜索想读的内容</p>

    <!-- 搜索 -->
    <form class="mt-5 flex gap-2" role="search" @submit.prevent="submitSearch">
      <label for="article-search" class="sr-only">搜索文章</label>
      <input
        id="article-search"
        v-model="search"
        type="search"
        placeholder="搜索标题 / 摘要 / 正文"
        class="flex-1 rounded-lg border border-app bg-surface px-3.5 py-2.5 text-sm focus-visible:ring-2 ring-app"
      >
      <button
        type="submit"
        class="px-4 py-2.5 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
      >搜索</button>
    </form>

    <!-- 栏目筛选 -->
    <div class="mt-4 flex flex-wrap gap-2 text-sm">
      <button
        type="button"
        class="px-3 py-1.5 rounded-full border transition-colors focus-visible:ring-2 ring-app"
        :class="!cid ? 'bg-surface-2 font-medium' : 'border-app hover:bg-surface-2'"
        @click="applyFilter({ cid: undefined, page: undefined })"
      >全部</button>
      <button
        v-for="category in categories"
        :key="category.cid"
        type="button"
        class="px-3 py-1.5 rounded-full border transition-colors focus-visible:ring-2 ring-app"
        :class="cid === category.cid ? 'bg-surface-2 font-medium' : 'border-app hover:bg-surface-2'"
        @click="applyFilter({ cid: category.cid, page: undefined })"
      >{{ category.cname }} <span class="text-muted">{{ category.articleCount }}</span></button>
    </div>

    <!-- 标签筛选：过多时折叠 -->
    <div v-if="tags.length" class="mt-3 flex flex-wrap gap-2 text-sm">
      <button
        v-for="item in visibleTags"
        :key="item.tid"
        type="button"
        class="px-2.5 py-1 rounded-full border transition-colors focus-visible:ring-2 ring-app"
        :class="tag === item.tname ? 'bg-surface-2 font-medium' : 'border-app hover:bg-surface-2'"
        @click="applyFilter({ tag: tag === item.tname ? undefined : item.tname, page: undefined })"
      ># {{ item.tname }} <span class="text-muted">{{ item.articleCount }}</span></button>
      <button
        v-if="hasHiddenTags"
        type="button"
        class="px-2.5 py-1 rounded-full text-muted underline hover:text-main focus-visible:ring-2 ring-app"
        @click="tagsExpanded = !tagsExpanded"
      >{{ tagsExpanded ? '收起标签' : `更多标签（${tags.length - TAG_COLLAPSE}）` }}</button>
    </div>

    <!-- 当前筛选条件 -->
    <div v-if="hasActiveFilter" class="mt-4 flex flex-wrap items-center gap-2 text-sm text-muted">
      <span>筛选：</span>
      <span v-if="q" class="px-2 py-1 rounded-md bg-surface-2">“{{ q }}”</span>
      <span v-if="cid" class="px-2 py-1 rounded-md bg-surface-2">{{ activeCategoryName ?? `栏目 ${cid}` }}</span>
      <span v-if="tag" class="px-2 py-1 rounded-md bg-surface-2"># {{ tag }}</span>
      <button
        type="button"
        class="underline hover:text-main focus-visible:ring-2 ring-app rounded"
        @click="router.push('/articles')"
      >清除</button>
    </div>

    <!-- 文章卡片列表 -->
    <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>
    <div v-else-if="error" class="mt-6 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted" role="alert">
      文章加载失败，请稍后重试
    </div>
    <div v-else-if="!data?.list?.length" class="mt-6 rounded-xl border border-app bg-surface px-4 py-14 text-center text-muted">
      没有匹配的文章，换个关键词试试
    </div>
    <ul v-else class="mt-6 grid gap-4 sm:grid-cols-2">
      <li v-for="article in data.list" :key="article.aid">
        <ArticleCard :article="article" />
      </li>
    </ul>

    <!-- 分页 -->
    <nav v-if="totalPages > 1" class="mt-8 flex items-center justify-center gap-2" aria-label="分页">
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm hover:bg-surface-2 disabled:opacity-40 focus-visible:ring-2 ring-app"
        :disabled="page <= 1"
        @click="goPage(page - 1)"
      >上一页</button>
      <span class="text-sm text-muted px-2">{{ page }} / {{ totalPages }}</span>
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm hover:bg-surface-2 disabled:opacity-40 focus-visible:ring-2 ring-app"
        :disabled="page >= totalPages"
        @click="goPage(page + 1)"
      >下一页</button>
    </nav>

    <!-- 热门文章：有数据才显示 -->
    <section v-if="hot.length" class="mt-12" aria-labelledby="hot-heading">
      <h2 id="hot-heading" class="font-serif text-xl font-bold">热门文章</h2>
      <ol class="mt-4 grid gap-2 sm:grid-cols-2 text-sm">
        <li v-for="(item, index) in hot" :key="item.aid" class="flex gap-2 items-baseline">
          <span class="text-muted w-5 shrink-0 tabular-nums">{{ index + 1 }}.</span>
          <NuxtLink :to="`/article/${item.aid}`" class="hover:text-main line-clamp-1 focus-visible:ring-2 ring-app rounded">
            {{ item.title }}
          </NuxtLink>
        </li>
      </ol>
    </section>
  </div>
</template>
