<script setup lang="ts">
// 文章评论列表 + 发表（匿名昵称或登录身份，先审后发开关生效）
interface CommentItem {
  id: number
  nickname: string
  content: string
  createTime: string
}

const props = defineProps<{ aid: number, allowComment: boolean }>()

const emit = defineEmits<{ (e: 'published', message: string): void }>()

const { data: session } = await useAsyncData('me-profile', () => api<MeProfile | null>('/me/profile').catch(() => null))

interface MeProfile {
  uid: number
  nickname: string
  username: string
}

const { data: comments, refresh } = await useAsyncData(`comments-${props.aid}`, () => api<CommentItem[]>(`/comments?aid=${props.aid}`))

const nickname = ref('')
const content = ref('')
const submitting = ref(false)
const feedback = ref<{ ok: boolean, message: string } | null>(null)

async function submit () {
  if (submitting.value) {
    return
  }
  submitting.value = true
  feedback.value = null
  try {
    const result = await api<{ audited: boolean, message: string }>('/comments', {
      method: 'POST',
      query: { aid: props.aid },
      body: {
        nickname: nickname.value.trim() || undefined,
        content: content.value.trim()
      }
    })
    emit('published', result.message)
    feedback.value = { ok: true, message: result.message }
    content.value = ''
    await refresh()
  } catch (error) {
    feedback.value = { ok: false, message: (error as Error).message || '提交失败' }
  } finally {
    submitting.value = false
  }
}

function fmtTime (value: string) {
  return value?.slice(0, 16) ?? ''
}
</script>

<template>
  <section class="mt-10" aria-labelledby="comments-heading">
    <h2 id="comments-heading" class="font-serif text-xl font-bold">
      评论 <span class="text-muted text-base font-normal">({{ comments?.length ?? 0 }})</span>
    </h2>

    <ul v-if="comments?.length" class="mt-4 space-y-3">
      <li
        v-for="comment in comments"
        :key="comment.id"
        class="rounded-xl border border-app bg-surface px-4 py-3"
      >
        <div class="flex items-center justify-between text-sm">
          <span class="font-medium">{{ comment.nickname }}</span>
          <time class="text-xs text-muted">{{ fmtTime(comment.createTime) }}</time>
        </div>
        <p class="mt-2 text-sm whitespace-pre-wrap break-words">{{ comment.content }}</p>
      </li>
    </ul>
    <p v-else class="mt-4 text-sm text-muted">还没有评论，来抢沙发</p>

    <form v-if="allowComment" class="mt-6 rounded-xl border border-app bg-surface p-4" @submit.prevent="submit">
      <h3 class="text-sm font-medium">发表评论</h3>
      <div v-if="!session" class="mt-2">
        <label for="comment-nickname" class="sr-only">昵称</label>
        <input
          id="comment-nickname"
          v-model="nickname"
          maxlength="32"
          placeholder="昵称（未登录）"
          class="w-full sm:w-56 rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        >
      </div>
      <p v-else class="mt-2 text-xs text-muted">以 {{ session.nickname || session.username }} 的身份评论</p>
      <label for="comment-content" class="sr-only">评论内容</label>
      <textarea
        id="comment-content"
        v-model="content"
        rows="3"
        maxlength="1000"
        required
        placeholder="说点什么（1-1000 字）"
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
{{ submitting ? '提交中…' : '提交' }}
</button>
      </div>
    </form>
    <p v-else class="mt-6 text-sm text-muted">评论已关闭</p>
  </section>
</template>
