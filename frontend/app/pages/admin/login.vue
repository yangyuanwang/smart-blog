<script setup lang="ts">
// 后台登录：admin-login 端点（额外校验管理员角色）
definePageMeta({ layout: 'auth' })

const route = useRoute()
const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref('')

async function submit () {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const result = await api<{ accessToken: string, refreshToken: string, user: { admin: boolean } }>('/auth/admin-login', {
      method: 'POST',
      body: { username: username.value.trim(), password: password.value }
    })
    saveTokens(result.accessToken, result.refreshToken)
    const next = String(route.query.next ?? '/admin')
    await navigateTo(next.startsWith('/admin') ? next : '/admin')
  } catch (e) {
    error.value = (e as Error).message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rounded-2xl border border-app bg-surface p-6">
    <h1 class="font-serif text-xl font-bold text-center">后台登录</h1>
    <p class="mt-1 text-xs text-muted text-center">仅管理员账号可进入</p>
    <form class="mt-5 space-y-4" @submit.prevent="submit">
      <label class="block text-sm">
        <span class="mb-1 block">用户名</span>
        <input v-model="username" required autocomplete="username" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">密码</span>
        <input v-model="password" type="password" required autocomplete="current-password" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <p v-if="error" class="text-sm" style="color: var(--color-danger)" role="alert">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="w-full py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >
{{ submitting ? '登录中…' : '登录' }}
</button>
    </form>
  </div>
</template>
