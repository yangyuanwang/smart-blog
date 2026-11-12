<script setup lang="ts">
// 用户管理：搜索 / 锁定解锁 / 角色调整 / 重置密码
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface UserRow {
  uid: number
  username: string
  nickname?: string
  email?: string
  role?: string
  state?: string
  createTime?: string
  lastLoginTime?: string
  loginCount?: number
}

const keyword = ref('')
const { data, refresh } = await useAsyncData(
  'admin-users',
  () => api<PagePayload>('/admin/users', { query: { page: 1, size: 20, ...(keyword.value ? { keyword: keyword.value } : {}) } }),
  { watch: [keyword] }
)
interface PagePayload { list: UserRow[], total: number }

const { data: session } = await useAsyncData('me-profile', () => api<{ uid: number } | null>('/me/profile').catch(() => null))

const toast = ref('')
const resetting = ref<{ uid: number, password: string } | null>(null)

async function changeState (user: UserRow, state: string) {
  try {
    await api(`/admin/users/${user.uid}/state`, { method: 'PUT', body: { value: state } })
    toast.value = state === '0' ? '已锁定（会话已吊销）' : '已解锁'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function changeRole (user: UserRow, role: string) {
  try {
    await api(`/admin/users/${user.uid}/role`, { method: 'PUT', body: { value: role } })
    toast.value = role === '0' ? '已提升为管理员' : '已降为普通用户'
    await refresh()
  } catch (error) {
    toast.value = (error as Error).message
  }
}

async function resetPassword () {
  if (!resetting.value) {
    return
  }
  try {
    await api(`/admin/users/${resetting.value.uid}/password`, { method: 'PUT', body: { value: resetting.value.password } })
    toast.value = '密码已重置'
    resetting.value = null
  } catch (error) {
    toast.value = (error as Error).message
  }
}

const stateMeta: Record<string, { label: string, color: string }> = {
  '0': { label: '已锁定', color: 'var(--color-danger)' },
  '1': { label: '正常', color: 'var(--color-ok)' }
}

useSeoMeta({ title: '用户管理' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">用户管理</h1>
    <div class="mt-4">
      <label class="sr-only" for="user-search">搜索用户</label>
      <input
        id="user-search"
        v-model="keyword"
        placeholder="用户名 / 昵称"
        class="w-64 rounded-lg border border-app bg-surface px-3 py-2 text-sm focus-visible:ring-2 ring-app"
      >
    </div>
    <p v-if="toast" class="mt-2 text-sm" style="color: var(--color-ok)" role="status">{{ toast }}</p>

    <div class="mt-4 rounded-xl border border-app bg-surface overflow-x-auto">
      <table class="w-full text-sm min-w-[680px]">
        <thead>
          <tr class="text-left text-muted border-b border-app">
            <th class="px-4 py-3">用户</th>
            <th class="px-4 py-3">角色</th>
            <th class="px-4 py-3">状态</th>
            <th class="px-4 py-3">最近登录</th>
            <th class="px-4 py-3">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!data?.list?.length">
            <td colspan="5" class="px-4 py-10 text-center text-muted">没有用户</td>
          </tr>
          <tr v-for="user in data?.list ?? []" :key="user.uid" class="border-b border-app last:border-0">
            <td class="px-4 py-3">
              <p class="font-medium">{{ user.username }}（{{ user.nickname || '无昵称' }}）</p>
              <p class="text-xs text-muted">{{ user.email || '未绑定邮箱' }} · 注册 {{ user.createTime?.slice(0, 10) }}</p>
            </td>
            <td class="px-4 py-3">
              <span :style="{ color: user.role === '0' ? 'var(--color-accent)' : 'var(--text-muted)' }">
                {{ user.role === '0' ? '管理员' : '普通用户' }}
              </span>
            </td>
            <td class="px-4 py-3">
              <span :style="{ color: stateMeta[user.state ?? '1']?.color }">{{ stateMeta[user.state ?? '1']?.label }}</span>
            </td>
            <td class="px-4 py-3 text-xs text-muted">{{ user.lastLoginTime?.slice(0, 16) || '从未登录' }}</td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1.5 text-xs">
                <template v-if="user.uid !== session?.uid">
                  <button
                    type="button"
                    class="px-2 py-1 rounded-md border border-app hover:bg-surface-2"
                    @click="changeState(user, user.state === '0' ? '1' : '0')"
                  >
{{ user.state === '0' ? '解锁' : '锁定' }}
</button>
                  <button
                    type="button"
                    class="px-2 py-1 rounded-md border border-app hover:bg-surface-2"
                    @click="changeRole(user, user.role === '0' ? '1' : '0')"
                  >
{{ user.role === '0' ? '降为用户' : '设为管理员' }}
</button>
                  <button
                    type="button"
                    class="px-2 py-1 rounded-md border border-app hover:bg-surface-2"
                    @click="resetting = { uid: user.uid, password: '' }"
                  >
重置密码
</button>
                </template>
                <span v-else class="text-xs text-muted">当前登录账号</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="resetting" class="fixed inset-0 z-50 grid place-items-center bg-black/40" @click.self="resetting = null">
      <div class="rounded-xl border border-app bg-surface p-5 w-full max-w-sm">
        <h2 class="font-serif font-semibold">重置密码（uid {{ resetting.uid }}）</h2>
        <input v-model="resetting.password" type="password" minlength="6" placeholder="新密码（至少 6 位）" class="mt-3 w-full rounded-lg border border-app bg-app px-3 py-2 text-sm">
        <div class="mt-4 flex justify-end gap-2">
          <button type="button" class="px-3 py-2 rounded-lg text-sm border border-app hover:bg-surface-2" @click="resetting = null">取消</button>
          <button type="button" class="px-3 py-2 rounded-lg text-sm font-medium text-white" style="background: linear-gradient(120deg, var(--color-accent), var(--color-accent-2))" @click="resetPassword">确认重置</button>
        </div>
      </div>
    </div>
  </div>
</template>
