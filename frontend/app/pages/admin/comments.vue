<script setup lang="ts">
// 评论审核：通过 / 隐藏 / 删除
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface CommentRow {
  id: number
  nickname: string
  content: string
  articleTitle?: string
  status: string
  createTime: string
}

const status = ref('')
const { data, pending, refresh } = await useAsyncData(
  'admin-comments',
  () => api<PagePayload>('/admin/comments', { query: { page: 1, size: 20, ...(status.value ? { status: status.value } : {}) } }),
  { watch: [status] }
)
interface PagePayload { list: CommentRow[], total: number }

const toast = ref('')

async function audit (id: number, next: string) {
  try {
    await api(`/admin/comments/${id}/audit`, { method: 'PUT', body: { status: next } })
    toast.value = next === '1' ? '已通过' : '已隐藏'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function remove (id: number) {
  if (!window.confirm('确定删除该评论吗？')) {
    return
  }
  try {
    await api(`/admin/comments/${id}`, { method: 'DELETE' })
    toast.value = '已删除'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

const statusMeta: Record<string, { label: string, color: string }> = {
  '0': { label: '待审', color: 'var(--color-warn)' },
  '1': { label: '通过', color: 'var(--color-ok)' }
}

useSeoMeta({ title: '评论审核' })
</script>

<template>
  <div>
    <div class="flex items-center justify-between">
      <h1 class="font-serif text-2xl font-bold">评论审核</h1>
      <select v-model="status" class="rounded-lg border border-app bg-surface px-3 py-2 text-sm">
        <option value="">全部</option>
        <option value="0">待审</option>
        <option value="1">已通过</option>
      </select>
    </div>
    <p v-if="toast" class="mt-2 text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>
    <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>
    <div v-else-if="!data?.list?.length" class="mt-6 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted">
      没有评论
    </div>
    <ul v-else class="mt-4 space-y-3">
      <li v-for="comment in data.list" :key="comment.id" class="rounded-xl border border-app bg-surface p-4">
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="font-medium">{{ comment.nickname }}</span>
          <span :style="{ color: statusMeta[comment.status]?.color }">{{ statusMeta[comment.status]?.label }}</span>
          <span class="text-xs text-muted">on「{{ comment.articleTitle }}」</span>
          <time class="ml-auto text-xs text-muted">{{ comment.createTime?.slice(0, 16) }}</time>
        </div>
        <p class="mt-2 text-sm whitespace-pre-wrap">{{ comment.content }}</p>
        <div class="mt-3 flex gap-2 text-xs">
          <button v-if="comment.status !== '1'" type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="audit(comment.id, '1')">通过</button>
          <button v-if="comment.status === '1'" type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="audit(comment.id, '0')">隐藏</button>
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="remove(comment.id)">删除</button>
        </div>
      </li>
    </ul>
  </div>
</template>
