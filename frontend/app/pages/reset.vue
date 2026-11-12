<script setup lang="ts">
// 找回密码：两步流 —— 请求邮箱验证码 -> 验证码 + 新密码重置
definePageMeta({ layout: 'auth' })

const step = ref<'request' | 'confirm'>('request')
const username = ref('')
const email = ref('')
const code = ref('')
const newPassword = ref('')
const submitting = ref(false)
const error = ref('')
const notice = ref('')

async function requestCode () {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await api('/auth/reset/request', { method: 'POST', body: { username: username.value.trim() } })
    // 后端不区分账号是否存在（防枚举），统一提示检查邮箱
    notice.value = '如果该账号绑定了邮箱，验证码已发送，请查收'
    step.value = 'confirm'
  } catch (e) {
    error.value = (e as Error).message || '请求失败'
  } finally {
    submitting.value = false
  }
}

async function confirmReset () {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await api('/auth/reset/confirm', {
      method: 'POST',
      body: {
        username: username.value.trim(),
        email: email.value.trim(),
        code: code.value.trim(),
        newPassword: newPassword.value
      }
    })
    notice.value = '密码已重置，请使用新密码登录'
    await navigateTo('/login')
  } catch (e) {
    error.value = (e as Error).message || '重置失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rounded-2xl border border-app bg-surface p-6">
    <h1 class="font-serif text-xl font-bold text-center">找回密码</h1>
    <p v-if="notice" class="mt-3 text-sm text-center" style="color: var(--color-ok)" role="status">{{ notice }}</p>

    <form v-if="step === 'request'" class="mt-5 space-y-4" @submit.prevent="requestCode">
      <label class="block text-sm">
        <span class="mb-1 block">用户名</span>
        <input v-model="username" required class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <p v-if="error" class="text-sm" style="color: var(--color-danger)" role="alert">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="w-full py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >
{{ submitting ? '发送中…' : '发送验证码' }}
</button>
    </form>

    <form v-else class="mt-5 space-y-4" @submit.prevent="confirmReset">
      <label class="block text-sm">
        <span class="mb-1 block">账号绑定邮箱</span>
        <input v-model="email" type="email" required class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">验证码</span>
        <input v-model="code" required maxlength="6" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="block text-sm">
        <span class="mb-1 block">新密码（至少 6 位）</span>
        <input v-model="newPassword" type="password" required minlength="6" maxlength="32" autocomplete="new-password" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <p v-if="error" class="text-sm" style="color: var(--color-danger)" role="alert">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="w-full py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >
{{ submitting ? '提交中…' : '重置密码' }}
</button>
    </form>

    <p class="mt-4 text-center text-sm text-muted">
      未绑定邮箱？请联系管理员重置 · <NuxtLink to="/login" class="hover:text-main">返回登录</NuxtLink>
    </p>
  </div>
</template>
