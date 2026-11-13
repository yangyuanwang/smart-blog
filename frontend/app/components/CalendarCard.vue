<script setup lang="ts">
// 月历组件：当前月网格 + 今日高亮 + 有文章发布的日期标记（数据由父组件传入）
const props = defineProps<{ marked?: Record<string, number> }>()

const now = new Date()
const year = now.getFullYear()
const month = now.getMonth()

const WEEK = ['日', '一', '二', '三', '四', '五', '六']

const cells = computed(() => {
  const first = new Date(year, month, 1)
  const days = new Date(year, month + 1, 0).getDate()
  const leading = first.getDay()
  const list: Array<{ day: number | null, key: string }> = []
  for (let i = 0; i < leading; i++) {
    list.push({ day: null, key: `lead-${i}` })
  }
  for (let day = 1; day <= days; day++) {
    list.push({ day, key: `d-${day}` })
  }
  return list
})

function iso (day: number) {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

</script>

<template>
  <div class="rounded-xl border border-app bg-surface p-4">
    <div class="flex items-baseline justify-between">
      <p class="font-serif font-semibold">{{ year }} 年 {{ month + 1 }} 月</p>
      <p class="text-xs text-muted">{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }) }}</p>
    </div>
    <div class="mt-3 grid grid-cols-7 gap-1 text-center text-xs">
      <span v-for="w in WEEK" :key="w" class="py-1 text-muted" aria-hidden="true">{{ w }}</span>
      <span
        v-for="cell in cells"
        :key="cell.key"
        class="relative py-1.5 rounded-md"
        :class="cell.day === null ? '' : 'hover:bg-surface-2'"
        :aria-label="cell.day ? `${year}年${month + 1}月${cell.day}日` : undefined"
      >
        <template v-if="cell.day">
          <span
            class="inline-grid place-items-center w-6 h-6 rounded-full"
            :class="cell.key === `d-${now.getDate()}` ? 'text-white font-semibold' : ''"
            :style="cell.key === `d-${now.getDate()}` ? { background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' } : {}"
          >{{ cell.day }}</span>
          <span
            v-if="cell.day && props.marked?.[iso(cell.day)]"
            class="absolute bottom-0 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full"
            :style="{ background: 'var(--color-accent)' }"
            aria-hidden="true"
          />
        </template>
      </span>
    </div>
    <p class="mt-2 text-xs text-muted flex items-center gap-1.5">
      <span class="inline-block w-1 h-1 rounded-full" :style="{ background: 'var(--color-accent)' }" aria-hidden="true" />
      有文章发布的日期
    </p>
  </div>
</template>
