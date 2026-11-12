<script setup lang="ts">
// 登录日志
definePageMeta({ layout: 'admin', middleware: 'admin-auth' })

interface LogRow {
  id: number
  username?: string
  ip?: string
  success: string
  message?: string
  loginTime: string
}

const { data, pending } = await useAsyncData('admin-logs', () => api<PagePayload>('/admin/logs', { query: { page: 1, size: 30 } }))
interface PagePayload { list: LogRow[], total: number }

useSeoMeta({ title: '登录日志' })
</script>

<template>
  <div>
    <h1 class="font-serif text-2xl font-bold">登录日志</h1>
    <p v-if="pending" class="mt-6 text-muted" role="status">加载中…</p>
    <div v-else class="mt-4 rounded-xl border border-app bg-surface overflow-x-auto">
      <table class="w-full text-sm min-w-[560px]">
        <thead>
          <tr class="text-left text-muted border-b border-app">
            <th class="px-4 py-3">时间</th>
            <th class="px-4 py-3">用户</th>
            <th class="px-4 py-3">IP</th>
            <th class="px-4 py-3">结果</th>
            <th class="px-4 py-3">说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!data?.list?.length">
            <td colspan="5" class="px-4 py-10 text-center text-muted">暂无日志</td>
          </tr>
          <tr v-for="log in data?.list ?? []" :key="log.id" class="border-b border-app last:border-0">
            <td class="px-4 py-3 text-xs text-muted">{{ log.loginTime?.slice(0, 19) }}</td>
            <td class="px-4 py-3">{{ log.username }}</td>
            <td class="px-4 py-3 text-xs text-muted">{{ log.ip }}</td>
            <td class="px-4 py-3">
              <span :style="{ color: log.success === '1' ? 'var(--color-ok)' : 'var(--color-danger)' }">
                {{ log.success === '1' ? '成功' : '失败' }}
              </span>
            </td>
            <td class="px-4 py-3 text-muted">{{ log.message }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
