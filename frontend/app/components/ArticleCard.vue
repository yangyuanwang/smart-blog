<script setup lang="ts">
// 文章卡片：封面图（有则展示）+ 分类/日期 + 标题 + 摘要 + 作者/时长
interface Props {
  article: {
    aid: number
    title: string
    digest?: string
    categoryName?: string
    authorName?: string
    createTime?: string
    isTop?: number
    readingMinutes?: number
    logo?: string
  }
  showDigest?: boolean
  featured?: boolean
}
withDefaults(defineProps<Props>(), { showDigest: true, featured: false })

function fmtDate (value?: string) {
  return value?.slice(0, 10) ?? ''
}
</script>

<template>
  <NuxtLink
    :to="`/article/${article.aid}`"
    class="group block h-full rounded-xl border border-app bg-surface overflow-hidden hover:bg-surface-2 hover:-translate-y-0.5 transition-all focus-visible:ring-2 ring-app"
  >
    <!-- 封面：无图时显示渐变占位 + 标题字 -->
    <div :class="featured ? 'h-44' : 'h-32'" class="relative bg-surface-2 overflow-hidden">
      <img
        v-if="article.logo"
        :src="article.logo"
        :alt="`${article.title} 封面`"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        loading="lazy"
      >
      <div v-else class="w-full h-full grid place-items-center bg-gradient-to-br from-[color:var(--surface-2)] to-[color:var(--surface)]">
        <span class="font-serif text-3xl gradient-text" aria-hidden="true">{{ article.title.slice(0, 2) }}</span>
      </div>
      <span
        v-if="article.isTop === 1"
        class="absolute top-2 right-2 text-xs px-2 py-0.5 rounded-md bg-black/50 text-white backdrop-blur"
        style="color: var(--color-warn)"
      >置顶</span>
    </div>

    <div class="p-5">
      <div class="flex items-center gap-2 text-xs text-muted">
        <span v-if="article.categoryName" class="px-2 py-0.5 rounded-md bg-surface-2">{{ article.categoryName }}</span>
        <time v-if="article.createTime">{{ fmtDate(article.createTime) }}</time>
      </div>
      <h3 class="mt-2 font-serif text-lg font-semibold group-hover:gradient-text transition-colors line-clamp-2">
        {{ article.title }}
      </h3>
      <p v-if="showDigest && article.digest" class="mt-2 text-sm text-muted line-clamp-2">{{ article.digest }}</p>
      <p v-if="article.readingMinutes" class="mt-3 text-xs text-muted">
        {{ article.authorName ?? '' }} · 阅读约 {{ article.readingMinutes }} 分钟
      </p>
    </div>
  </NuxtLink>
</template>
