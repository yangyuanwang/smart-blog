<script setup lang="ts">
// 文章管理列表：筛选 / 状态机 / 置顶热门 / AI 摘要重生成 / 删除
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface AdminArticle {
  aid: number
  title: string
  categoryName?: string
  authorName?: string
  publishStatus?: string
  createTime?: string
  visitCount?: number
  thumbsUp?: number
  isTop?: number
  aiSummary?: string
}

const route = useRoute()
const page = computed(() => Math.max(1, Number(route.query.page) || 1))
const title = ref(String(route.query.title ?? ''))
const status = ref(String(route.query.publishStatus ?? ''))

const { data, pending, refresh } = await useAsyncData(
  `admin-articles-${page.value}-${title.value}-${status.value}`,
  () => api<PagePayload>('/admin/articles', {
    query: {
      page: page.value,
      size: 10,
      ...(title.value ? { title: title.value } : {}),
      ...(status.value ? { publishStatus: status.value } : {})
    }
  }),
  { watch: [page, status] }
)

interface PagePayload { list: AdminArticle[], total: number, page: number, size: number }

const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / (data.value?.size ?? 10))))
const toast = useToast()

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: '0', label: '草稿' },
  { value: '1', label: '已发布' },
  { value: '2', label: '已下线' }
]

const statusMeta: Record<string, { label: string, color: string }> = {
  '0': { label: '草稿', color: 'var(--text-muted)' },
  '1': { label: '已发布', color: 'var(--color-ok)' },
  '2': { label: '已下线', color: 'var(--color-danger)' }
}

function search () {
  navigateTo({ path: '/admin/articles', query: { ...(title.value ? { title: title.value } : {}) } })
}

async function changeStatus (id: number, next: string) {
  try {
    const result = await api<{ message: string }>(`/admin/articles/${id}/status`, { method: 'PUT', body: { status: next } })
    toast.success(result.message)
    await refresh()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function toggleTop (article: AdminArticle) {
  try {
    await api(`/admin/articles/${article.aid}/hot`, { method: 'PUT', body: { status: article.isTop === 1 ? '0' : '1' } })
    toast.success(article.isTop === 1 ? '已取消置顶' : '已置顶')
    await refresh()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function regenAi (id: number) {
  try {
    await api(`/admin/articles/${id}/ai-summary`, { method: 'PUT' })
    toast.success('AI 摘要生成任务已提交（异步执行）')
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function remove (article: AdminArticle) {
  if (!window.confirm(`确定删除文章「${article.title}」吗？`)) {
    return
  }
  try {
    await api(`/admin/articles/${article.aid}`, { method: 'DELETE' })
    toast.success('已删除')
    await refresh()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

useSeoMeta({ title: '文章管理' })
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="font-serif text-2xl font-bold">文章管理</h1>
      <NuxtLink
        to="/admin/articles/new"
        class="px-4 py-2 rounded-lg text-sm font-medium text-white focus-visible:ring-2 ring-app"
        :style="{ background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' }"
      >+ 新建文章</NuxtLink>
    </div>

    <div class="mt-4 flex flex-wrap gap-2">
      <input
        v-model="title"
        placeholder="按标题搜索"
        class="rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app"
        @keyup.enter="search"
      >
      <select v-model="status" class="rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app" @change="search">
        <option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <button type="button" class="px-3 py-2 rounded-lg text-sm border border-app bg-surface hover:bg-surface-2" @click="search">搜索</button>
    </div>
    <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>

    <div v-else class="mt-4 rounded-xl border border-app bg-surface overflow-x-auto">
      <table class="w-full text-sm min-w-[720px]">
        <thead>
          <tr class="text-left text-muted border-b border-app">
            <th class="px-4 py-3">标题</th>
            <th class="px-4 py-3">栏目</th>
            <th class="px-4 py-3">状态</th>
            <th class="px-4 py-3">浏览/赞</th>
            <th class="px-4 py-3">时间</th>
            <th class="px-4 py-3">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!data?.list?.length">
            <td colspan="6" class="px-4 py-10 text-center text-muted">没有文章</td>
          </tr>
          <tr v-for="article in data?.list ?? []" :key="article.aid" class="border-b border-app last:border-0">
            <td class="px-4 py-3">
              <NuxtLink :to="`/admin/articles/${article.aid}`" class="font-medium hover:underline">{{ article.title }}</NuxtLink>
              <p v-if="article.aiSummary" class="mt-1 text-xs text-muted line-clamp-1">AI: {{ article.aiSummary }}</p>
            </td>
            <td class="px-4 py-3 text-muted">{{ article.categoryName }}</td>
            <td class="px-4 py-3">
              <span :style="{ color: statusMeta[article.publishStatus ?? '0']?.color }">{{ statusMeta[article.publishStatus ?? '0']?.label }}</span>
            </td>
            <td class="px-4 py-3 text-muted">{{ article.visitCount ?? 0 }} / {{ article.thumbsUp ?? 0 }}</td>
            <td class="px-4 py-3 text-muted text-xs">{{ article.createTime?.slice(0, 10) }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1.5 text-xs">
                <button
                  v-if="article.publishStatus !== '1'"
                  type="button"
                  class="px-2 py-1 rounded-md border border-app hover:bg-surface-2"
                  @click="changeStatus(article.aid, '1')"
                >
发布
</button>
                <button
                  v-if="article.publishStatus === '1'"
                  type="button"
                  class="px-2 py-1 rounded-md border border-app hover:bg-surface-2"
                  @click="changeStatus(article.aid, '2')"
                >
下线
</button>
                <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" @click="toggleTop(article)">
                  {{ article.isTop === 1 ? '取消置顶' : '置顶' }}
                </button>
                <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" @click="regenAi(article.aid)">AI 摘要</button>
                <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="remove(article)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <nav v-if="totalPages > 1" class="mt-4 flex items-center justify-center gap-2" aria-label="分页">
      <button type="button" class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm disabled:opacity-40" :disabled="page <= 1" @click="navigateTo({ path: '/admin/articles', query: { ...(page > 2 ? { page: String(page - 1) } : {}) } })">上一页</button>
      <span class="text-sm text-muted px-2">{{ page }} / {{ totalPages }}</span>
      <button type="button" class="px-3 py-1.5 rounded-lg border border-app bg-surface text-sm disabled:opacity-40" :disabled="page >= totalPages" @click="navigateTo({ path: '/admin/articles', query: { page: String(page + 1), ...(title ? { title } : {}) } })">下一页</button>
    </nav>
  </div>
</template>
