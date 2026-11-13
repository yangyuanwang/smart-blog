<script setup lang="ts">
// 文章详情：SSR 正文 + 客户端 Shiki 双主题高亮 + TOC 联动 + 阅读进度 + 点赞/浏览 + AI 摘要 + SEO
import { codeToHtml } from 'shiki'

interface TocItem { level: number, id: string, text: string }
interface ArticleItem {
  aid: number
  title: string
  digest: string
  categoryName: string
  authorName: string
  createTime: string
  updateTime?: string
  visitCount: number
  thumbsUp: number
  logo?: string
  seoTitle?: string
  seoKeywords?: string
  seoDesc?: string
  aiSummary?: string
  aiGeneratedAt?: string
  readingMinutes?: number
}
interface DetailPayload {
  article: ArticleItem
  html: string
  toc: TocItem[]
  prev: { aid: number, title: string } | null
  next: { aid: number, title: string } | null
  related: ArticleItem[]
  comments: unknown[]
  allowComment: boolean
  views: number
}

const route = useRoute()
const id = Number(route.params.id)

const { data, error } = await useAsyncData(`article-${id}`, () => api<DetailPayload>(`/articles/${id}`))

if (error.value || !data.value) {
  throw createError({ statusCode: 404, statusMessage: 'Article Not Found', fatal: true })
}

const article = computed(() => data.value!.article)
const bodyHtml = ref(data.value!.html)
const highlighted = ref(false)

// Shiki 双主题高亮：水合后替换 pre>code，亮暗主题随 class 切换（无重渲）
onMounted(async () => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    // 仍执行高亮（非动效），但不阻塞
  }
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(`<div id="root">${data.value!.html}</div>`, 'text/html')
    const blocks = doc.querySelectorAll('pre > code')
    for (const block of Array.from(blocks)) {
      const cls = block.className || ''
      const match = /language-([\w-]+)/.exec(cls)
      const lang = match?.[1] ?? 'text'
      const code = block.textContent ?? ''
      try {
        const html = await codeToHtml(code, {
          lang,
          themes: { light: 'github-light', dark: 'github-dark' },
          cssVariablePrefix: '--shiki'
        })
        const wrapper = doc.createElement('div')
        wrapper.innerHTML = html
        block.parentElement?.replaceWith(wrapper.firstElementChild ?? block)
      } catch {
        // 不支持的语言保持原样
      }
    }
    bodyHtml.value = doc.getElementById('root')!.innerHTML
  } finally {
    highlighted.value = true
  }
})

// 阅读进度条
const progress = ref(0)
function onScroll () {
  const el = document.getElementById('article-body')
  if (!el) {
    return
  }
  const start = el.offsetTop - 64
  const total = el.offsetHeight
  const current = window.scrollY - start
  progress.value = Math.min(100, Math.max(0, (current / Math.max(total, 1)) * 100))
}
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

// TOC 滚动联动
const activeToc = ref('')
let tocObserver: IntersectionObserver | null = null
onMounted(() => {
  if (!data.value?.toc?.length) {
    return
  }
  tocObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        activeToc.value = entry.target.id
      }
    }
  }, { rootMargin: '-64px 0px -70% 0px' })
  for (const item of data.value.toc) {
    const el = document.getElementById(item.id)
    if (el) {
      tocObserver.observe(el)
    }
  }
})
onBeforeUnmount(() => tocObserver?.disconnect())

// 浏览上报（会话去重）与点赞
const views = ref(data.value!.views)
const likes = ref(article.value.thumbsUp ?? 0)
const liked = ref(false)
const likeFeedback = ref('')

onMounted(async () => {
  try {
    await api(`/articles/${id}/view`, {
      method: 'POST',
      body: { sessionId: getSessionId() }
    })
  } catch {
    // 浏览上报失败不影响阅读
  }
})

async function like () {
  try {
    const result = await api<{ likes: number, first: boolean }>(`/articles/${id}/like`, {
      method: 'POST',
      body: { sessionId: getSessionId() }
    })
    likes.value = result.likes
    liked.value = true
    if (result.first) {
      likeFeedback.value = '已加上一赞'
    }
  } catch (error) {
    likeFeedback.value = (error as Error).message || '点赞失败'
  }
}

const pageTitle = computed(() => article.value.seoTitle || article.value.title)
const origin = useRequestURL().origin
useSeoMeta({
  title: pageTitle.value,
  description: article.value.seoDesc || article.value.digest,
  keywords: article.value.seoKeywords,
  ogTitle: pageTitle.value,
  ogDescription: article.value.seoDesc || article.value.digest,
  ogType: 'article',
  ogImage: article.value.logo
    ? (article.value.logo.startsWith('http') ? article.value.logo : origin + article.value.logo)
    : undefined,
  twitterCard: 'summary_large_image'
})
useHead({
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'BlogPosting',
        headline: article.value.title,
        description: article.value.seoDesc || article.value.digest,
        datePublished: article.value.createTime,
        author: { '@type': 'Person', name: article.value.authorName || '博主' },
        mainEntityOfPage: `${useRequestURL().origin}/article/${article.value.aid}`
      })
    }
  ]
})

function fmtDate (value?: string) {
  return value?.slice(0, 10) ?? ''
}
</script>

<template>
  <div>
    <!-- 阅读进度条 -->
    <div
      class="fixed top-0 left-0 h-0.5 z-50 transition-none"
      :style="{ width: `${progress}%`, background: 'linear-gradient(90deg, var(--color-accent), var(--color-accent-2))' }"
      aria-hidden="true"
    />

    <div class="grid gap-8 lg:grid-cols-[1fr_220px]">
      <article>
        <!-- 封面 Hero：有封面时大图 + 渐变遮罩；无封面时渐变横幅 -->
        <header class="relative rounded-2xl overflow-hidden border border-app">
          <div class="h-56 sm:h-72 bg-surface-2 overflow-hidden">
            <img
              v-if="article.logo"
              :src="article.logo"
              :alt="`${article.title} 封面`"
              class="w-full h-full object-cover"
            >
            <div
              v-else
              class="w-full h-full glow-hero bg-surface-2 grid place-items-center"
            >
              <span class="font-serif text-6xl gradient-text select-none" aria-hidden="true">{{ article.title.slice(0, 2) }}</span>
            </div>
          </div>
          <!-- 底部信息条：分类 + 标题 + 元数据 -->
          <div class="bg-surface p-5 sm:p-6">
            <div class="flex flex-wrap items-center gap-2 text-xs text-muted">
              <span
                v-if="article.categoryName"
                class="px-2 py-0.5 rounded-md bg-surface-2"
              >{{ article.categoryName }}</span>
              <time>发布于 {{ fmtDate(article.createTime) }}</time>
              <span v-if="article.readingMinutes">阅读约 {{ article.readingMinutes }} 分钟</span>
              <span>{{ views }} 次浏览</span>
            </div>
            <h1 class="mt-3 font-serif text-3xl sm:text-4xl font-bold leading-tight">{{ article.title }}</h1>
          </div>
        </header>

        <!-- AI 摘要卡 -->
        <aside
          v-if="article.aiSummary"
          class="mt-6 rounded-xl border border-app bg-surface p-4 flex gap-3"
          aria-label="AI 摘要"
        >
          <span class="shrink-0 text-xs font-medium px-2 py-1 rounded-md bg-surface-2 gradient-text">AI 摘要</span>
          <p class="text-sm text-muted leading-relaxed">{{ article.aiSummary }}</p>
        </aside>

        <!-- 操作栏：点赞 + 分享提示 -->
        <div class="mt-6 flex items-center gap-3 pb-6 border-b border-app">
          <button
            type="button"
            class="inline-flex items-center gap-2 px-4 py-2 rounded-xl border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors text-sm"
            :aria-pressed="liked"
            @click="like"
          >
            <span aria-hidden="true">{{ liked ? '💗' : '🤍' }}</span>
            <span>{{ likes }}</span>
          </button>
          <p v-if="likeFeedback" class="text-sm text-muted" role="status">{{ likeFeedback }}</p>
          <span class="ml-auto text-xs text-muted">SSR 直出 · Shiki 高亮</span>
        </div>

        <!-- 正文（SSR 直出 + 客户端 Shiki 增强）；空正文给优雅态 -->
        <div
          v-if="bodyHtml.trim()"
          id="article-body"
          class="article-body mt-8"
          :class="{ 'is-highlighted': highlighted }"
          v-html="bodyHtml"
        />
        <div v-else class="mt-8 rounded-xl border border-app bg-surface px-4 py-12 text-center text-muted">
          本文暂无正文内容
        </div>

        <!-- 上一篇 / 下一篇 -->
        <nav class="mt-10 grid gap-3 sm:grid-cols-2" aria-label="上下篇">
          <NuxtLink
            v-if="data?.prev"
            :to="`/article/${data.prev.aid}`"
            class="rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
          >
            <p class="text-xs text-muted">← 上一篇</p>
            <p class="mt-1 text-sm font-medium line-clamp-1">{{ data.prev.title }}</p>
          </NuxtLink>
          <span v-else />
          <NuxtLink
            v-if="data?.next"
            :to="`/article/${data.next.aid}`"
            class="rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors sm:text-right"
          >
            <p class="text-xs text-muted">下一篇 →</p>
            <p class="mt-1 text-sm font-medium line-clamp-1">{{ data.next.title }}</p>
          </NuxtLink>
        </nav>

        <!-- 相关推荐 -->
        <section v-if="data?.related?.length" class="mt-10" aria-labelledby="related-heading">
          <h2 id="related-heading" class="font-serif text-xl font-bold">相关推荐</h2>
          <ul class="mt-4 grid gap-4 sm:grid-cols-2">
            <li v-for="item in data.related" :key="item.aid">
              <ArticleCard :article="item" :show-digest="false" />
            </li>
          </ul>
        </section>

        <CommentSection :aid="id" :allow-comment="!!data?.allowComment" />
      </article>

      <!-- TOC 侧栏 -->
      <aside v-if="data?.toc?.length" class="hidden lg:block lg:sticky lg:top-20 self-start" aria-label="目录">
        <nav class="rounded-xl border border-app bg-surface p-4 max-h-[70vh] overflow-auto">
          <h2 class="font-serif font-semibold text-sm">目录</h2>
          <ul class="mt-2 space-y-1 text-sm">
            <li v-for="item in data.toc" :key="item.id" :style="{ paddingLeft: `${(item.level - 1) * 12}px` }">
              <a
                :href="`#${item.id}`"
                class="block py-1 rounded hover:text-main focus-visible:ring-2 ring-app"
                :class="activeToc === item.id ? 'text-main font-medium' : 'text-muted'"
              >{{ item.text }}</a>
            </li>
          </ul>
        </nav>
      </aside>
    </div>
  </div>
</template>

<style>
/* 正文排版：编辑感节奏 + 亮暗统一适配 */
.article-body {
  line-height: 1.9;
  font-size: 1rem;
  overflow-wrap: break-word;
  letter-spacing: 0.01em;
}
.article-body h1, .article-body h2, .article-body h3, .article-body h4 {
  font-family: var(--font-serif);
  font-weight: 700;
  margin: 2em 0 0.7em;
  scroll-margin-top: 5rem;
}
.article-body h1 { font-size: 1.6rem; }
.article-body h2 {
  font-size: 1.4rem;
  padding-left: 0.75rem;
  border-left: 4px solid var(--color-accent);
}
.article-body h3 { font-size: 1.15rem; }
.article-body h2 + p, .article-body h3 + p { margin-top: 0.4em; }
.article-body p { margin: 0.9em 0; }
.article-body a { color: var(--color-accent); text-decoration: underline; text-underline-offset: 3px; }
.article-body ul, .article-body ol { margin: 0.9em 0; padding-left: 1.6em; }
.article-body li { margin: 0.35em 0; }
.article-body ul { list-style: disc; }
.article-body ol { list-style: decimal; }
.article-body li::marker { color: var(--color-accent-2); }
.article-body blockquote {
  margin: 1.4em 0;
  padding: 0.8em 1.2em;
  border-left: 3px solid var(--color-accent-2);
  background: var(--surface-2);
  border-radius: 0 10px 10px 0;
  color: var(--text-muted);
}
.article-body blockquote p { margin: 0.3em 0; }
.article-body pre {
  position: relative;
  margin: 1.4em 0;
  padding: 1rem 1.1rem;
  border-radius: 12px;
  overflow-x: auto;
  background: var(--surface-2);
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 0.85rem;
  line-height: 1.7;
}
.article-body :not(pre) > code {
  font-family: var(--font-mono);
  font-size: 0.85em;
  padding: 0.15em 0.45em;
  border-radius: 6px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  color: var(--color-accent-2);
}
.article-body hr {
  border: 0;
  height: 1px;
  margin: 2.2em auto;
  width: 60%;
  background: linear-gradient(90deg, transparent, var(--border), transparent);
}
.article-body img {
  max-width: 100%;
  border-radius: 12px;
  margin: 1.4em auto;
  border: 1px solid var(--border);
}
.article-body table { width: 100%; border-collapse: collapse; margin: 1.4em 0; font-size: 0.9rem; border-radius: 10px; overflow: hidden; }
.article-body th, .article-body td { border: 1px solid var(--border); padding: 0.55em 0.9em; text-align: left; }
.article-body th { background: var(--surface-2); font-weight: 600; }
.article-body strong { color: var(--text); font-weight: 700; }
/* Shiki 双主题：跟随暗色 class 切换 */
.article-body .shiki, .article-body .shiki span {
  background-color: transparent !important;
}
html:not(.dark) .article-body .shiki span { color: var(--shiki-light) !important; }
html.dark .article-body .shiki span { color: var(--shiki-dark) !important; }
</style>
