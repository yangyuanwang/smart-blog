<script setup lang="ts">
// 文章卡片：列表页与首页共用
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
}
withDefaults(defineProps<Props>(), { showDigest: true })

function fmtDate (value?: string) {
  return value?.slice(0, 10) ?? ''
}
</script>

<template>
  <NuxtLink
    :to="`/article/${article.aid}`"
    class="group block h-full rounded-xl border border-app bg-surface p-5 hover:bg-surface-2 hover:-translate-y-0.5 transition-all focus-visible:ring-2 ring-app"
  >
    <div class="flex items-center gap-2 text-xs text-muted">
      <span v-if="article.categoryName" class="px-2 py-0.5 rounded-md bg-surface-2">{{ article.categoryName }}</span>
      <time v-if="article.createTime">{{ fmtDate(article.createTime) }}</time>
      <span v-if="article.isTop === 1" class="font-medium" style="color: var(--color-warn)">置顶</span>
    </div>
    <h3 class="mt-2 font-serif text-lg font-semibold group-hover:gradient-text transition-colors">
      {{ article.title }}
    </h3>
    <p v-if="showDigest && article.digest" class="mt-2 text-sm text-muted line-clamp-2">{{ article.digest }}</p>
    <p v-if="article.readingMinutes" class="mt-3 text-xs text-muted">
      {{ article.authorName ?? '' }} · 阅读约 {{ article.readingMinutes }} 分钟
    </p>
  </NuxtLink>
</template>
