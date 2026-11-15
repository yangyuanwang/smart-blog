<script setup lang="ts">
// 全站标题模板：子页 "页名 · 智序"，首页兜底站名（config schema 不支持函数形式，故在应用层设置）
useHead({
  titleTemplate: (title?: string) => (title ? `${title} · 智序` : '智序 SMART WORKSPACE')
})
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <!-- 全局通知层 -->
  <AppToast />
</template>
