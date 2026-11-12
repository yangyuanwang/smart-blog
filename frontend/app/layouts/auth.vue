<script setup lang="ts">
// 认证页布局：居中卡片 + 品牌背景
const colorMode = useColorMode()
</script>

<template>
  <div class="min-h-screen grid place-items-center px-4 bg-app text-main relative overflow-hidden glow-hero">
    <button
      type="button"
      class="absolute top-4 right-4 p-2 rounded-lg hover:bg-surface-2 focus-visible:ring-2 ring-app"
      :aria-label="colorMode.value === 'dark' ? '切换到亮色模式' : '切换到暗色模式'"
      @click="colorMode.preference = colorMode.value === 'dark' ? 'light' : 'dark'"
    >
      <span aria-hidden="true">{{ colorMode.value === 'dark' ? '🌙' : '☀️' }}</span>
    </button>
    <div class="w-full max-w-sm">
      <p class="text-center font-serif font-bold text-2xl mb-6">
        <NuxtLink to="/" class="gradient-text focus-visible:ring-2 ring-app rounded">智序 SMART WORKSPACE</NuxtLink>
      </p>
      <slot />
      <p class="mt-6 text-center text-sm text-muted">
        <NuxtLink to="/" class="hover:text-main focus-visible:ring-2 ring-app rounded">← 返回首页</NuxtLink>
      </p>
    </div>
  </div>
</template>
