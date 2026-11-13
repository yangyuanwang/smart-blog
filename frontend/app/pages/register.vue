<script setup lang="ts">
// 注册页：用户名 + 密码 + 昵称 + 邮箱（选填；填写且邮件服务可用时需验证码确认所有权）
definePageMeta({ layout: 'auth' })

const username = ref('')
const password = ref('')
const nickname = ref('')
const email = ref('')
const emailCode = ref('')
const mailEnabled = ref(false)
const sending = ref(false)
const countdown = ref(0)
const submitting = ref(false)
const error = ref('')
const success = ref(false)

let countdownTimer: ReturnType<typeof setInterval> | null = null

// 邮箱验证策略依赖邮件服务可用性
onMounted(async () => {
  try {
    const result = await $fetch<{ code: number, data: { enabled: boolean } }>('/api/v1/auth/mail-enabled')
    mailEnabled.value = result.code === 0 && result.data.enabled
  } catch {
    mailEnabled.value = false
  }
})

// 发码按钮 60 秒倒计时
function startCountdown () {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})

// 需要验证码：邮件服务可用且用户填了邮箱
const needsCode = computed(() => mailEnabled.value && email.value.trim().length > 3)

async function sendCode () {
  if (sending.value || countdown.value > 0) {
    return
  }
  sending.value = true
  error.value = ''
  try {
    await api('/auth/email/code', { method: 'POST', body: { email: email.value.trim() } })
    startCountdown()
    success.value = false
    error.value = ''
  } catch (e) {
    error.value = (e as Error).message || '发送失败'
  } finally {
    sending.value = false
  }
}

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
        email: email.value.trim() || undefined,
        emailCode: needsCode.value ? emailCode.value.trim() || undefined : undefined
      }
    })
    saveTokens(result.accessToken, result.refreshToken)
    await useAuthSession().refresh()
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
        <span class="mb-1 block">邮箱（选填{{ mailEnabled ? '，填写需验证码验证' : '，找回密码用' }}）</span>
        <input v-model="email" type="email" maxlength="128" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <div v-if="needsCode" class="flex gap-2">
        <label class="flex-1 block text-sm">
          <span class="sr-only">邮箱验证码</span>
          <input v-model="emailCode" required maxlength="6" placeholder="邮箱验证码" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <button
          type="button"
          :disabled="sending || countdown > 0"
          class="px-3 py-2 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 disabled:opacity-40 focus-visible:ring-2 ring-app"
          @click="sendCode"
        >{{ sending ? '发送中…' : countdown > 0 ? `${countdown}s` : '发送验证码' }}</button>
      </div>
      <p v-if="error" class="text-sm" style="color: var(--color-danger)" role="alert">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="w-full py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >
{{ submitting ? '注册中…' : '注册' }}
</button>
      <p v-if="success" class="text-sm" style="color: var(--color-ok)" role="status">注册成功，已自动登录</p>
    </form>
    <p class="mt-4 text-center text-sm text-muted">
      已有账号？<NuxtLink to="/login" class="hover:text-main focus-visible:ring-2 ring-app rounded">去登录</NuxtLink>
    </p>
  </div>
</template>
