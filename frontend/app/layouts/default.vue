<script setup lang="ts">
// 全站默认布局：导航 + 内容区 + 页脚，键盘可达、焦点可见、窄屏抽屉菜单
const route = useRoute()
const colorMode = useColorMode()
const mobileOpen = ref(false)

const links = [
  { to: '/articles', label: '文章' },
  { to: '/projects', label: '作品集' },
  { to: '/messages', label: '留言' },
  { to: '/diaries', label: '日记' },
  { to: '/links', label: '友链' },
  { to: '/about', label: '关于' }
]

// 路由变化时收起移动端菜单并复位焦点到内容区
watch(() => route.fullPath, () => {
  mobileOpen.value = false
})

function toggleTheme () {
  colorMode.preference = colorMode.value === 'dark' ? 'light' : 'dark'
}

const siteName = computed(() => '智序 SMART WORKSPACE')
</script>

<template>
  <div class="min-h-screen flex flex-col bg-app text-main">
    <a
      href="#main"
      class="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:top-2 focus:left-2 focus:px-3 focus:py-2 focus:rounded-lg focus:bg-surface focus:ring-2"
    >跳到主要内容</a>

    <header class="sticky top-0 z-40 border-b border-app bg-app/85 backdrop-blur">
      <nav class="mx-auto max-w-6xl px-4 h-14 flex items-center gap-4" aria-label="主导航">
        <NuxtLink to="/" class="font-serif font-bold tracking-wide shrink-0" @click="mobileOpen = false">
          <span class="gradient-text">{{ siteName }}</span>
        </NuxtLink>

        <ul class="hidden md:flex items-center gap-1 text-sm ml-2">
          <li v-for="link in links" :key="link.to">
            <NuxtLink
              :to="link.to"
              class="px-3 py-1.5 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
              active-class="text-main font-medium bg-surface-2"
            >{{ link.label }}</NuxtLink>
          </li>
        </ul>

        <div class="ml-auto flex items-center gap-1">
          <button
            type="button"
            class="p-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
            :aria-label="colorMode.value === 'dark' ? '切换到亮色模式' : '切换到暗色模式'"
            @click="toggleTheme"
          >
            <span aria-hidden="true">{{ colorMode.value === 'dark' ? '🌙' : '☀️' }}</span>
          </button>
          <NuxtLink
            to="/admin"
            class="hidden md:inline-flex px-3 py-1.5 text-sm rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
          >后台</NuxtLink>
          <button
            type="button"
            class="md:hidden p-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
            :aria-expanded="mobileOpen"
            aria-label="打开菜单"
            @click="mobileOpen = !mobileOpen"
          >
            <span aria-hidden="true">☰</span>
          </button>
        </div>
      </nav>

      <!-- 窄屏抽屉导航 -->
      <div v-if="mobileOpen" class="md:hidden border-t border-app bg-app px-4 py-2">
        <ul class="flex flex-col text-sm">
          <li v-for="link in links" :key="link.to">
            <NuxtLink
              :to="link.to"
              class="block px-3 py-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
              active-class="bg-surface-2 font-medium"
            >{{ link.label }}</NuxtLink>
          </li>
          <li>
            <NuxtLink to="/admin" class="block px-3 py-2 rounded-lg hover:bg-surface-2">后台</NuxtLink>
          </li>
        </ul>
      </div>
    </header>

    <main id="main" class="flex-1 mx-auto w-full max-w-6xl px-4 py-8">
      <slot />
    </main>

    <footer class="border-t border-app mt-8">
      <div class="mx-auto max-w-6xl px-4 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-sm text-muted">
        <p>© {{ new Date().getFullYear() }} 智序 SMART WORKSPACE</p>
        <p class="flex items-center gap-3">
          <a href="/rss.xml" class="hover:text-main focus-visible:ring-2 ring-app rounded">RSS</a>
          <span aria-hidden="true">·</span>
          <span>Nuxt 3 × Spring Boot 3</span>
        </p>
      </div>
    </footer>
  </div>
</template>
