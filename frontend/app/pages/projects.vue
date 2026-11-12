<script setup lang="ts">
// 作品集：项目卡片（技术栈徽章 / 仓库 / 演示链接）
interface ProjectItem {
  id: number
  name: string
  summary: string
  description?: string
  techStack?: string
  cover?: string
  repoUrl?: string
  demoUrl?: string
  featured?: string
}

const { data, error, pending } = await useAsyncData('projects', () => api<ProjectItem[]>('/projects'))

function techList (stack?: string) {
  return (stack ?? '').split(',').map(item => item.trim()).filter(Boolean)
}

useSeoMeta({ title: '作品集', description: '做过的项目：技术栈、仓库与演示' })
</script>

<template>
  <div>
    <h1 class="font-serif text-3xl font-bold">作品集</h1>
    <p class="mt-2 text-sm text-muted">做过的一些项目，记录技术栈与实现</p>

    <p v-if="pending" class="mt-8 text-muted" role="status">加载中…</p>
    <div v-else-if="error" class="mt-8 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted" role="alert">
      作品加载失败，请稍后重试
    </div>
    <div v-else-if="!data?.length" class="mt-8 rounded-xl border border-app bg-surface px-4 py-14 text-center text-muted">
      作品正在整理中，敬请期待
    </div>
    <ul v-else class="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <li
        v-for="project in data"
        :key="project.id"
        class="rounded-xl border border-app bg-surface overflow-hidden hover:-translate-y-0.5 hover:bg-surface-2 transition-all"
      >
        <div class="h-32 bg-surface-2 grid place-items-center overflow-hidden">
          <img
            v-if="project.cover"
            :src="project.cover"
            :alt="`${project.name} 封面`"
            class="w-full h-full object-cover"
            loading="lazy"
          >
          <span v-else class="font-serif text-4xl gradient-text" aria-hidden="true">{{ project.name.slice(0, 2) }}</span>
        </div>
        <div class="p-5">
          <div class="flex items-center gap-2">
            <h2 class="font-serif text-lg font-semibold">{{ project.name }}</h2>
            <span v-if="project.featured === '1'" class="text-xs px-2 py-0.5 rounded-md bg-surface-2" style="color: var(--color-warn)">精选</span>
          </div>
          <p class="mt-2 text-sm text-muted">{{ project.summary }}</p>
          <div class="mt-3 flex flex-wrap gap-1.5">
            <span
              v-for="tech in techList(project.techStack)"
              :key="tech"
              class="text-xs px-2 py-0.5 rounded-full border border-app text-muted"
            >{{ tech }}</span>
          </div>
          <div class="mt-4 flex gap-3 text-sm">
            <a
              v-if="project.repoUrl"
              :href="project.repoUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="underline-offset-2 hover:underline focus-visible:ring-2 ring-app rounded"
            >仓库</a>
            <a
              v-if="project.demoUrl"
              :href="project.demoUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="underline-offset-2 hover:underline focus-visible:ring-2 ring-app rounded"
            >演示</a>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>
