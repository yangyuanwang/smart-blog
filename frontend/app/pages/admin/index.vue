<script setup lang="ts">
// 后台仪表盘：内容与互动统计卡片
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface Dashboard {
  articles: number
  published: number
  visits: number
  comments: number
  messages: number
  pendingMessages: number
  pendingLinkApplies: number
  categories: number
  links: number
  projects: number
  users: number
}

const { data, pending } = await useAsyncData('admin-dashboard', () => api<Dashboard>('/admin/dashboard'))

const cards = computed(() => [
  { label: '文章总数', value: data.value?.articles, hint: `已发布 ${data.value?.published ?? 0}` },
  { label: '总访问量', value: data.value?.visits, hint: '公开文章累计' },
  { label: '评论', value: data.value?.comments, hint: '全部评论' },
  { label: '留言', value: data.value?.messages, hint: `待审 ${data.value?.pendingMessages ?? 0}` },
  { label: '友链', value: data.value?.links, hint: `待审申请 ${data.value?.pendingLinkApplies ?? 0}` },
  { label: '作品', value: data.value?.projects, hint: `栏目 ${data.value?.categories ?? 0}` },
  { label: '用户', value: data.value?.users, hint: '注册账号' }
])

useSeoMeta({ title: '控制台' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">总览</h1>
    <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>
    <div v-else class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div
        v-for="card in cards"
        :key="card.label"
        class="rounded-xl border border-app bg-surface p-5"
      >
        <p class="text-sm text-muted">{{ card.label }}</p>
        <p class="mt-1 font-serif text-3xl font-bold">{{ card.value ?? '—' }}</p>
        <p class="mt-1 text-xs text-muted">{{ card.hint }}</p>
      </div>
    </div>
    <div class="mt-6 rounded-xl border border-app bg-surface p-5 text-sm text-muted">
      提示：浏览量每 5 分钟从 Redis 批量落库；AI 摘要在文章发布后异步生成（未配置 LLM 时自动跳过）。
    </div>
  </div>
</template>
