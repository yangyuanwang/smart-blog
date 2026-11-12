<script setup lang="ts">
// 站点设置：保存即失效缓存，前台 5 秒内生效
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

const fields = [
  { key: 'site_name', label: '站点名' },
  { key: 'site_subtitle', label: '副标题' },
  { key: 'about', label: '关于页内容（textarea）', multiline: true },
  { key: 'footer', label: '页脚文案' },
  { key: 'icp', label: '备案号（页脚展示）' },
  { key: 'page_size', label: '列表分页大小' },
  { key: 'allow_comment', label: '开启评论/留言（1 开 / 0 关）' },
  { key: 'comment_audit', label: '评论先审后发（1 开 / 0 关）' },
  { key: 'allow_register', label: '开放注册（1 开 / 0 关）' },
  { key: 'admin_message_pass', label: '管理员留言直接过审（1 开 / 0 关）' },
  { key: 'show_visit', label: '展示访问量（1 开 / 0 关）' }
]

const values = ref<Record<string, string>>({})
const saving = ref(false)
const toast = ref('')

const { data, refresh } = await useAsyncData('admin-settings', () => api<Record<string, string>>('/admin/settings'))

watch(data, (value) => {
  values.value = { ...(value ?? {}) }
}, { immediate: true })

async function save () {
  saving.value = true
  try {
    await api('/admin/settings', { method: 'PUT', body: { values: values.value } })
    toast.value = '设置已保存，前台缓存已失效'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  } finally {
    saving.value = false
  }
}

useSeoMeta({ title: '站点设置' })
</script>

<template>
  <div class="max-w-3xl">
    <h1 class="font-serif text-2xl font-bold">站点设置</h1>
    <form class="mt-6 rounded-xl border border-app bg-surface p-5 space-y-4" @submit.prevent="save">
      <label v-for="field in fields" :key="field.key" class="block text-sm">
        <span class="mb-1 block">{{ field.label }}</span>
        <textarea
          v-if="field.multiline"
          v-model="values[field.key]"
          rows="4"
          class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        />
        <input
          v-else
          v-model="values[field.key]"
          class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        >
      </label>
      <p v-if="toast" class="text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>
      <button
        type="submit"
        :disabled="saving"
        class="px-5 py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >{{ saving ? '保存中…' : '保存设置' }}</button>
    </form>
  </div>
</template>
