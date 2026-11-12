<script setup lang="ts">
// 友链管理 + 友链申请审核
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface LinkItem {
  id: number
  siteName: string
  siteUrl: string
  siteDesc: string
  sort?: number
}
interface ApplyRow {
  id: number
  siteName: string
  siteUrl: string
  siteDesc: string
  contact?: string
  status: string
  remark?: string
  createTime: string
}

const { data: links, refresh: refreshLinks } = await useAsyncData('admin-links', () => api<LinkItem[]>('/admin/links'))
const { data: applies, refresh: refreshApplies } = await useAsyncData('admin-link-applies', () => api<PagePayload>('/admin/link-applies', { query: { page: 1, size: 20 } }))
interface PagePayload { list: ApplyRow[], total: number }

const form = ref({ id: null as number | null, siteName: '', siteUrl: '', siteDesc: '', sort: 0 })
const rejecting = ref<{ id: number, remark: string } | null>(null)
const toast = ref('')

async function save () {
  try {
    await api('/admin/links', { method: 'POST', body: { ...form.value } })
    toast.value = form.value.id ? '友链已更新' : '友链已添加'
    form.value = { id: null, siteName: '', siteUrl: '', siteDesc: '', sort: 0 }
    await refreshLinks()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

function edit (link: LinkItem) {
  form.value = { id: link.id, siteName: link.siteName, siteUrl: link.siteUrl, siteDesc: link.siteDesc, sort: link.sort ?? 0 }
}

async function removeLink (id: number) {
  if (!window.confirm('确定删除该友链吗？')) {
    return
  }
  try {
    await api(`/admin/links/${id}`, { method: 'DELETE' })
    toast.value = '友链已删除'
    await refreshLinks()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function approve (id: number) {
  try {
    await api(`/admin/link-applies/${id}/approve`, { method: 'PUT' })
    toast.value = '申请已通过并加入友链'
    await refreshApplies()
    await refreshLinks()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function reject () {
  if (!rejecting.value) {
    return
  }
  try {
    await api(`/admin/link-applies/${rejecting.value.id}/reject`, { method: 'PUT', body: { remark: rejecting.value.remark } })
    rejecting.value = null
    toast.value = '申请已拒绝'
    await refreshApplies()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

const statusMeta: Record<string, { label: string, color: string }> = {
  '0': { label: '待审', color: 'var(--color-warn)' },
  '1': { label: '通过', color: 'var(--color-ok)' },
  '2': { label: '拒绝', color: 'var(--color-danger)' }
}

useSeoMeta({ title: '友链管理' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">友链与申请</h1>
    <p v-if="toast" class="mt-2 text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>

    <!-- 待审申请 -->
    <section class="mt-6" aria-labelledby="apply-heading">
      <h2 id="apply-heading" class="font-serif font-semibold">待审申请</h2>
      <div v-if="!applies?.list?.length" class="mt-3 text-sm text-muted">没有申请记录</div>
      <ul v-else class="mt-3 space-y-3">
        <li v-for="apply in applies.list" :key="apply.id" class="rounded-xl border border-app bg-surface p-4">
          <div class="flex flex-wrap items-center gap-2 text-sm">
            <span class="font-medium">{{ apply.siteName }}</span>
            <span :style="{ color: statusMeta[apply.status]?.color }">{{ statusMeta[apply.status]?.label }}</span>
            <time class="ml-auto text-xs text-muted">{{ apply.createTime?.slice(0, 16) }}</time>
          </div>
          <p class="mt-1 text-xs text-muted">{{ apply.siteUrl }} · {{ apply.siteDesc }}</p>
          <p v-if="apply.remark" class="mt-1 text-xs text-muted">备注：{{ apply.remark }}</p>
          <div v-if="apply.status === '0'" class="mt-3 flex gap-2 text-xs">
            <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="approve(apply.id)">通过</button>
            <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="rejecting = { id: apply.id, remark: '' }">拒绝</button>
          </div>
          <div v-else-if="rejecting?.id === apply.id" class="mt-3 flex gap-2">
            <input v-model="rejecting.remark" placeholder="拒绝理由（选填）" class="flex-1 rounded-lg border border-app bg-app px-3 py-2 text-sm">
            <button type="button" class="px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="reject">确认</button>
          </div>
        </li>
      </ul>
    </section>

    <!-- 友链表单 -->
    <section class="mt-8 rounded-xl border border-app bg-surface p-4" aria-labelledby="link-form-heading">
      <h2 id="link-form-heading" class="font-serif font-semibold">{{ form.id ? '编辑友链' : '添加友链' }}</h2>
      <form class="mt-3 grid gap-3 sm:grid-cols-2" @submit.prevent="save">
        <input v-model="form.siteName" required maxlength="30" placeholder="站点名" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <input v-model="form.siteUrl" required maxlength="200" placeholder="https://" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <input v-model="form.siteDesc" required maxlength="100" placeholder="站点描述" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <input v-model.number="form.sort" type="number" placeholder="排序" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <div class="flex gap-2 sm:col-span-2">
          <button type="submit" class="px-4 py-2 rounded-lg text-sm font-medium text-white" style="background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2))">
            {{ form.id ? '更新' : '添加' }}
          </button>
          <button v-if="form.id" type="button" class="px-4 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="form = { id: null, siteName: '', siteUrl: '', siteDesc: '', sort: 0 }">取消编辑</button>
        </div>
      </form>
    </section>

    <!-- 友链列表 -->
    <section class="mt-8" aria-labelledby="link-list-heading">
      <h2 id="link-list-heading" class="font-serif font-semibold">现有友链（{{ links?.length ?? 0 }}）</h2>
      <ul class="mt-3 space-y-2">
        <li v-for="link in links ?? []" :key="link.id" class="flex items-center gap-3 rounded-lg border border-app bg-surface px-4 py-3 text-sm">
          <span class="font-medium">{{ link.siteName }}</span>
          <span class="text-xs text-muted line-clamp-1">{{ link.siteUrl }}</span>
          <span class="ml-auto flex gap-2 text-xs">
            <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" @click="edit(link)">编辑</button>
            <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="removeLink(link.id)">删除</button>
          </span>
        </li>
      </ul>
    </section>
  </div>
</template>
