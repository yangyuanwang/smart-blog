<script setup lang="ts">
// 全局错误页：404 友好提示，其他错误提供返回入口
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()

const is404 = computed(() => props.error.statusCode === 404)

function goHome () {
  clearError({ redirect: '/' })
}

useSeoMeta({ title: is404.value ? '页面不存在' : '出错了' })
</script>

<template>
  <div class="min-h-screen grid place-items-center px-4 bg-app text-main">
    <div class="text-center">
      <p class="font-mono text-7xl font-bold gradient-text">{{ error.statusCode }}</p>
      <h1 class="mt-4 font-serif text-xl font-semibold">
        {{ is404 ? '这一页没有找到' : '出了点问题' }}
      </h1>
      <p class="mt-2 text-sm text-muted">
        {{ is404 ? '页面可能被移动或删除了，去首页或文章列表看看吧' : error.statusMessage || '请稍后重试' }}
      </p>
      <div class="mt-6 flex items-center justify-center gap-3">
        <button
          type="button"
          class="px-5 py-2.5 rounded-xl text-sm font-medium text-white focus-visible:ring-2 ring-app"
          :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
          @click="goHome"
        >回首页</button>
        <NuxtLink
          to="/articles"
          class="px-5 py-2.5 rounded-xl text-sm font-medium border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
        >浏览文章</NuxtLink>
      </div>
    </div>
  </div>
</template>
