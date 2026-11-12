<script setup lang="ts">
// 留言板：楼中楼分页 + 发布（匿名/登录）+ 删除自己的留言
interface MessageItem {
  id: number
  nickname: string
  content: string
  reply?: string
  createTime: string
  uid?: number
  initial?: string
  avatarColor?: string
  canDelete?: boolean
  replies: MessageItem[]
}
interface PagePayload { list: MessageItem[], total: number, page: number, size: number }

const route = useRoute()
const router = useRouter()
const page = computed(() => Math.max(1, Number(route.query.page) || 1))

const { data, error, pending, refresh } = await useAsyncData(
  `messages-${page.value}`,
  () => api<PagePayload>('/messages', { query: { page: page.value } }),
  { watch: [page] }
)

const { data: session } = await useAsyncData('me-profile', () => api<{ uid: number, nickname: string, username: string } | null>('/me/profile').catch(() => null))

const nickname = ref('')
const content = ref('')
const parentId = ref<number | null>(null)
const submitting = ref(false)
const feedback = ref<{ ok: boolean, message: string } | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / (data.value?.size ?? 8))))

async function submit () {
  if (submitting.value || !content.value.trim()) {
    return
  }
  submitting.value = true
  feedback.value = null
  try {
    const result = await api<{ audited: boolean, message: string }>('/messages', {
      method: 'POST',
      body: {
        nickname: nickname.value.trim() || undefined,
        content: content.value.trim(),
        parentId: parentId.value ?? undefined
      }
    })
    feedback.value = { ok: true, message: result.message }
    content.value = ''
    parentId.value = null
    await refresh()
  } catch (error) {
    feedback.value = { ok: false, message: (error as Error).message || '提交失败' }
  } finally {
    submitting.value = false
  }
}

async function remove (id: number) {
  if (!window.confirm('确定删除这条留言吗？')) {
    return
  }
  try {
    await api(`/messages/${id}`, { method: 'DELETE' })
    await refresh()
  } catch (error) {
    feedback.value = { ok: false, message: (error as Error).message || '删除失败' }
  }
}

function startReply (message: MessageItem) {
  parentId.value = message.id
  content.value = `回复 @${message.nickname}：`
}

function goPage (target: number) {
  router.push({ path: '/messages', query: target > 1 ? { page: String(target) } : {} })
}

useSeoMeta({ title: '留言板', description: '给博主留言，楼中楼回复，先审后发' })
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="font-serif text-3xl font-bold">留言板</h1>
    <p class="mt-2 text-sm text-muted">在这里留言、提问或打个招呼（留言先审后发）</p>

    <!-- 发布表单 -->
    <form class="mt-6 rounded-xl border border-app bg-surface p-4" @submit.prevent="submit">
      <div v-if="parentId" class="text-xs text-muted mb-2">
        正在回复 #{{ parentId }}
        <button type="button" class="underline ml-2" @click="parentId = null">取消</button>
      </div>
      <div v-if="!session" class="flex flex-col sm:flex-row gap-2">
        <label for="msg-nickname" class="sr-only">昵称</label>
        <input
          id="msg-nickname"
          v-model="nickname"
          maxlength="32"
          placeholder="昵称（未登录）"
          class="sm:w-56 rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        >
      </div>
      <p v-else class="text-xs text-muted mt-1">以 {{ session.nickname || session.username }} 的身份留言</p>
      <label for="msg-content" class="sr-only">留言内容</label>
      <textarea
        id="msg-content"
        v-model="content"
        rows="3"
        maxlength="1000"
        required
        placeholder="说点什么（1-1000 字，不能包含链接）"
        class="mt-2 w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
      />
      <div class="mt-3 flex items-center justify-between gap-3">
        <p v-if="feedback" :role="feedback.ok ? 'status' : 'alert'" class="text-sm" :style="{ color: feedback.ok ? 'var(--color-ok)' : 'var(--color-danger)' }">
          {{ feedback.message }}
        </p>
        <span v-else />
        <button
          type="submit"
          :disabled="submitting || !content.trim()"
          class="px-4 py-2 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
          :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
        >
{{ submitting ? '提交中…' : '发布留言' }}
</button>
      </div>
    </form>

    <!-- 留言列表 -->
    <p v-if="pending" class="mt-8 text-muted" role="status">加载中…</p>
    <div v-else-if="error" class="mt-8 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted" role="alert">
      留言加载失败，请稍后重试
    </div>
    <div v-else-if="!data?.list?.length" class="mt-8 rounded-xl border border-app bg-surface px-4 py-12 text-center text-muted">
      还没有留言，写下第一条吧
    </div>
    <ul v-else class="mt-8 space-y-4">
      <li
        v-for="message in data.list"
        :key="message.id"
        class="rounded-xl border border-app bg-surface p-4"
      >
        <div class="flex items-center gap-3">
          <span
            class="w-9 h-9 rounded-full grid place-items-center text-white font-medium"
            :style="{ background: message.avatarColor || 'var(--color-accent-2)' }"
            aria-hidden="true"
          >{{ message.initial || message.nickname.slice(0, 1) }}</span>
          <div class="min-w-0">
            <p class="text-sm font-medium">{{ message.nickname }}</p>
            <time class="text-xs text-muted">{{ message.createTime?.slice(0, 16) }}</time>
          </div>
          <div class="ml-auto flex gap-2 text-xs">
            <button type="button" class="text-muted hover:text-main focus-visible:ring-2 ring-app rounded px-1" @click="startReply(message)">回复</button>
            <button
              v-if="message.canDelete"
              type="button"
              class="text-muted hover:text-main focus-visible:ring-2 ring-app rounded px-1"
              @click="remove(message.id)"
            >
删除
</button>
          </div>
        </div>
        <p class="mt-3 text-sm whitespace-pre-wrap break-words">{{ message.content }}</p>
        <p v-if="message.reply" class="mt-3 rounded-lg bg-surface-2 px-3 py-2 text-sm">
          <span class="font-medium" style="color: var(--color-accent)">博主回复：</span>{{ message.reply }}
        </p>
        <ul v-if="message.replies?.length" class="mt-3 space-y-2 pl-4 border-l border-app">
          <li v-for="reply in message.replies" :key="reply.id" class="text-sm">
            <span class="font-medium">{{ reply.nickname }}</span>
            <time class="ml-2 text-xs text-muted">{{ reply.createTime?.slice(0, 16) }}</time>
            <p class="mt-1 whitespace-pre-wrap break-words">{{ reply.content }}</p>
          </li>
        </ul>
      </li>
    </ul>

    <nav v-if="totalPages > 1" class="mt-8 flex items-center justify-center gap-2" aria-label="分页">
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm hover:bg-surface-2 disabled:opacity-40"
        :disabled="page <= 1"
        @click="goPage(page - 1)"
      >
上一页
</button>
      <span class="text-sm text-muted px-2">{{ page }} / {{ totalPages }}</span>
      <button
        type="button"
        class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm hover:bg-surface-2 disabled:opacity-40"
        :disabled="page >= totalPages"
        @click="goPage(page + 1)"
      >
下一页
</button>
    </nav>
  </div>
</template>
