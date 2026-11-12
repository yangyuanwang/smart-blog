<script setup lang="ts">
// 日记管理：列表 + 新增/编辑/删除
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface Diary {
  id?: number
  title: string
  content: string
  mood?: string
  createTime?: string
}

const moods = ['随记', '开工', '想走', '读完', '高兴', '发呆']

const { data, refresh } = await useAsyncData('admin-diaries', () => api<Diary[]>('/admin/diaries'))

const form = ref<Diary>({ title: '', content: '', mood: '随记' })
const toast = ref('')

function edit (diary: Diary) {
  form.value = { ...diary }
}

function reset () {
  form.value = { title: '', content: '', mood: '随记' }
}

async function save () {
  try {
    await api('/admin/diaries', { method: 'POST', body: { ...form.value } })
    toast.value = form.value.id ? '日记已更新' : '日记已添加'
    reset()
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function remove (diary: Diary) {
  if (!window.confirm(`确定删除日记「${diary.title}」吗？`)) {
    return
  }
  try {
    await api(`/admin/diaries/${diary.id}`, { method: 'DELETE' })
    toast.value = '日记已删除'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

useSeoMeta({ title: '日记管理' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">日记管理</h1>
    <p v-if="toast" class="mt-2 text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>

    <form class="mt-6 rounded-xl border border-app bg-surface p-4 grid gap-3" @submit.prevent="save">
      <div class="flex flex-wrap gap-3">
        <input v-model="form.title" required maxlength="100" placeholder="标题" class="flex-1 rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <select v-model="form.mood" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
          <option v-for="mood in moods" :key="mood" :value="mood">{{ mood }}</option>
        </select>
      </div>
      <textarea v-model="form.content" required rows="3" maxlength="2000" placeholder="内容" class="rounded-lg border border-app bg-app px-3 py-2 text-sm" />
      <div class="flex gap-2">
        <button type="submit" class="px-4 py-2 rounded-lg text-sm font-medium text-white" style="background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2))">
          {{ form.id ? '更新' : '添加' }}
        </button>
        <button v-if="form.id" type="button" class="px-4 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="reset">取消编辑</button>
      </div>
    </form>

    <ul class="mt-6 space-y-3">
      <li v-for="diary in data ?? []" :key="diary.id" class="rounded-xl border border-app bg-surface p-4">
        <div class="flex items-center gap-2 text-sm">
          <span class="font-medium">{{ diary.title }}</span>
          <span class="text-xs px-2 py-0.5 rounded-full border border-app text-muted">{{ diary.mood }}</span>
          <time class="ml-auto text-xs text-muted">{{ diary.createTime?.slice(0, 16) }}</time>
        </div>
        <p class="mt-2 text-sm text-muted whitespace-pre-wrap">{{ diary.content }}</p>
        <div class="mt-3 flex gap-2 text-xs">
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" @click="edit(diary)">编辑</button>
          <button type="button" class="px-2.5 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="remove(diary)">删除</button>
        </div>
      </li>
    </ul>
  </div>
</template>
