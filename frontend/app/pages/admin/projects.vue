<script setup lang="ts">
// 作品集管理：卡片式 CRUD（状态/精选/排序/技术栈/封面）
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface Project {
  id?: number
  name: string
  summary: string
  description?: string
  techStack?: string
  cover?: string
  repoUrl?: string
  demoUrl?: string
  featured?: string
  sort?: number
  status?: string
}

const { data, refresh } = await useAsyncData('admin-projects', () => api<Project[]>('/admin/projects'))

const form = ref<Project>({ name: '', summary: '', techStack: '', repoUrl: '', demoUrl: '', featured: '0', status: '1', sort: 0, cover: '' })
const uploading = ref(false)
const toast = ref('')

function edit (project: Project) {
  form.value = { ...project, featured: project.featured ?? '0', status: project.status ?? '1', sort: project.sort ?? 0 }
}

function reset () {
  form.value = { name: '', summary: '', techStack: '', repoUrl: '', demoUrl: '', featured: '0', status: '1', sort: 0, cover: '' }
}

async function uploadCover (event: Event) {
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
    form.value.cover = result.url
  } catch (error) {
    toast.value = (error as Error).message
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function save () {
  try {
    await api('/admin/projects', { method: 'POST', body: { ...form.value } })
    toast.value = form.value.id ? '作品已更新' : '作品已添加'
    reset()
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function remove (project: Project) {
  if (!window.confirm(`确定删除作品「${project.name}」吗？`)) {
    return
  }
  try {
    await api(`/admin/projects/${project.id}`, { method: 'DELETE' })
    toast.value = '作品已删除'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

useSeoMeta({ title: '作品集管理' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">作品集管理</h1>
    <p v-if="toast" class="mt-2 text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>

    <form class="mt-6 rounded-xl border border-app bg-surface p-4 grid gap-3" @submit.prevent="save">
      <div class="grid gap-3 sm:grid-cols-2">
        <input v-model="form.name" required maxlength="64" placeholder="项目名" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <input v-model="form.summary" required maxlength="200" placeholder="一句话简介" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <input v-model="form.techStack" maxlength="255" placeholder="技术栈（逗号分隔）" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <div class="flex items-center gap-2">
          <img v-if="form.cover" :src="form.cover" alt="封面" class="w-16 h-10 object-cover rounded border border-app">
          <label class="px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2 cursor-pointer">
            {{ uploading ? '上传中…' : '上传封面' }}
            <input type="file" accept="image/*" class="sr-only" @change="uploadCover">
          </label>
          <button v-if="form.cover" type="button" class="text-xs text-muted" @click="form.cover = ''">移除</button>
        </div>
        <input v-model="form.repoUrl" maxlength="255" placeholder="仓库地址 https://" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <input v-model="form.demoUrl" maxlength="255" placeholder="演示地址 https://" class="rounded-lg border border-app bg-app px-3 py-2 text-sm">
      </div>
      <textarea v-model="form.description" rows="3" placeholder="详细介绍（Markdown，选填）" class="rounded-lg border border-app bg-app px-3 py-2 text-sm" />
      <div class="flex flex-wrap items-center gap-4 text-sm">
        <label class="flex items-center gap-2"><input v-model="form.featured" type="checkbox" true-value="1" false-value="0" class="rounded"> 精选（进首页）</label>
        <label class="flex items-center gap-2"><input v-model="form.status" type="checkbox" true-value="1" false-value="0" class="rounded"> 展示</label>
        <label class="flex items-center gap-2">排序 <input v-model.number="form.sort" type="number" class="w-20 rounded-lg border border-app bg-app px-2 py-1"></label>
        <div class="ml-auto flex gap-2">
          <button v-if="form.id" type="button" class="px-4 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="reset">取消编辑</button>
          <button type="submit" class="px-4 py-2 rounded-lg text-sm font-medium text-white" style="background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2))">
            {{ form.id ? '更新' : '添加' }}
          </button>
        </div>
      </div>
    </form>

    <ul class="mt-6 space-y-3">
      <li v-for="project in data ?? []" :key="project.id" class="flex items-center gap-3 rounded-xl border border-app bg-surface px-4 py-3">
        <img v-if="project.cover" :src="project.cover" alt="" class="w-14 h-10 object-cover rounded border border-app">
        <div class="min-w-0">
          <p class="font-medium">
{{ project.name }}
            <span v-if="project.featured === '1'" class="text-xs ml-1" style="color: var(--color-warn)">精选</span>
            <span v-if="project.status !== '1'" class="text-xs ml-1 text-muted">隐藏</span>
          </p>
          <p class="text-xs text-muted line-clamp-1">{{ project.summary }} · {{ project.techStack }}</p>
        </div>
        <span class="ml-auto flex gap-2 text-xs shrink-0">
          <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" @click="edit(project)">编辑</button>
          <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="remove(project)">删除</button>
        </span>
      </li>
    </ul>
  </div>
</template>
