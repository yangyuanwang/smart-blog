<script setup lang="ts">
// 留言审核：通过 / 驳回 / 回复 / 删除
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface MessageRow {
  id: number
  nickname: string
  content: string
  reply?: string
  status: string
  createTime: string
}

const { data, pending, refresh } = await useAsyncData('admin-messages', () => api<PagePayload>('/admin/messages', { query: { page: 1, size: 20 } }))
interface PagePayload { list: MessageRow[], total: number }

const toast = ref('')
const replying = ref<{ id: number, text: string } | null>(null)

async function audit (id: number, next: string) {
  try {
    await api(`/admin/messages/${id}/audit`, { method: 'PUT', body: { status: next } })
    toast.value = next === '1' ? '已通过' : '已驳回'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function reply () {
  if (!replying.value) {
    return
  }
  try {
    await api(`/admin/messages/${replying.value.id}/reply`, { method: 'PUT', body: { reply: replying.value.text } })
    replying.value = null
    toast.value = '已回复并通过'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function remove (id: number) {
  if (!window.confirm('确定删除该留言（含回复）吗？')) {
    return
  }
  try {
    await api(`/admin/messages/${id}`, { method: 'DELETE' })
    toast.value = '已删除'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

const statusMeta: Record<string, { label: string, color: string }> = {
  '0': { label: '待审', color: 'var(--color-warn)' },
  '1': { label: '通过', color: 'var(--color-ok)' },
  '2': { label: '驳回', color: 'var(--color-danger)' }
}

useSeoMeta({ title: '留言审核' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">留言审核</h1>
    <p v-if="toast" class="mt-2 text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>
    <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>
    <div v-else-if="!data?.list?.length" class="mt-6 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted">
      没有留言
    </div>
    <ul v-else class="mt-4 space-y-3">
      <li v-for="message in data.list" :key="message.id" class="rounded-xl border border-app bg-surface p-4">
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="font-medium">{{ message.nickname }}</span>
          <span :style="{ color: statusMeta[message.status]?.color }">{{ statusMeta[message.status]?.label }}</span>
          <time class="ml-auto text-xs text-muted">{{ message.createTime?.slice(0, 16) }}</time>
        </div>
        <p class="mt-2 text-sm whitespace-pre-wrap">{{ message.content }}</p>
        <p v-if="message.reply" class="mt-2 rounded-lg bg-surface-2 px-3 py-2 text-sm">
          <span style="color: var(--color-accent)">博主回复：</span>{{ message.reply }}
        </p>
        <div v-if="replying?.id === message.id" class="mt-3 flex gap-2">
          <input v-model="replying.text" maxlength="1000" placeholder="回复内容" class="flex-1 rounded-lg border border-app bg-app px-3 py-2 text-sm">
          <button type="button" class="px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="reply">发送</button>
          <button type="button" class="px-3 py-2 rounded-lg text-sm text-muted" @click="replying = null">取消</button>
        </div>
        <div v-else class="mt-3 flex gap-2 text-xs">
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="replying = { id: message.id, text: '' }">回复</button>
          <button v-if="message.status !== '1'" type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="audit(message.id, '1')">通过</button>
          <button v-if="message.status !== '2'" type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="audit(message.id, '2')">驳回</button>
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="remove(message.id)">删除</button>
        </div>
      </li>
    </ul>
  </div>
</template>
