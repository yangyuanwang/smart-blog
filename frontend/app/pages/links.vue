<script setup lang="ts">
// 友链列表 + 申请入口（提交后进待审池）
interface LinkItem {
  id: number
  siteName: string
  siteUrl: string
  siteDesc: string
}

const { data, error, pending } = await useAsyncData('links', () => api<LinkItem[]>('/links'))

const showForm = ref(false)
const form = ref({ siteName: '', siteUrl: '', siteDesc: '', contact: '' })
const submitting = ref(false)
const feedback = ref<{ ok: boolean, message: string } | null>(null)

async function submit () {
  if (submitting.value) {
    return
  }
  submitting.value = true
  feedback.value = null
  try {
    await api('/link-applies', { method: 'POST', body: { ...form.value } })
    feedback.value = { ok: true, message: '申请已提交，等待管理员审核' }
    showForm.value = false
  } catch (error) {
    feedback.value = { ok: false, message: (error as Error).message || '提交失败' }
  } finally {
    submitting.value = false
  }
}

useSeoMeta({ title: '友链', description: '交换友链，一起在互联网上留下足迹' })
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div class="flex items-center justify-between">
      <h1 class="font-serif text-3xl font-bold">友链</h1>
      <button
        type="button"
        class="px-4 py-2 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
        :aria-expanded="showForm"
        @click="showForm = !showForm"
      >
{{ showForm ? '收起' : '申请友链' }}
</button>
    </div>

    <!-- 申请表单 -->
    <form v-if="showForm" class="mt-6 rounded-xl border border-app bg-surface p-4 grid gap-3" @submit.prevent="submit">
      <div class="grid gap-3 sm:grid-cols-2">
        <label class="text-sm">
          <span class="mb-1 block">站点名 <span style="color: var(--color-danger)">*</span></span>
          <input v-model="form.siteName" required maxlength="30" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
        <label class="text-sm">
          <span class="mb-1 block">站点地址 <span style="color: var(--color-danger)">*</span></span>
          <input v-model="form.siteUrl" required maxlength="120" placeholder="https://" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
      </div>
      <label class="text-sm">
        <span class="mb-1 block">站点描述 <span style="color: var(--color-danger)">*</span></span>
        <input v-model="form.siteDesc" required maxlength="100" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <label class="text-sm">
        <span class="mb-1 block">联系方式（选填）</span>
        <input v-model="form.contact" maxlength="100" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>
      <div class="flex items-center justify-between gap-3">
        <p v-if="feedback" :role="feedback.ok ? 'status' : 'alert'" class="text-sm" :style="{ color: feedback.ok ? 'var(--color-ok)' : 'var(--color-danger)' }">
          {{ feedback.message }}
        </p>
        <span v-else />
        <button
          type="submit"
          :disabled="submitting"
          class="px-4 py-2 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
          :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
        >
{{ submitting ? '提交中…' : '提交申请' }}
</button>
      </div>
    </form>

    <!-- 友链卡片 -->
    <p v-if="pending" class="mt-8 text-muted" role="status">加载中…</p>
    <div v-else-if="error" class="mt-8 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted" role="alert">
      友链加载失败，请稍后重试
    </div>
    <div v-else-if="!data?.length" class="mt-8 rounded-xl border border-app bg-surface px-4 py-12 text-center text-muted">
      还没有友链，点「申请友链」加入吧
    </div>
    <ul v-else class="mt-8 grid gap-4 sm:grid-cols-2">
      <li v-for="link in data" :key="link.id">
        <a
          :href="link.siteUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="block rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 hover:-translate-y-0.5 transition-all focus-visible:ring-2 ring-app"
        >
          <p class="font-semibold">{{ link.siteName }}</p>
          <p class="mt-1 text-sm text-muted line-clamp-1">{{ link.siteDesc }}</p>
          <p class="mt-2 text-xs text-muted line-clamp-1">{{ link.siteUrl }}</p>
        </a>
      </li>
    </ul>
  </div>
</template>
