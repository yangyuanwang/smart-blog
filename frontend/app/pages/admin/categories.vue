<script setup lang="ts">
// 栏目与标签管理：双栏 + 编辑（V1.1 第二批补齐的重命名/换栏目能力）
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface Category { cid: number, cname: string, articleCount: number }
interface Tag { tid: number, tname: string, cid: number }

const { data: categories, refresh: refreshCategories } = await useAsyncData('admin-categories', () => api<Category[]>('/admin/categories'))
const { data: tags, refresh: refreshTags } = await useAsyncData('admin-tags', () => api<Tag[]>('/admin/tags'))

const newCategory = ref('')
const newTag = ref('')
const newTagCid = ref<number | null>(null)
const editingCategory = ref<{ cid: number, name: string } | null>(null)
const editingTag = ref<{ tid: number, tname: string, cid: number } | null>(null)
const toast = useToast()

async function addCategory () {
  try {
    await api('/admin/categories', { method: 'POST', body: { cname: newCategory.value } })
    newCategory.value = ''
    toast.success('栏目已添加')
    await refreshCategories()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function renameCategory () {
  if (!editingCategory.value) {
    return
  }
  try {
    await api(`/admin/categories/${editingCategory.value.cid}`, { method: 'PUT', body: { cname: editingCategory.value.name } })
    editingCategory.value = null
    toast.success('栏目已重命名')
    await refreshCategories()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function removeCategory (category: Category) {
  if (!window.confirm(`确定删除栏目「${category.cname}」及其标签吗？`)) {
    return
  }
  try {
    await api(`/admin/categories/${category.cid}`, { method: 'DELETE' })
    toast.success('栏目已删除')
    await refreshCategories()
    await refreshTags()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function addTag () {
  try {
    await api('/admin/tags', { method: 'POST', body: { cid: newTagCid.value, tname: newTag.value } })
    newTag.value = ''
    toast.success('标签已添加')
    await refreshTags()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function updateTag () {
  if (!editingTag.value) {
    return
  }
  try {
    await api(`/admin/tags/${editingTag.value.tid}`, { method: 'PUT', body: { tname: editingTag.value.tname, cid: editingTag.value.cid } })
    editingTag.value = null
    toast.success('标签已更新')
    await refreshTags()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

async function removeTag (tag: Tag) {
  if (!window.confirm(`确定删除标签「${tag.tname}」吗？`)) {
    return
  }
  try {
    await api(`/admin/tags/${tag.tid}`, { method: 'DELETE' })
    toast.success('标签已删除')
    await refreshTags()
  } catch (error) {
    toast.error((error as Error).message)
  }
}

useSeoMeta({ title: '栏目与标签' })

// 按栏目分组标签：归属关系一目了然；无栏目（cid 为空）的归入「未分组」
const groupedTags = computed<{ cid: number | null, name: string, tags: Tag[] }[]>(() => {
  const list = tags.value ?? []
  const groups: { cid: number | null, name: string, tags: Tag[] }[] = (categories.value ?? []).map(category => ({
    cid: category.cid,
    name: category.cname,
    tags: list.filter(tag => tag.cid === category.cid)
  }))
  const orphans = list.filter(tag => !tag.cid || !groups.some(group => group.cid === tag.cid))
  if (orphans.length) {
    groups.push({ cid: null, name: '未分组', tags: orphans })
  }
  return groups
})
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">栏目与标签</h1>

    <div class="mt-6 grid gap-6 lg:grid-cols-2">
      <!-- 栏目 -->
      <section class="rounded-xl border border-app bg-surface p-4" aria-labelledby="category-heading">
        <h2 id="category-heading" class="font-serif font-semibold">栏目</h2>
        <form class="mt-3 flex gap-2" @submit.prevent="addCategory">
          <label class="sr-only" for="new-category">新栏目名</label>
          <input id="new-category" v-model="newCategory" maxlength="30" placeholder="新栏目名" class="flex-1 rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
          <button type="submit" class="px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2">添加</button>
        </form>
        <ul class="mt-4 space-y-2">
          <li v-for="category in categories ?? []" :key="category.cid" class="flex items-center gap-2 rounded-lg border border-app px-3 py-2">
            <template v-if="editingCategory?.cid === category.cid">
              <input v-model="editingCategory.name" maxlength="30" class="flex-1 rounded-lg border border-app bg-app px-2 py-1 text-sm">
              <button type="button" class="text-xs px-2 py-1 rounded-md border border-app hover:bg-surface-2" @click="renameCategory">保存</button>
              <button type="button" class="text-xs text-muted" @click="editingCategory = null">取消</button>
            </template>
            <template v-else>
              <span class="font-medium">{{ category.cname }}</span>
              <span class="text-xs text-muted">{{ category.articleCount }} 篇</span>
              <span class="ml-auto flex gap-1.5 text-xs">
                <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" @click="editingCategory = { cid: category.cid, name: category.cname }">编辑</button>
                <button type="button" class="px-2 py-1 rounded-md border border-app hover:bg-surface-2" style="color: var(--color-danger)" @click="removeCategory(category)">删除</button>
              </span>
            </template>
          </li>
        </ul>
      </section>

      <!-- 标签 -->
      <section class="rounded-xl border border-app bg-surface p-4" aria-labelledby="tag-heading">
        <h2 id="tag-heading" class="font-serif font-semibold">标签</h2>
        <form class="mt-3 flex gap-2" @submit.prevent="addTag">
          <label class="sr-only" for="new-tag">新标签名</label>
          <input id="new-tag" v-model="newTag" maxlength="30" placeholder="新标签名" class="w-36 rounded-lg border border-app bg-app px-3 py-2 text-sm focus-visible:ring-2 ring-app">
          <select v-model.number="newTagCid" class="flex-1 rounded-lg border border-app bg-app px-2 py-2 text-sm focus-visible:ring-2 ring-app">
            <option :value="null" disabled>所属栏目</option>
            <option v-for="category in categories ?? []" :key="category.cid" :value="category.cid">{{ category.cname }}</option>
          </select>
          <button type="submit" class="px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2">添加</button>
        </form>
        <!-- 按栏目分组的标签：归属一目了然 -->
        <div class="mt-4 space-y-4">
          <section
            v-for="group in groupedTags"
            :key="group.cid ?? 0"
            :aria-label="`栏目 ${group.name} 的标签`"
            class="rounded-lg border border-app p-3"
          >
            <div class="flex items-baseline justify-between">
              <h3 class="text-sm font-medium">{{ group.name }}</h3>
              <span class="text-xs text-muted">{{ group.tags.length }} 个标签</span>
            </div>
            <ul class="mt-2 flex flex-wrap gap-2">
              <li v-for="tag in group.tags" :key="tag.tid">
                <template v-if="editingTag?.tid === tag.tid">
                  <div class="flex gap-1 items-center rounded-full border border-app px-3 py-1">
                    <input v-model="editingTag.tname" maxlength="30" class="w-24 rounded border border-app bg-app px-1 text-xs">
                    <select v-model.number="editingTag.cid" class="rounded border border-app bg-app text-xs">
                      <option v-for="category in categories ?? []" :key="category.cid" :value="category.cid">{{ category.cname }}</option>
                    </select>
                    <button type="button" class="text-xs" @click="updateTag">存</button>
                    <button type="button" class="text-xs text-muted" @click="editingTag = null">取消</button>
                  </div>
                </template>
                <span v-else class="inline-flex items-center gap-1.5 rounded-full border border-app px-3 py-1 text-sm">
                  {{ tag.tname }}
                  <button type="button" class="text-xs text-muted hover:text-main" aria-label="编辑标签" @click="editingTag = { tid: tag.tid, tname: tag.tname, cid: tag.cid }">✏️</button>
                  <button type="button" class="text-xs text-muted hover:text-main" style="color: var(--color-danger)" aria-label="删除标签" @click="removeTag(tag)">×</button>
                </span>
              </li>
              <li v-if="!group.tags.length" class="text-xs text-muted">该栏目下还没有标签</li>
            </ul>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>
