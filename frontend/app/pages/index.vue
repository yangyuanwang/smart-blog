<script setup lang="ts">
// 首页：介绍首屏 → 公告（去重）→ 精选文章大卡 → 最近文章 → 最近记录（日记）→ 关于摘要
// 不放实时统计面板与外源热点；统计仅作低调信息并链接到对应列表
const subtitles = [
  '写代码，也记录把想法做成产品的过程。',
  '把复杂讲简单，把简单做扎实。',
  '后端架构 × 全栈工程实践。'
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

interface HomePayload {
  latest: ArticleItem[]
  featuredProjects: ProjectItem[]
  notices: NoticeItem[]
  diaries: DiaryItem[]
  site: SiteInfo
  stats: Stats
}
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
interface ProjectItem { id: number, name: string, summary: string }
interface NoticeItem { id: number, title: string, content: string, isTop?: string }
interface DiaryItem { id: number, title: string, content: string, mood?: string, createTime: string }
interface SiteInfo { site_name?: string, site_footer?: string, about?: string, icp?: string }
interface Stats { articles?: number, visits?: number, tags?: number, projects?: number }

const { data, error, pending } = await useAsyncData('home', () => api<HomePayload>('/home'), {
  server: true,
  default: () => ({ latest: [], featuredProjects: [], notices: [], diaries: [], site: {} as SiteInfo, stats: {} as Stats })
})

// 精选：管理员置顶（isTop=1）优先，否则取最新一篇公开文章
const articles = computed(() => data.value?.latest ?? [])
const featured = computed(() => articles.value.find(a => a.isTop === 1) ?? articles.value[0] ?? null)
const recent = computed(() => articles.value.filter(a => a.aid !== featured.value?.aid).slice(0, 5))

// 公告：按 id 与内容双重去重，最多 1 条置顶 + 2 条普通；无公告时整块隐藏
const pinnedNotice = computed(() => {
  const seenTitle = new Set<string>()
  const list = data.value?.notices ?? []
  for (const notice of list) {
    if (seenTitle.has(`${notice.id}`) || seenTitle.has(notice.title)) {
      continue
    }
    seenTitle.add(`${notice.id}`)
    seenTitle.add(notice.title)
    if (notice.isTop === '1') {
      return notice
    }
  }
  return null
})
const normalNotices = computed(() => {
  const list = data.value?.notices ?? []
  const seen = new Set<string>()
  const result: NoticeItem[] = []
  for (const notice of list) {
    if (notice.isTop === '1' && notice.id === pinnedNotice.value?.id) {
      continue
    }
    const key = `${notice.id}|${notice.title}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    result.push(notice)
    if (result.length >= 2) {
      break
    }
  }
  return result
})
const hasNotices = computed(() => !!pinnedNotice.value || normalNotices.value.length > 0)

// 低调信息：文章数固定展示；开源作品数仅在真实存在时展示并链接到作品集
const articleCount = computed(() => data.value?.stats?.articles ?? 0)
const projectCount = computed(() => data.value?.stats?.projects ?? 0)

// 技术方向标签：一到两行
const stack = ['Java 21', 'Spring Boot 3', 'Nuxt 3', 'TypeScript', 'MySQL 8', 'Redis 7', 'Docker']

// 精选大卡封面：加载失败回退占位
const featuredLogoFailed = ref(false)
watch(() => featured.value?.logo, () => {
  featuredLogoFailed.value = false
})

const moodColor: Record<string, string> = {
  随记: 'var(--color-accent)',
  开工: 'var(--color-ok)',
  想走: 'var(--color-accent-2)',
  读完: 'var(--color-warn)',
  高兴: 'var(--color-danger)',
  发呆: 'var(--text-muted)'
}

function fmtDate (value?: string) {
  return value?.slice(0, 10) ?? ''
}

// 关于摘要：优先站点设置里的 about，缺失时用一句通用介绍
const aboutSummary = computed(() => {
  const about = data.value?.site?.about?.trim()
  return about || '后端工程师，关注架构、数据库与工程效率。这里是我的文章与记录。'
})

useSeoMeta({
  title: '首页',
  description: '智序个人博客：技术文章、作品集与思考记录'
})
</script>

<template>
  <div>
    <!-- 介绍首屏：左文案 + 右文字名片（不放虚构数据面板） -->
    <section class="glow-hero rounded-2xl border border-app bg-surface overflow-hidden">
      <div class="grid lg:grid-cols-[1.3fr_1fr]">
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
            >阅读最新文章</NuxtLink>
            <NuxtLink
              to="/about"
              class="px-5 py-2.5 rounded-xl text-sm font-medium border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
            >了解我</NuxtLink>
          </div>
          <div class="mt-7 flex flex-wrap gap-2">
            <span
              v-for="tech in stack"
              :key="tech"
              class="text-xs px-2.5 py-1 rounded-full border border-app text-muted"
            >{{ tech }}</span>
          </div>
        </div>

        <!-- 右侧：简洁文字名片 + 低调的带链接统计 -->
        <div class="px-6 sm:px-10 py-10 lg:py-16 lg:pl-0 flex items-center">
          <div class="w-full rounded-2xl border border-app bg-surface-2 p-6">
            <div class="flex items-center gap-4">
              <span
                class="shrink-0 w-14 h-14 rounded-full grid place-items-center font-serif text-2xl font-bold text-white"
                :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
                aria-hidden="true"
              >智</span>
              <div>
                <p class="font-serif text-lg font-bold">智序</p>
                <p class="text-xs text-muted mt-0.5">后端工程师 · 全栈实践者</p>
              </div>
            </div>
            <div class="mt-5 flex items-center gap-4 text-sm text-muted">
              <NuxtLink to="/articles" class="hover:text-main focus-visible:ring-2 ring-app rounded" title="查看全部文章">
                <span class="tabular-nums">{{ articleCount }}</span> 篇文章
              </NuxtLink>
              <NuxtLink v-if="projectCount > 0" to="/projects" class="hover:text-main focus-visible:ring-2 ring-app rounded" title="查看作品集">
                <span class="tabular-nums">{{ projectCount }}</span> 个开源作品
              </NuxtLink>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 公告：去重后至多 1 条置顶 + 2 条普通，无公告整块隐藏 -->
    <section v-if="hasNotices" aria-label="公告" class="mt-8 space-y-2">
      <div
        v-for="notice in (pinnedNotice ? [pinnedNotice, ...normalNotices] : normalNotices)"
        :key="notice.id"
        class="flex items-start gap-3 rounded-xl border border-app bg-surface px-4 py-3 text-sm"
      >
        <span
          class="shrink-0 mt-0.5 px-2 py-0.5 rounded-md bg-surface-2 text-xs"
          :style="{ color: notice.isTop === '1' ? 'var(--color-warn)' : 'var(--text-muted)' }"
        >{{ notice.isTop === '1' ? '置顶公告' : '公告' }}</span>
        <p class="text-muted">{{ notice.title }}</p>
      </div>
    </section>

    <!-- 精选文章大卡：置顶优先，否则最新一篇；封面缺失或加载失败时用占位 -->
    <section v-if="featured" class="mt-12" aria-labelledby="featured-heading">
      <div class="flex items-center justify-between">
        <h2 id="featured-heading" class="font-serif text-2xl font-bold">精选文章</h2>
        <NuxtLink to="/articles" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">全部文章 →</NuxtLink>
      </div>
      <NuxtLink
        :to="`/article/${featured.aid}`"
        class="group mt-4 grid sm:grid-cols-[2fr_3fr] rounded-2xl border border-app bg-surface overflow-hidden hover:bg-surface-2 transition-colors focus-visible:ring-2 ring-app"
      >
        <div class="relative w-full aspect-[2/1] sm:aspect-auto sm:min-h-64 bg-surface-2 overflow-hidden">
          <img
            v-if="featured.logo && !featuredLogoFailed"
            :src="featured.logo"
            :alt="`${featured.title} 封面`"
            class="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
            @error="featuredLogoFailed = true"
          >
          <div v-else class="absolute inset-0 grid place-items-center bg-gradient-to-br from-[color:var(--surface-2)] to-[color:var(--surface)]">
            <span class="font-serif text-5xl gradient-text" aria-hidden="true">{{ featured.title.slice(0, 2) }}</span>
          </div>
        </div>
        <div class="p-6 sm:p-8 flex flex-col justify-center">
          <div class="flex items-center gap-2 text-xs">
            <span
              class="px-2 py-0.5 rounded-md text-white"
              :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
            >{{ featured.isTop === 1 ? '精选' : '最新' }}</span>
            <span v-if="featured.categoryName" class="px-2 py-0.5 rounded-md bg-surface-2 text-muted">{{ featured.categoryName }}</span>
            <time class="text-muted">{{ fmtDate(featured.createTime) }}</time>
          </div>
          <h3 class="mt-3 font-serif text-2xl sm:text-3xl font-bold leading-snug">{{ featured.title }}</h3>
          <p class="mt-3 text-sm text-muted leading-relaxed line-clamp-3">{{ featured.digest }}</p>
          <p class="mt-4 text-xs text-muted">{{ featured.authorName }} · 阅读约 {{ featured.readingMinutes ?? 1 }} 分钟</p>
        </div>
      </NuxtLink>
    </section>

    <!-- 最近文章：真实公开文章，桌面两列 / 手机一列 -->
    <section class="mt-12" aria-labelledby="latest-heading">
      <div class="flex items-center justify-between">
        <h2 id="latest-heading" class="font-serif text-2xl font-bold">最近文章</h2>
        <NuxtLink to="/articles" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">查看全部文章 →</NuxtLink>
      </div>

      <p v-if="pending" class="mt-4 text-muted" role="status">加载中…</p>
      <div v-else-if="error" class="mt-4 rounded-xl border border-app bg-surface px-4 py-6 text-center text-muted" role="alert">
        内容加载失败，请稍后刷新重试
      </div>
      <div v-else-if="!recent.length" class="mt-4 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted">
        还没有公开的文章，去后台发布第一篇吧
      </div>
      <ul v-else class="mt-4 grid gap-4 sm:grid-cols-2">
        <li v-for="article in recent" :key="article.aid">
          <ArticleCard :article="article" />
        </li>
      </ul>
    </section>

    <!-- 最近记录：公开日记，突出日期与摘要 -->
    <section v-if="data?.diaries?.length" class="mt-12" aria-labelledby="diaries-heading">
      <div class="flex items-center justify-between">
        <h2 id="diaries-heading" class="font-serif text-2xl font-bold">最近记录</h2>
        <NuxtLink to="/diaries" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">日记页 →</NuxtLink>
      </div>
      <ul class="mt-4 grid gap-4 sm:grid-cols-3">
        <li v-for="diary in data.diaries.slice(0, 3)" :key="diary.id">
          <NuxtLink
            to="/diaries"
            class="block h-full rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 transition-colors focus-visible:ring-2 ring-app"
          >
            <div class="flex items-center gap-2 text-xs text-muted">
              <time>{{ fmtDate(diary.createTime) }}</time>
              <span
                v-if="diary.mood"
                class="px-2 py-0.5 rounded-full border border-app"
                :style="{ color: moodColor[diary.mood] ?? 'var(--text-muted)' }"
              >{{ diary.mood }}</span>
            </div>
            <h3 class="mt-2 font-serif font-semibold">{{ diary.title }}</h3>
            <p class="mt-1.5 text-sm text-muted line-clamp-2">{{ diary.content }}</p>
          </NuxtLink>
        </li>
      </ul>
    </section>

    <!-- 关于摘要：链接到完整关于页 -->
    <section class="mt-12" aria-labelledby="about-heading">
      <div class="rounded-2xl border border-app bg-surface px-6 py-8 sm:px-10">
        <h2 id="about-heading" class="font-serif text-2xl font-bold">关于我</h2>
        <p class="mt-3 text-sm text-muted leading-relaxed max-w-2xl whitespace-pre-line">{{ aboutSummary }}</p>
        <NuxtLink
          to="/about"
          class="mt-4 inline-block text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded"
        >了解更多 →</NuxtLink>
      </div>
    </section>
  </div>
</template>
