<script setup lang="ts">
// 后台工作台布局：左侧导航 + 顶栏，窄屏抽屉
const route = useRoute()
const collapsed = ref(false)
const mobileOpen = ref(false)

const groups = [
  {
    label: '内容',
    items: [
      { to: '/admin/articles', label: '文章管理', icon: '📝' },
      { to: '/admin/categories', label: '栏目与标签', icon: '🗂' },
      { to: '/admin/projects', label: '作品集', icon: '🧩' }
    ]
  },
  {
    label: '互动',
    items: [
      { to: '/admin/comments', label: '评论审核', icon: '💬' },
      { to: '/admin/messages', label: '留言审核', icon: '📨' },
      { to: '/admin/links', label: '友链与申请', icon: '🔗' }
    ]
  },
  {
    label: '日常',
    items: [
      { to: '/admin/diaries', label: '日记', icon: '📔' },
      { to: '/admin/notices', label: '公告', icon: '📢' }
    ]
  },
  {
    label: '系统',
    items: [
      { to: '/admin/users', label: '用户管理', icon: '👤' },
      { to: '/admin/logs', label: '登录日志', icon: '📋' },
      { to: '/admin/settings', label: '站点设置', icon: '⚙️' },
      { to: '/admin/profile', label: '个人中心', icon: '🧑' }
    ]
  }
]

watch(() => route.fullPath, () => {
  mobileOpen.value = false
})

async function logout () {
  try {
    const { refresh } = readTokens()
    if (refresh) {
      await api('/auth/logout', { method: 'POST', body: { refreshToken: refresh } }).catch(() => null)
    }
  } finally {
    clearTokens()
    await navigateTo('/admin/login')
  }
}
</script>

<template>
  <div class="min-h-screen flex bg-app text-main">
    <!-- 侧栏 -->
    <aside
      class="hidden md:flex flex-col border-r border-app bg-surface transition-all"
      :class="collapsed ? 'w-16' : 'w-56'"
    >
      <div class="h-14 flex items-center px-4 border-b border-app">
        <NuxtLink to="/admin" class="font-serif font-bold truncate" :class="collapsed ? 'text-lg' : ''">
          {{ collapsed ? '智' : '智序控制台' }}
        </NuxtLink>
      </div>
      <nav class="flex-1 overflow-y-auto py-2 text-sm" aria-label="后台导航">
        <div v-for="group in groups" :key="group.label" class="px-2 py-1">
          <p v-if="!collapsed" class="px-3 py-1 text-xs text-muted uppercase tracking-wide">{{ group.label }}</p>
          <NuxtLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
            active-class="bg-surface-2 font-medium"
            :title="item.label"
          >
            <span aria-hidden="true">{{ item.icon }}</span>
            <span v-if="!collapsed" class="truncate">{{ item.label }}</span>
          </NuxtLink>
        </div>
      </nav>
      <div class="border-t border-app p-2 space-y-1">
        <button
          type="button"
          class="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app text-sm"
          @click="collapsed = !collapsed"
        >
          <span aria-hidden="true">{{ collapsed ? '»' : '«' }}</span>
          <span v-if="!collapsed">折叠</span>
        </button>
        <button
          type="button"
          class="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app text-sm"
          @click="logout"
        >
          <span aria-hidden="true">🚪</span>
          <span v-if="!collapsed">退出登录</span>
        </button>
      </div>
    </aside>

    <div class="flex-1 flex flex-col min-w-0">
      <!-- 顶栏 -->
      <header class="h-14 border-b border-app bg-app/85 backdrop-blur flex items-center gap-3 px-4 sticky top-0 z-30">
        <button
          type="button"
          class="md:hidden p-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
          aria-label="打开菜单"
          :aria-expanded="mobileOpen"
          @click="mobileOpen = !mobileOpen"
        >
          <span aria-hidden="true">☰</span>
        </button>
        <NuxtLink to="/" class="text-sm text-muted hover:text-main focus-visible:ring-2 ring-app rounded">← 回前台</NuxtLink>
        <span class="ml-auto text-sm text-muted">控制台</span>
      </header>

      <!-- 窄屏抽屉 -->
      <div v-if="mobileOpen" class="md:hidden border-b border-app bg-surface px-4 py-2 grid grid-cols-2 gap-1 text-sm">
        <NuxtLink
          v-for="group in groups.flatMap(g => g.items)"
          :key="group.to"
          :to="group.to"
          class="px-3 py-2 rounded-lg hover:bg-surface-2"
          active-class="bg-surface-2 font-medium"
        >{{ group.icon }} {{ group.label }}</NuxtLink>
        <button type="button" class="px-3 py-2 rounded-lg hover:bg-surface-2 text-left" @click="logout">🚪 退出登录</button>
      </div>

      <main class="flex-1 p-4 sm:p-6 min-w-0">
        <slot />
      </main>
    </div>
  </div>
</template>
