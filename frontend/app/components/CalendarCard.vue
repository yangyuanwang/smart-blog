<script setup lang="ts">
// 月历筛选器：月份切换 + 今日高亮 + 有日记的日期圆点标记；点击有记录的日期筛选当天日记
// 数据由父组件传入（marked: 日期 → 篇数），选中通过 select 事件回传（再点一次取消）
const props = defineProps<{
  marked?: Record<string, number>
  selected?: string | null
  /** 初始定位月份（YYYY-MM）：用于默认展示最近有记录的月份而非当前月 */
  focusDate?: string
}>()

const emit = defineEmits<{ select: [date: string | null] }>()

const today = new Date()
// 初始视图：优先定位 focusDate 指定月份；数据晚于首帧到达时补一次定位
const initial = props.focusDate?.match(/^(\d{4})-(\d{2})$/)
const viewYear = ref(initial ? Number(initial[1]) : today.getFullYear())
const viewMonth = ref(initial ? Number(initial[2]) - 1 : today.getMonth()) // 0-based
let focused = !!initial
watch(() => props.focusDate, (value) => {
  if (focused || !value) {
    return
  }
  const m = value.match(/^(\d{4})-(\d{2})$/)
  if (!m) {
    return
  }
  viewYear.value = Number(m[1])
  viewMonth.value = Number(m[2]) - 1
  focused = true
})

const WEEK = ['日', '一', '二', '三', '四', '五', '六']

const isCurrentMonth = computed(() =>
  viewYear.value === today.getFullYear() && viewMonth.value === today.getMonth()
)

const monthLabel = computed(() => `${viewYear.value} 年 ${viewMonth.value + 1} 月`)

function shiftMonth (delta: number) {
  const next = new Date(viewYear.value, viewMonth.value + delta, 1)
  viewYear.value = next.getFullYear()
  viewMonth.value = next.getMonth()
}

const cells = computed(() => {
  const first = new Date(viewYear.value, viewMonth.value, 1)
  const days = new Date(viewYear.value, viewMonth.value + 1, 0).getDate()
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
  return `${viewYear.value}-${String(viewMonth.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function onSelect (day: number) {
  const date = iso(day)
  // 再点同一天取消筛选
  emit('select', props.selected === date ? null : date)
}
</script>

<template>
  <div class="rounded-xl border border-app bg-surface p-4">
    <div class="flex items-center justify-between">
      <p class="font-serif font-semibold">{{ monthLabel }}</p>
      <div class="flex items-center gap-1">
        <button
          type="button"
          class="w-7 h-7 grid place-items-center rounded-md border border-app hover:bg-surface-2 focus-visible:ring-2 ring-app"
          aria-label="上一个月"
          @click="shiftMonth(-1)"
        >‹</button>
        <button
          v-if="!isCurrentMonth"
          type="button"
          class="px-2 h-7 rounded-md border border-app text-xs hover:bg-surface-2 focus-visible:ring-2 ring-app"
          @click="viewYear = today.getFullYear(); viewMonth = today.getMonth()"
        >本月</button>
        <button
          type="button"
          class="w-7 h-7 grid place-items-center rounded-md border border-app hover:bg-surface-2 focus-visible:ring-2 ring-app"
          aria-label="下一个月"
          @click="shiftMonth(1)"
        >›</button>
      </div>
    </div>
    <div class="mt-3 grid grid-cols-7 gap-1 text-center text-xs">
      <span v-for="w in WEEK" :key="w" class="py-1 text-muted" aria-hidden="true">{{ w }}</span>
      <template v-for="cell in cells" :key="cell.key">
        <span v-if="!cell.day" />
        <button
          v-else-if="props.marked?.[iso(cell.day)]"
          type="button"
          class="relative py-1.5 rounded-md hover:bg-surface-2 focus-visible:ring-2 ring-app"
          :class="props.selected === iso(cell.day) ? 'bg-surface-2 font-medium' : ''"
          :aria-pressed="props.selected === iso(cell.day)"
          :aria-label="`${viewYear}年${viewMonth + 1}月${cell.day}日，${props.marked[iso(cell.day)]} 篇日记`"
          @click="onSelect(cell.day)"
        >
          <span
            class="inline-grid place-items-center w-6 h-6 rounded-full"
            :class="props.selected === iso(cell.day) ? 'text-white font-semibold' : ''"
            :style="props.selected === iso(cell.day) ? { background: 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))' } : {}"
          >{{ cell.day }}</span>
          <span
            class="absolute bottom-0 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full"
            :style="{ background: 'var(--color-accent)' }"
            aria-hidden="true"
          />
        </button>
        <span
          v-else
          class="relative py-1.5 rounded-md text-muted"
          :aria-label="`${viewYear}年${viewMonth + 1}月${cell.day}日`"
        >
          <span
            class="inline-grid place-items-center w-6 h-6 rounded-full"
            :class="isCurrentMonth && cell.day === today.getDate() ? 'border border-app font-medium' : ''"
          >{{ cell.day }}</span>
        </span>
      </template>
    </div>
    <p class="mt-2 text-xs text-muted flex items-center gap-1.5">
      <span class="inline-block w-1 h-1 rounded-full" :style="{ background: 'var(--color-accent)' }" aria-hidden="true" />
      有日记的日期，点击筛选当天记录
    </p>
  </div>
</template>
