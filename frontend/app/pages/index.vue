<script setup lang="ts">
// 首页：双栏 Hero（文案 + 实时统计）+ 公告 + 最新文章 + 精选作品
const subtitles = [
  '写代码，也写字。',
  '把复杂讲简单，把简单做扎实。',
  'Java 后端 × 全栈工程实践。'
]

const typed = ref('')
let timer: ReturnType<typeof setInterval> | null = null

function startTypewriter () {
  if (import.meta.client && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    let si = 0
    let ci = 0
    timer = setInterval(() => {
      const current = subtitles[si]
      ci++
      typed.value = current.slice(0, ci)
      if (ci >= current.length) {
        clearInterval(timer!)
        timer = setTimeout(() => {
          si = (si + 1) % subtitles.length
          ci = 0
          typed.value = ''
          startTypewriter()
        }, 2200) as unknown as ReturnType<typeof setInterval>
      }
    }, 90)
  } else {
    typed.value = subtitles[0]
  }
}

onMounted(startTypewriter)
onBeforeUnmount(() => {
  if (timer) {
    clearTimeout(timer)
    clearInterval(timer)
  }
})

const { data, error, pending } = await useAsyncData('home', () => api<{
  latest: ArticleItem[]
  featuredProjects: ProjectItem[]
  notices: NoticeItem[]
  diaries: DiaryItem[]
  stats: Stats
}>('/home'), { server: true, default: () => ({ latest: [], featuredProjects: [], notices: [], diaries: [], stats: {} as Stats }) })

interface ArticleItem {
  aid: number
  title: string
  digest: string
  categoryName: string
  authorName: string
  createTime: string
  logo?: string
  isTop: number
  readingMinutes: number
}
interface ProjectItem {
  id: number
  name: string
  summary: string
  techStack: string
  cover?: string
  repoUrl: string
  demoUrl: string
}
interface NoticeItem { id: number, title: string, content: string }
interface DiaryItem { id: number, title: string, mood: string, createTime: string }
interface Stats { articles?: number, visits?: number, tags?: number, projects?: number }

// 技术栈徽章：与作品集呼应的静态展示
const stack = ['Java 21', 'Spring Boot 3', 'Nuxt 3', 'TypeScript', 'Tailwind', 'MySQL 8', 'Redis 7', 'Docker']

const statCards = computed(() => [
  { label: '已发布文章', value: data.value?.stats?.articles ?? 0, icon: '📚' },
  { label: '累计访问', value: data.value?.stats?.visits ?? 0, icon: '👀' },
  { label: '标签话题', value: data.value?.stats?.tags ?? 0, icon: '🏷️' },
  { label: '开源作品', value: data.value?.stats?.projects ?? 0, icon: '🧩' }
])

// 日历标记：最新文章的发布日期
const markedDates = computed(() => {
  const map: Record<string, number> = {}
  for (const article of data.value?.latest ?? []) {
    const day = article.createTime?.slice(0, 10)
    if (day) {
      map[day] = (map[day] ?? 0) + 1
    }
  }
  return map
})

// 今日热点：走 Nitro 服务端路由（内存缓存 30 分钟，失败返回空数组）
const { data: hotData, pending: hotPending } = await useAsyncData('hotnews', () => $fetch<{
  code: number, data: Array<{ title: string, url: string, hot?: string }>
}>('/hotnews'), { server: true, default: () => ({ code: 0, data: [] }) })
const hotItems = computed(() => hotData.value?.data ?? [])

// 管理入口仅管理员可见（游客/普通访客不显示后台与控制台）；登录态由布局层统一拉取
const { user: session } = useAuthSession()

function fmtDate (value: string) {
  return value?.slice(0, 10) ?? ''
}

useSeoMeta({
  title: '首页',
  description: '智序个人博客：技术文章、作品集与思考记录'
})
</script>

<template>
  <div>
    <!-- 双栏 Hero：左文案 + 右实时统计 -->
    <section class="glow-hero rounded-2xl border border-app bg-surface overflow-hidden">
      <div class="grid lg:grid-cols-[1.2fr_1fr]">
        <div class="px-6 py-12 sm:px-10 sm:py-16 flex flex-col justify-center">
          <p class="text-sm tracking-widest text-muted uppercase">SMART WORKSPACE</p>
          <h1 class="mt-4 font-serif text-4xl sm:text-5xl font-bold leading-tight">
            你好，欢迎来到<span class="gradient-text">智序</span>
          </h1>
          <p class="mt-4 text-lg text-muted h-7" aria-live="polite">{{ typed }}<span class="animate-pulse" aria-hidden="true">▍</span></p>
          <p class="mt-4 text-sm text-muted leading-relaxed max-w-md">
            这里记录我的学习、踩坑与重建：后端架构、数据库设计、容器化部署，以及一个博客的现代化改造全程。
          </p>
          <div class="mt-7 flex flex-wrap items-center gap-3">
            <NuxtLink
              to="/articles"
              class="px-5 py-2.5 rounded-xl text-sm font-medium text-white focus-visible:ring-2 ring-app"
              :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
            >阅读文章</NuxtLink>
            <NuxtLink
              to="/projects"
              class="px-5 py-2.5 rounded-xl text-sm font-medium border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
            >查看作品集</NuxtLink>
          </div>
          <div class="mt-7 flex flex-wrap gap-2">
            <span
              v-for="tech in stack"
              :key="tech"
              class="text-xs px-2.5 py-1 rounded-full border border-app text-muted"
            >{{ tech }}</span>
          </div>
        </div>

        <!-- 右侧统计面板 -->
        <div class="px-6 sm:px-10 py-10 lg:py-16 lg:pl-0 flex items-center">
          <div class="w-full rounded-2xl border border-app bg-surface-2 p-5">
            <p class="text-xs text-muted tracking-wide">站点实时数据</p>
            <div class="mt-4 grid grid-cols-2 gap-3">
              <div
                v-for="card in statCards"
                :key="card.label"
                class="rounded-xl border border-app bg-surface p-4"
              >
                <p class="text-xl" aria-hidden="true">{{ card.icon }}</p>
                <p class="mt-1 font-serif text-2xl font-bold">{{ card.value }}</p>
                <p class="text-xs text-muted mt-0.5">{{ card.label }}</p>
              </div>
            </div>
            <NuxtLink
              v-if="session?.admin"
              to="/admin"
              class="mt-4 block text-center text-xs text-muted hover:text-main focus-visible:ring-2 ring-app rounded py-1"
            >进入控制台 →</NuxtLink>
          </div>
        </div>
      </div>
    </section>

    <!-- 公告 -->
    <section v-if="data?.notices?.length" aria-label="公告" class="mt-8 space-y-2">
      <div
        v-for="notice in data.notices.slice(0, 2)"
        :key="notice.id"
        class="flex items-start gap-3 rounded-xl border border-app bg-surface px-4 py-3 text-sm"
      >
        <span class="shrink-0 mt-0.5 px-2 py-0.5 rounded-md bg-surface-2 text-xs" style="color: var(--color-warn)">公告</span>
        <p class="text-muted">{{ notice.title }}</p>
      </div>
    </section>

    <!-- 最新文章 -->
    <section class="mt-12" aria-labelledby="latest-heading">
      <div class="flex items-center justify-between">
        <h2 id="latest-heading" class="font-serif text-2xl font-bold">最新文章</h2>
        <NuxtLink to="/articles" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">全部 →</NuxtLink>
      </div>

      <p v-if="pending" class="mt-4 text-muted" role="status">加载中…</p>
      <div v-else-if="error" class="mt-4 rounded-xl border border-app bg-surface px-4 py-6 text-center text-muted" role="alert">
        内容加载失败，请稍后刷新重试
      </div>
      <div v-else-if="!data?.latest?.length" class="mt-4 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted">
        还没有公开的文章，去后台发布第一篇吧
      </div>
      <ul v-else class="mt-4 grid gap-4 sm:grid-cols-2">
        <li v-for="article in data.latest" :key="article.aid">
          <ArticleCard :article="article" />
        </li>
      </ul>
    </section>

    <!-- 日历 + 今日热点 -->
    <section class="mt-12 grid gap-4 lg:grid-cols-[1fr_1.4fr]" aria-label="日历与热点">
      <CalendarCard :marked="markedDates" />
      <div class="rounded-xl border border-app bg-surface p-4">
        <div class="flex items-baseline justify-between">
          <p class="font-serif font-semibold">今日热点</p>
          <span class="text-xs text-muted">百度热点 · 30 分钟缓存</span>
        </div>
        <p v-if="hotPending" class="mt-4 text-sm text-muted" role="status">加载中…</p>
        <p v-else-if="!hotItems.length" class="mt-4 text-sm text-muted">热点源暂时不可用</p>
        <ol v-else class="mt-3 space-y-2">
          <li v-for="(item, index) in hotItems" :key="item.url" class="flex items-center gap-3 text-sm">
            <span
              class="shrink-0 w-5 h-5 grid place-items-center rounded-md text-xs"
              :class="index < 3 ? 'text-white' : 'bg-surface-2 text-muted'"
              :style="index < 3 ? { background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' } : {}"
            >{{ index + 1 }}</span>
            <a
              :href="item.url"
              target="_blank"
              rel="noopener noreferrer"
              class="truncate hover:text-main focus-visible:ring-2 ring-app rounded"
            >{{ item.title }}</a>
            <span v-if="item.hot" class="ml-auto shrink-0 text-xs text-muted">{{ item.hot }}</span>
          </li>
        </ol>
      </div>
    </section>

    <!-- 精选作品 -->
    <section v-if="data?.featuredProjects?.length" class="mt-12" aria-labelledby="projects-heading">
      <div class="flex items-center justify-between">
        <h2 id="projects-heading" class="font-serif text-2xl font-bold">精选作品</h2>
        <NuxtLink to="/projects" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">全部 →</NuxtLink>
      </div>
      <ul class="mt-4 grid gap-4 sm:grid-cols-3">
        <li v-for="project in data.featuredProjects" :key="project.id">
          <div class="h-full rounded-xl border border-app bg-surface overflow-hidden hover:bg-surface-2 transition-colors">
            <div class="h-24 bg-surface-2 grid place-items-center overflow-hidden">
              <img v-if="project.cover" :src="project.cover" :alt="`${project.name} 封面`" class="w-full h-full object-cover" loading="lazy">
              <span v-else class="font-serif text-3xl gradient-text" aria-hidden="true">{{ project.name.slice(0, 2) }}</span>
            </div>
            <div class="p-4">
              <h3 class="font-semibold">{{ project.name }}</h3>
              <p class="mt-1 text-sm text-muted line-clamp-2">{{ project.summary }}</p>
              <p class="mt-2 text-xs text-muted">{{ project.techStack }}</p>
            </div>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>
