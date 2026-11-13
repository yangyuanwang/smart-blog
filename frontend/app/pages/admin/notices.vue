<script setup lang="ts">
// 公告管理：新增/置顶/删除
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface Notice {
  id?: number
  title: string
  content: string
  isTop?: string
  createTime?: string
}

const { data, refresh } = await useAsyncData('admin-notices', () => api<Notice[]>('/admin/notices'))

const form = ref<Notice>({ title: '', content: '', isTop: '0' })
const toast = useToast()

async function save () {
  try {
    await api('/admin/notices', { method: 'POST', body: { ...form.value } })
    toast.success(form.value.id ? '公告已更新' : '公告已发布')
    form.value = { title: '', content: '', isTop: '0' }
    await refresh()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

function edit (notice: Notice) {
  form.value = { ...notice }
}

async function remove (notice: Notice) {
  if (!window.confirm(`确定删除公告「${notice.title}」吗？`)) {
    return
  }
  try {
    await api(`/admin/notices/${notice.id}`, { method: 'DELETE' })
    toast.success('公告已删除')
    await refresh()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

useSeoMeta({ title: '公告管理' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">公告管理</h1>

    <form class="mt-6 rounded-xl border border-app bg-surface p-4 grid gap-3" @submit.prevent="save">
      <div class="flex flex-wrap gap-3">
        <input v-model="form.title" required maxlength="100" placeholder="公告标题" class="flex-1 rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <label class="flex items-center gap-2 text-sm">
          <input v-model="form.isTop" type="checkbox" true-value="1" false-value="0" class="rounded"> 置顶
        </label>
      </div>
      <textarea v-model="form.content" required rows="2" maxlength="2000" placeholder="公告内容" class="rounded-lg border border-app bg-app px-3 py-2 text-sm" />
      <div class="flex gap-2">
        <button type="submit" class="px-4 py-2 rounded-lg text-sm font-medium text-white" style="background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2))">
          {{ form.id ? '更新' : '发布' }}
        </button>
        <button v-if="form.id" type="button" class="px-4 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="form = { title: '', content: '', isTop: '0' }">取消编辑</button>
      </div>
    </form>

    <ul class="mt-6 space-y-3">
      <li v-for="notice in data ?? []" :key="notice.id" class="rounded-xl border border-app bg-surface p-4">
        <div class="flex items-center gap-2 text-sm">
          <span class="font-medium">{{ notice.title }}</span>
          <span v-if="notice.isTop === '1'" class="text-xs" style="color: var(--color-warn)">置顶</span>
          <time class="ml-auto text-xs text-muted">{{ notice.createTime?.slice(0, 16) }}</time>
        </div>
        <p class="mt-2 text-sm text-muted whitespace-pre-wrap">{{ notice.content }}</p>
        <div class="mt-3 flex gap-2 text-xs">
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="edit(notice)">编辑</button>
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="remove(notice)">删除</button>
        </div>
      </li>
    </ul>
  </div>
</template>
