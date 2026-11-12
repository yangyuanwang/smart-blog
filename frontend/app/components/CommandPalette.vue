<script setup lang="ts">
// ⌘K 全局命令面板：导航快捷入口 + 文章搜索（键盘可达，Esc 关闭，焦点回返）
interface SearchItem {
  aid: number
  title: string
  digest?: string
}

const open = ref(false)
const query = ref('')
const results = ref<SearchItem[]>([])
const activeIndex = ref(0)
const trigger = ref<HTMLElement | null>(null)

const navItems = [
  { to: '/', label: '首页', icon: '🏠' },
  { to: '/articles', label: '文章列表', icon: '📚' },
  { to: '/projects', label: '作品集', icon: '🧩' },
  { to: '/messages', label: '留言板', icon: '💬' },
  { to: '/diaries', label: '日记', icon: '📔' },
  { to: '/links', label: '友链', icon: '🔗' },
  { to: '/about', label: '关于', icon: '👤' }
]

const filteredNav = computed(() =>
  navItems.filter(item => !query.value || item.label.includes(query.value))
)

// 搜索防抖（300ms），query 非空时请求后端
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(query, (value) => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  const keyword = value.trim()
  if (!keyword) {
    results.value = []
    activeIndex.value = 0
    return
  }
  debounceTimer = setTimeout(async () => {
    try {
      results.value = await api<SearchItem[]>(`/search?q=${encodeURIComponent(keyword)}&limit=8`)
    } catch {
      results.value = []
    }
    activeIndex.value = 0
  }, 300)
})

watch(open, (value) => {
  if (value) {
    query.value = ''
    results.value = []
  }
})

function go (to: string) {
  open.value = false
  navigateTo(to)
}

function moveActive (delta: number) {
  const total = filteredNav.value.length + results.value.length
  if (!total) {
    return
  }
  activeIndex.value = (activeIndex.value + delta + total) % total
}

function chooseActive () {
  const navCount = filteredNav.value.length
  if (activeIndex.value < navCount) {
    const item = filteredNav.value[activeIndex.value]
    if (item) {
      go(item.to)
    }
    return
  }
  const article = results.value[activeIndex.value - navCount]
  if (article) {
    go(`/article/${article.aid}`)
  }
}

function onKeydown (event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (!open.value) {
      trigger.value = document.activeElement as HTMLElement
    }
    open.value = !open.value
    return
  }
  if (!open.value) {
    return
  }
  if (event.key === 'Escape') {
    open.value = false
    nextTick(() => trigger.value?.focus())
  } else if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveActive(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(-1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    chooseActive()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

// 当前高亮项属于文章结果还是导航
const activeNavIndex = computed(() => (activeIndex.value < filteredNav.value.length ? activeIndex.value : -1))
const activeResultIndex = computed(() => activeIndex.value - filteredNav.value.length)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-start justify-center pt-[12vh] px-4 bg-black/50 backdrop-blur-sm"
      @click.self="open = false; trigger?.focus()"
    >
      <div
        class="w-full max-w-xl rounded-2xl border border-app bg-surface shadow-2xl overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-label="全局命令面板"
      >
        <input
          v-model="query"
          autofocus
          type="text"
          placeholder="搜索文章，或输入页面名跳转…"
          aria-label="搜索"
          class="w-full px-4 py-3.5 bg-transparent border-b border-app text-sm focus:outline-none"
          @keydown="onKeydown"
        >
        <div class="max-h-[50vh] overflow-y-auto py-1">
          <p v-if="!filteredNav.length && !results.length" class="px-4 py-6 text-sm text-muted text-center">
            {{ query ? '没有匹配结果' : '输入以搜索' }}
          </p>

          <p v-if="filteredNav.length" class="px-4 pt-2 pb-1 text-xs text-muted">页面</p>
          <button
            v-for="(item, index) in filteredNav"
            :key="item.to"
            type="button"
            class="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-left hover:bg-surface-2 focus-visible:ring-2 ring-app"
            :class="activeNavIndex === index ? 'bg-surface-2' : ''"
            @click="go(item.to)"
            @mousemove="activeIndex = index"
          >
            <span aria-hidden="true">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </button>

          <p v-if="results.length" class="px-4 pt-2 pb-1 text-xs text-muted">文章</p>
          <button
            v-for="(article, index) in results"
            :key="article.aid"
            type="button"
            class="w-full px-4 py-2.5 text-sm text-left hover:bg-surface-2 focus-visible:ring-2 ring-app"
            :class="activeResultIndex === index ? 'bg-surface-2' : ''"
            @click="go(`/article/${article.aid}`)"
            @mousemove="activeIndex = filteredNav.length + index"
          >
            <p class="font-medium">{{ article.title }}</p>
            <p v-if="article.digest" class="mt-0.5 text-xs text-muted line-clamp-1">{{ article.digest }}</p>
          </button>
        </div>
        <div class="px-4 py-2 border-t border-app text-xs text-muted flex gap-3">
          <span>↑↓ 选择</span>
          <span>Enter 跳转</span>
          <span>Esc 关闭</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>
