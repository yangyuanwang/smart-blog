<script setup lang="ts">
// 关于页：内容由站点设置（about / site_name / site_subtitle）提供
const { data: site } = await useAsyncData('site-public', () => api<Record<string, string>>('/site'))

useSeoMeta({ title: '关于', description: '关于博主与这个站点' })
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="font-serif text-3xl font-bold">关于</h1>
    <div class="mt-6 rounded-xl border border-app bg-surface p-6">
      <p class="font-serif text-xl font-semibold">{{ site?.site_name || '智序 SMART WORKSPACE' }}</p>
      <p class="mt-2 text-sm text-muted whitespace-pre-wrap leading-relaxed">{{ site?.about || '内容加载中…' }}</p>
    </div>
    <div class="mt-6 grid gap-4 sm:grid-cols-3 text-center">
      <NuxtLink to="/articles" class="rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors">
        <p class="font-serif font-semibold">技术文章</p>
        <p class="mt-1 text-xs text-muted">学习与踩坑记录</p>
      </NuxtLink>
      <NuxtLink to="/projects" class="rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors">
        <p class="font-serif font-semibold">作品集</p>
        <p class="mt-1 text-xs text-muted">做过的东西</p>
      </NuxtLink>
      <NuxtLink to="/messages" class="rounded-xl border border-app bg-surface p-4 hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors">
        <p class="font-serif font-semibold">留言板</p>
        <p class="mt-1 text-xs text-muted">随时打个招呼</p>
      </NuxtLink>
    </div>
  </div>
</template>
