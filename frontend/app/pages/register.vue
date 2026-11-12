<script setup lang="ts">
// 注册页：用户名 + 密码 + 昵称 + 邮箱（邮箱用于找回密码）
definePageMeta({ layout: 'auth' })

const username = ref('')
const password = ref('')
const nickname = ref('')
const email = ref('')
const submitting = ref(false)
const error = ref('')
const success = ref(false)

async function submit () {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const result = await api<{ accessToken: string, refreshToken: string }>('/auth/register', {
      method: 'POST',
      body: {
        username: username.value.trim(),
        password: password.value,
        nickname: nickname.value.trim() || undefined,
        email: email.value.trim() || undefined
      }
    })
    saveTokens(result.accessToken, result.refreshToken)
    success.value = true
    await navigateTo('/')
  } catch (e) {
    error.value = (e as Error).message || '注册失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rounded-2xl border border-app bg-surface p-6">
    <h1 class="font-serif text-xl font-bold text-center">注册</h1>
    <form class="mt-5 space-y-4" @submit.prevent="submit">
      <label class="block text-sm">
        <span class="mb-1 block">用户名（2-32 字符）</span>
        <input v-model="username" required minlength="2" maxlength="32" autocomplete="username" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">密码（6-32 字符）</span>
        <input v-model="password" type="password" required minlength="6" maxlength="32" autocomplete="new-password" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">昵称（选填）</span>
        <input v-model="nickname" maxlength="32" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">邮箱（选填，找回密码用）</span>
        <input v-model="email" type="email" maxlength="128" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <p v-if="error" class="text-sm" style="color: var(--color-danger)" role="alert">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="w-full py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >{{ submitting ? '注册中…' : '注册' }}</button>
      <p v-if="success" class="text-sm" style="color: var(--color-ok)" role="status">注册成功，已自动登录</p>
    </form>
    <p class="mt-4 text-center text-sm text-muted">
      已有账号？<NuxtLink to="/login" class="hover:text-main focus-visible:ring-2 ring-app rounded">去登录</NuxtLink>
    </p>
  </div>
</template>
