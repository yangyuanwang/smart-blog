<script setup lang="ts">
// 文章详情：返回列表 → 头部信息（栏目/标题/摘要/作者/日期）→ 次要信息 → 封面 → 正文 → 标签/上下篇/相关 → 评论
// 侧栏仅保留目录（吸顶、不独立内滚、过长折叠）；移动端目录收成正文前按钮
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
  tagNames?: string
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

// 阅读进度条 + 浮动工具条显隐
const progress = ref(0)
const dockVisible = ref(false)
function onScroll () {
  const el = document.getElementById('article-body')
  if (!el) {
    return
  }
  const start = el.offsetTop - 64
  const total = el.offsetHeight
  const current = window.scrollY - start
  progress.value = Math.min(100, Math.max(0, (current / Math.max(total, 1)) * 100))
  dockVisible.value = window.scrollY > 420
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
const toast = useToast()

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
      toast.success('感谢点赞')
    }
  } catch (error) {
    toast.success((error as Error).message || '点赞失败')
  }
}

const pageTitle = computed(() => article.value.seoTitle || article.value.title)
const origin = useRequestURL().origin

// 封面：仅在 URL 有效且加载成功时展示
const logoFailed = ref(false)
const showCover = computed(() => !!article.value.logo && !logoFailed.value)

// 文章标签：逗号分隔文本 → 列表
const tagList = computed(() =>
  (article.value.tagNames ?? '').split(/[,，]/).map(t => t.trim()).filter(Boolean)
)

// 目录折叠：过长时默认收起，避免吸顶栏被撑满
const TOC_COLLAPSE = 12
const tocExpanded = ref(false)
const tocItems = computed(() => data.value?.toc ?? [])
const visibleToc = computed(() =>
  tocExpanded.value ? tocItems.value : tocItems.value.slice(0, TOC_COLLAPSE)
)
const hasHiddenToc = computed(() => tocItems.value.length > TOC_COLLAPSE)

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

function scrollToTop () {
  window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
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

    <div class="grid gap-10 lg:grid-cols-[1fr_230px]">
      <article>
        <!-- 返回列表 -->
        <NuxtLink
          to="/articles"
          class="inline-flex items-center gap-1.5 text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded"
        >
          <span aria-hidden="true">←</span> 返回文章列表
        </NuxtLink>

        <!-- 头部信息：栏目 / 标题 / 摘要 / 作者与日期 -->
        <header class="mt-4">
          <span
            v-if="article.categoryName"
            class="inline-block px-2.5 py-1 rounded-md text-xs font-medium text-white"
            :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
          >{{ article.categoryName }}</span>
          <h1 class="mt-3 font-serif text-3xl sm:text-4xl font-bold leading-tight">
            {{ article.title }}
          </h1>
          <p v-if="article.digest" class="mt-3 text-sm text-muted leading-relaxed">{{ article.digest }}</p>
          <div class="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
            <span>{{ article.authorName || '博主' }}</span>
            <time class="text-muted">发布于 {{ fmtDate(article.createTime) }}</time>
          </div>
          <!-- 次要信息 -->
          <div class="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted">
            <span v-if="article.readingMinutes" class="inline-flex items-center gap-1.5">
              <span aria-hidden="true">⏱</span>约 {{ article.readingMinutes }} 分钟
            </span>
            <span class="inline-flex items-center gap-1.5">
              <span aria-hidden="true">👁</span>{{ views }} 次浏览
            </span>
          </div>
        </header>

        <!-- 封面：仅有有效封面时展示 -->
        <figure v-if="showCover" class="mt-6 rounded-2xl overflow-hidden border border-app">
          <img
            :src="article.logo"
            :alt="`${article.title} 封面`"
            class="w-full aspect-[21/9] object-cover"
            @error="logoFailed = true"
          >
        </figure>

        <!-- 移动端目录：正文前的可展开按钮 -->
        <details v-if="tocItems.length" class="mt-6 lg:hidden rounded-xl border border-app bg-surface px-4 py-3">
          <summary class="cursor-pointer text-sm font-medium select-none focus-visible:ring-2 ring-app rounded">
            文章目录（{{ tocItems.length }} 节）
          </summary>
          <nav class="mt-2" aria-label="文章目录">
            <ul class="space-y-1 text-sm">
              <li v-for="item in tocItems" :key="item.id" :style="{ paddingLeft: `${(item.level - 1) * 12}px` }">
                <a :href="`#${item.id}`" class="block py-1 rounded text-muted hover:text-main focus-visible:ring-2 ring-app">{{ item.text }}</a>
              </li>
            </ul>
          </nav>
        </details>

        <!-- AI 摘要卡 -->
        <aside
          v-if="article.aiSummary"
          class="mt-6 rounded-xl border border-app bg-surface p-4 flex gap-3"
          aria-label="AI 摘要"
        >
          <span class="shrink-0 text-xs font-medium px-2 py-1 rounded-md bg-surface-2 gradient-text">AI 摘要</span>
          <p class="text-sm text-muted leading-relaxed">{{ article.aiSummary }}</p>
        </aside>

        <!-- 正文：干净纸面 + 顶部渐变条 -->
        <div class="paper paper-base mt-6 rounded-2xl border border-app px-6 py-8 sm:px-12 sm:py-10 overflow-hidden">
          <div class="paper-accent" aria-hidden="true" />
          <div
            v-if="bodyHtml.trim()"
            id="article-body"
            class="article-body"
            :class="{ 'is-highlighted': highlighted }"
            v-html="bodyHtml"
          />
          <div v-else class="py-12 text-center text-muted">
            本文暂无正文内容
          </div>
        </div>

        <!-- 文章标签 -->
        <div v-if="tagList.length" class="mt-6 flex flex-wrap gap-2 text-sm">
          <NuxtLink
            v-for="tag in tagList"
            :key="tag"
            :to="{ path: '/articles', query: { tag } }"
            class="px-2.5 py-1 rounded-full border border-app text-muted hover:bg-surface-2 hover:text-main focus-visible:ring-2 ring-app transition-colors"
          ># {{ tag }}</NuxtLink>
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

      <!-- 桌面端右侧目录：吸顶、不做独立滚动区，过长时折叠 -->
      <aside v-if="data" class="hidden lg:block lg:sticky lg:top-20 self-start" aria-label="文章目录">
        <nav v-if="tocItems.length" class="rounded-xl border border-app bg-surface p-4" aria-label="目录">
          <h2 class="font-serif font-semibold text-sm">目录</h2>
          <ul class="mt-2 space-y-1 text-sm">
            <li v-for="item in visibleToc" :key="item.id" :style="{ paddingLeft: `${(item.level - 1) * 12}px` }">
              <a
                :href="`#${item.id}`"
                class="block py-1 rounded hover:text-main focus-visible:ring-2 ring-app"
                :class="activeToc === item.id ? 'text-main font-medium' : 'text-muted'"
              >{{ item.text }}</a>
            </li>
          </ul>
          <button
            v-if="hasHiddenToc"
            type="button"
            class="mt-2 text-xs text-muted underline hover:text-main focus-visible:ring-2 ring-app rounded"
            @click="tocExpanded = !tocExpanded"
          >{{ tocExpanded ? '收起目录' : `展开全部（${tocItems.length} 节）` }}</button>
        </nav>
      </aside>
    </div>

    <!-- 浮动阅读工具条：滚过首屏后出现（点赞 / 评论 / 回顶部） -->
    <div
      class="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 transition-all duration-300"
      :class="dockVisible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-3 pointer-events-none'"
    >
      <div class="flex items-center gap-1 rounded-full border border-app bg-surface/90 backdrop-blur px-2 py-1.5 shadow-xl">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors text-sm"
          :aria-pressed="liked"
          :aria-label="liked ? '取消点赞' : '点赞'"
          @click="like"
        >
          <span aria-hidden="true">{{ liked ? '💗' : '🤍' }}</span>
          <span class="tabular-nums">{{ likes }}</span>
        </button>
        <span class="w-px h-5 bg-[color:var(--border)]" aria-hidden="true" />
        <a
          href="#comments"
          class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors text-sm"
          :aria-label="`跳到评论（${data?.comments?.length ?? 0} 条）`"
        >
          <span aria-hidden="true">💬</span>
          <span class="tabular-nums">{{ data?.comments?.length ?? 0 }}</span>
        </a>
        <span class="w-px h-5 bg-[color:var(--border)]" aria-hidden="true" />
        <button
          type="button"
          class="px-3.5 py-2 rounded-full hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors text-sm"
          aria-label="回到顶部"
          @click="scrollToTop"
        >
          <span aria-hidden="true">↑</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style>
/* 纸面容器：干净高级——顶部品牌渐变条 + 柔和悬浮阴影，无杂纹理 */
.paper-base {
  position: relative;
  background-color: var(--surface);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 12px 32px rgba(0, 0, 0, 0.05);
}
.paper-accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 16px 16px 0 0;
  background: linear-gradient(90deg, var(--color-accent), var(--color-accent-2));
  opacity: 0.85;
}

/* 正文排版：编辑感节奏 + 亮暗统一适配 */
.article-body {
  line-height: 1.9;
  font-size: 1rem;
  overflow-wrap: break-word;
  letter-spacing: 0.01em;
}
/* 首字下沉：衬线 + 品牌渐变，编辑刊头感 */
.article-body > p:first-of-type::first-letter {
  font-family: var(--font-serif);
  font-size: 3em;
  line-height: 1;
  font-weight: 700;
  margin-right: 0.1em;
  float: left;
  background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
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
  position: relative;
  margin: 1.6em 0;
  padding: 1em 1.4em 1em 3em;
  border-left: 3px solid var(--color-accent-2);
  background: var(--surface-2);
  border-radius: 0 12px 12px 0;
  color: var(--text-muted);
}
.article-body blockquote::before {
  content: "\201C";
  position: absolute;
  left: 0.6em;
  top: -0.1em;
  font-family: var(--font-serif);
  font-size: 2.6em;
  line-height: 1;
  color: var(--color-accent-2);
  opacity: 0.4;
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
