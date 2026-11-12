<script setup lang="ts">
// 首页：渐变光晕 Hero + 打字机副标题 + 最新文章 + 精选作品 + 公告
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
}>('/home'), { server: true, default: () => ({ latest: [], featuredProjects: [], notices: [], diaries: [] }) })

interface ArticleItem {
  aid: number
  title: string
  digest: string
  categoryName: string
  authorName: string
  createTime: string
  logo: string
  isTop: number
  readingMinutes: number
}
interface ProjectItem {
  id: number
  name: string
  summary: string
  techStack: string
  repoUrl: string
  demoUrl: string
}
interface NoticeItem { id: number, title: string, content: string }
interface DiaryItem { id: number, title: string, mood: string, createTime: string }

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
    <!-- Hero -->
    <section class="glow-hero rounded-2xl border border-app bg-surface px-6 py-14 sm:py-20 text-center">
      <p class="text-sm tracking-widest text-muted uppercase">SMART WORKSPACE</p>
      <h1 class="mt-4 font-serif text-4xl sm:text-5xl font-bold">
        你好，欢迎来到<span class="gradient-text">智序</span>
      </h1>
      <p class="mt-4 text-lg text-muted h-7" aria-live="polite">{{ typed }}<span class="animate-pulse" aria-hidden="true">▍</span></p>
      <div class="mt-8 flex flex-wrap items-center justify-center gap-3">
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
    <section class="mt-10" aria-labelledby="latest-heading">
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
          <NuxtLink
            :to="`/article/${article.aid}`"
            class="block h-full rounded-xl border border-app bg-surface p-5 hover:bg-surface-2 hover:-translate-y-0.5 transition-all focus-visible:ring-2 ring-app"
          >
            <div class="flex items-center gap-2 text-xs text-muted">
              <span class="px-2 py-0.5 rounded-md bg-surface-2">{{ article.categoryName }}</span>
              <time>{{ fmtDate(article.createTime) }}</time>
              <span v-if="article.isTop === 1" style="color: var(--color-warn)">置顶</span>
            </div>
            <h3 class="mt-2 font-serif text-lg font-semibold">{{ article.title }}</h3>
            <p class="mt-2 text-sm text-muted line-clamp-2">{{ article.digest }}</p>
            <p class="mt-3 text-xs text-muted">阅读约 {{ article.readingMinutes ?? 1 }} 分钟</p>
          </NuxtLink>
        </li>
      </ul>
    </section>

    <!-- 精选作品 -->
    <section v-if="data?.featuredProjects?.length" class="mt-10" aria-labelledby="projects-heading">
      <div class="flex items-center justify-between">
        <h2 id="projects-heading" class="font-serif text-2xl font-bold">精选作品</h2>
        <NuxtLink to="/projects" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">全部 →</NuxtLink>
      </div>
      <ul class="mt-4 grid gap-4 sm:grid-cols-3">
        <li v-for="project in data.featuredProjects" :key="project.id">
          <div class="h-full rounded-xl border border-app bg-surface p-5 hover:bg-surface-2 transition-colors">
            <h3 class="font-semibold">{{ project.name }}</h3>
            <p class="mt-1 text-sm text-muted line-clamp-2">{{ project.summary }}</p>
            <p class="mt-3 text-xs text-muted">{{ project.techStack }}</p>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>
