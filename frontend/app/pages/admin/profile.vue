<script setup lang="ts">
// 个人中心：资料 + 头像上传 + 改密（改密后自动换发令牌）
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

const profile = ref({
  uid: 0,
  username: '',
  nickname: '',
  email: '',
  phone: '',
  img: '',
  role: '1',
  admin: false
})
const nickname = ref('')
const email = ref('')
const phone = ref('')
const img = ref('')
const uploading = ref(false)

const oldPassword = ref('')
const newPassword = ref('')
const profileToast = useToast()
const passwordToast = useToast()
const passwordError = ref('')

onMounted(async () => {
  const data = await api<typeof profile.value>('/me/profile')
  profile.value = data
  nickname.value = data.nickname
  email.value = data.email
  phone.value = data.phone
  img.value = data.img
})

async function uploadAvatar (event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploading.value = true
  try {
    const body = new FormData()
    body.append('file', file)
    const result = await api<{ url: string }>('/admin/upload', { method: 'POST', body })
    img.value = result.url
  } catch (error) {
    profileToast.error((error as Error).message)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function saveProfile () {
  try {
    await api('/me/profile', {
      method: 'PUT',
      body: { nickname: nickname.value, email: email.value, phone: phone.value, img: img.value || undefined }
    })
    profileToast.success('资料已更新')
  } catch (error) {
    profileToast.error((error as Error).message)
  }
}

async function changePassword () {
  passwordError.value = ''
  try {
    const result = await api<{ accessToken: string, refreshToken: string }>('/me/password', {
      method: 'PUT',
      body: { oldPassword: oldPassword.value, newPassword: newPassword.value }
    })
    saveTokens(result.accessToken, result.refreshToken)
    passwordToast.success('密码已修改，会话已更新')
    oldPassword.value = ''
    newPassword.value = ''
  } catch (error) {
    passwordError.value = (error as Error).message
  }
}

useSeoMeta({ title: '个人中心' })
</script>

<template>
  <div class="max-w-2xl space-y-6">
    <section class="rounded-xl border border-app bg-surface p-5">
      <h1 class="font-serif text-2xl font-bold">个人中心</h1>
      <div class="mt-4 flex items-center gap-4">
        <img v-if="img" :src="img" alt="头像" class="w-16 h-16 rounded-full object-cover border border-app">
        <div v-else class="w-16 h-16 rounded-full grid place-items-center bg-surface-2 text-2xl" aria-hidden="true">
          {{ (profile.nickname || profile.username || '?').slice(0, 1) }}
        </div>
        <div class="text-sm">
          <p class="font-medium">{{ profile.username }}（{{ profile.nickname || '无昵称' }}）</p>
          <p class="text-xs text-muted">{{ profile.admin ? '管理员' : '普通用户' }} · uid {{ profile.uid }}</p>
        </div>
        <label class="ml-auto px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2 cursor-pointer">
          {{ uploading ? '上传中…' : '更换头像' }}
          <input type="file" accept="image/*" class="sr-only" @change="uploadAvatar">
        </label>
      </div>
      <form class="mt-5 space-y-3" @submit.prevent="saveProfile">
        <label class="block text-sm">
          <span class="mb-1 block">昵称</span>
          <input v-model="nickname" maxlength="32" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block">邮箱（找回密码用）</span>
          <input v-model="email" type="email" maxlength="128" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block">手机号（选填）</span>
          <input v-model="phone" maxlength="11" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <button type="submit" class="px-4 py-2 rounded-lg text-sm font-medium text-white" style="background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2))">保存资料</button>
      </form>
    </section>

    <section class="rounded-xl border border-app bg-surface p-5">
      <h2 class="font-serif font-semibold">修改密码</h2>
      <form class="mt-3 space-y-3" @submit.prevent="changePassword">
        <label class="block text-sm">
          <span class="mb-1 block">原密码</span>
          <input v-model="oldPassword" type="password" required autocomplete="current-password" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <label class="block text-sm">
          <span class="mb-1 block">新密码（至少 6 位）</span>
          <input v-model="newPassword" type="password" required minlength="6" maxlength="32" autocomplete="new-password" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <p v-if="passwordError" class="text-sm" style="color: var(--color-danger)" role="alert">{{ passwordError }}</p>
        <button type="submit" class="px-4 py-2 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2">修改密码</button>
      </form>
    </section>
  </div>
</template>
