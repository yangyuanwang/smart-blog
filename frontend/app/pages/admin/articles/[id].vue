<script setup lang="ts">
// 文章编辑器：新建与更新共用，含封面上传、标签、SEO、状态机
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

const route = useRoute()
const isEdit = computed(() => route.params.id !== 'new')
const aid = computed(() => (isEdit.value ? Number(route.params.id) : null))

const form = ref({
  title: '',
  digest: '',
  content: '',
  cid: null as number | null,
  tagNames: '',
  publishStatus: '0',
  isHot: 0,
  isTop: 0,
  logo: '',
  seoTitle: '',
  seoKeywords: '',
  seoDesc: ''
})

const { data: categories } = await useAsyncData('admin-categories', () => api<{ cid: number, cname: string }[]>('/admin/categories'))

if (isEdit.value) {
  const article = await api<{
    title: string
    digest?: string
    content: string
    cid?: number
    tagNames?: string
    publishStatus?: string
    isHot?: number
    isTop?: number
    logo?: string
    seoTitle?: string
    seoKeywords?: string
    seoDesc?: string
  }>(`/admin/articles/${aid.value}`)
  form.value = {
    title: article.title,
    digest: article.digest ?? '',
    content: article.content,
    cid: article.cid ?? null,
    tagNames: article.tagNames ?? '',
    publishStatus: article.publishStatus ?? '0',
    isHot: article.isHot ?? 0,
    isTop: article.isTop ?? 0,
    logo: article.logo ?? '',
    seoTitle: article.seoTitle ?? '',
    seoKeywords: article.seoKeywords ?? '',
    seoDesc: article.seoDesc ?? ''
  }
}

const saving = ref(false)
const toast = useToast()
const uploading = ref(false)

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
    form.value.logo = result.url
    toast.success('封面上传成功')
  } catch (e) {
    toast.error((e as Error).message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function save (publishStatus?: string) {
  if (saving.value) {
    return
  }
  saving.value = true
  toast.error('')
  toast.success('')
  try {
    const payload = {
      title: form.value.title,
      digest: form.value.digest,
      content: form.value.content,
      cid: form.value.cid,
      tagNames: form.value.tagNames,
      publishStatus: publishStatus ?? form.value.publishStatus,
      isHot: form.value.isHot,
      isTop: form.value.isTop,
      logo: form.value.logo,
      seoTitle: form.value.seoTitle,
      seoKeywords: form.value.seoKeywords,
      seoDesc: form.value.seoDesc
    }
    if (isEdit.value) {
      await api(`/admin/articles/${aid.value}`, { method: 'PUT', body: payload })
      form.value.publishStatus = publishStatus ?? form.value.publishStatus
      toast.success(publishStatus === '1' ? '已发布' : '已保存')
    } else {
      const created = await api<{ aid: number }>('/admin/articles', { method: 'POST', body: payload })
      toast.success('创建成功，跳转编辑页')
      await navigateTo(`/admin/articles/${created.aid}`)
    }
  } catch (e) {
    toast.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

useSeoMeta({ title: isEdit.value ? '编辑文章' : '新建文章' })
</script>

<template>
  <div class="max-w-4xl">
    <div class="flex items-center justify-between">
      <h1 class="font-serif text-2xl font-bold">{{ isEdit ? '编辑文章' : '新建文章' }}</h1>
      <NuxtLink to="/admin/articles" class="text-sm text-muted hover:text-main">← 返回列表</NuxtLink>
    </div>

    <div class="mt-6 space-y-4">
      <label class="block text-sm">
        <span class="mb-1 block">标题 <span style="color: var(--color-danger)">*</span></span>
        <input v-model="form.title" maxlength="255" class="w-full rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app">
      </label>

      <div class="grid gap-4 sm:grid-cols-2">
        <label class="block text-sm">
          <span class="mb-1 block">栏目 <span style="color: var(--color-danger)">*</span></span>
          <select v-model.number="form.cid" class="w-full rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app">
            <option :value="null" disabled>选择栏目</option>
            <option v-for="category in categories ?? []" :key="category.cid" :value="category.cid">{{ category.cname }}</option>
          </select>
        </label>
        <label class="block text-sm">
          <span class="mb-1 block">标签（逗号分隔）</span>
          <input v-model="form.tagNames" maxlength="255" placeholder="Java, Docker" class="w-full rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app">
        </label>
      </div>

      <div class="grid gap-4 sm:grid-cols-3">
        <label class="block text-sm">
          <span class="mb-1 block">发布状态</span>
          <select v-model="form.publishStatus" class="w-full rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app">
            <option value="0">草稿</option>
            <option value="1">已发布</option>
            <option value="2">已下线</option>
          </select>
        </label>
        <label class="flex items-center gap-2 text-sm mt-6">
          <input v-model="form.isHot" type="checkbox" :true-value="1" :false-value="0" class="rounded"> 热门
        </label>
        <label class="flex items-center gap-2 text-sm mt-6">
          <input v-model="form.isTop" type="checkbox" :true-value="1" :false-value="0" class="rounded"> 置顶
        </label>
      </div>

      <label class="block text-sm">
        <span class="mb-1 block">摘要（留空则自动生成）</span>
        <textarea v-model="form.digest" rows="2" class="w-full rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app" />
      </label>

      <div class="rounded-xl border border-app bg-surface p-4">
        <p class="text-sm font-medium">封面</p>
        <div class="mt-2 flex items-center gap-3">
          <img v-if="form.logo" :src="form.logo" alt="封面预览" class="w-32 h-20 object-cover rounded-lg border border-app">
          <label class="px-3 py-2 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 cursor-pointer focus-visible:ring-2 ring-app">
            {{ uploading ? '上传中…' : '选择图片' }}
            <input type="file" accept="image/jpeg,image/png,image/gif,image/webp" class="sr-only" @change="uploadCover">
          </label>
          <button v-if="form.logo" type="button" class="text-xs text-muted hover:text-main" @click="form.logo = ''">移除</button>
        </div>
      </div>

      <label class="block text-sm">
        <span class="mb-1 block">正文（Markdown）<span style="color: var(--color-danger)">*</span></span>
        <textarea
          v-model="form.content"
          rows="16"
          required
          class="w-full rounded-lg border border-app bg-surface px-3 py-2 font-mono text-sm focus-visible:ring-2 ring-app"
          placeholder="支持 Markdown，发布后 AI 会自动生成摘要"
        />
      </label>

      <details class="rounded-xl border border-app bg-surface p-4">
        <summary class="text-sm font-medium cursor-pointer">SEO 设置（选填）</summary>
        <div class="mt-3 space-y-3">
          <input v-model="form.seoTitle" maxlength="255" placeholder="SEO 标题" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm">
          <input v-model="form.seoKeywords" maxlength="255" placeholder="SEO 关键词" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm">
          <textarea v-model="form.seoDesc" rows="2" maxlength="500" placeholder="SEO 描述" class="w-full rounded-lg border border-app bg-app px-3 py-2 text-sm" />
        </div>
      </details>

      <div class="flex flex-wrap gap-3">
        <button
          type="button"
          :disabled="saving"
          class="px-5 py-2.5 rounded-lg text-sm font-medium text-white disabled:opacity-40 focus-visible:ring-2 ring-app"
          :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
          @click="save('1')"
        >
{{ saving ? '处理中…' : '发布' }}
</button>
        <button
          type="button"
          :disabled="saving"
          class="px-5 py-2.5 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app"
          @click="save('0')"
        >
存为草稿
</button>
        <button
          v-if="isEdit"
          type="button"
          :disabled="saving"
          class="px-5 py-2.5 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2 focus-visible:ring-2 ring-app"
          @click="save('2')"
        >
下线
</button>
      </div>
    </div>
  </div>
</template>
