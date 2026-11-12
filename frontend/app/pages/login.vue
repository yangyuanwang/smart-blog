<script setup lang="ts">
// 登录页：JWT 登录并写回令牌（验证码开关关闭时不展示验证码输入）
definePageMeta({ layout: 'auth' })

const email = ref('')
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
    const result = await api<{ accessToken: string, refreshToken: string, user: { uid: number } }>('/auth/login', {
      method: 'POST',
      body: { username: email.value.trim(), password: password.value }
    })
    saveTokens(result.accessToken, result.refreshToken)
    await navigateTo('/')
  } catch (e) {
    error.value = (e as Error).message || '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rounded-2xl border border-app bg-surface p-6">
    <h1 class="font-serif text-xl font-bold text-center">登录</h1>
    <form class="mt-5 space-y-4" @submit.prevent="submit">
      <label class="block text-sm">
        <span class="mb-1 block">用户名</span>
        <input
          v-model="email"
          required
          autocomplete="username"
          class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        >
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">密码</span>
        <input
          v-model="password"
          type="password"
          required
          autocomplete="current-password"
          class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        >
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
    <div class="mt-4 flex justify-between text-sm text-muted">
      <NuxtLink to="/reset" class="hover:text-main focus-visible:ring-2 ring-app rounded">忘记密码？</NuxtLink>
      <NuxtLink to="/register" class="hover:text-main focus-visible:ring-2 ring-app rounded">注册账号</NuxtLink>
    </div>
  </div>
</template>
