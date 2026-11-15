<script setup lang="ts">
// 日记时间线 + 月历时间筛选：点有记录的日期查看当天公开日记，再点一次取消
interface DiaryItem {
  id: number
  title: string
  content: string
  mood?: string
  createTime: string
}

const { data, error, pending } = await useAsyncData('diaries', () => api<DiaryItem[]>('/diaries'))

const moodColor: Record<string, string> = {
  随记: 'var(--color-accent)',
  开工: 'var(--color-ok)',
  想走: 'var(--color-accent-2)',
  读完: 'var(--color-warn)',
  高兴: 'var(--color-danger)',
  发呆: 'var(--text-muted)'
}

function fmtDate (value: string) {
  return value?.slice(0, 10) ?? ''
}

// 日历标记：有日记的日期 → 篇数
const markedDates = computed(() => {
  const map: Record<string, number> = {}
  for (const diary of data.value ?? []) {
    const day = fmtDate(diary.createTime)
    if (day) {
      map[day] = (map[day] ?? 0) + 1
    }
  }
  return map
})

// 选中日期筛选当天日记
const selectedDay = ref<string | null>(null)
const filtered = computed(() => {
  const list = data.value ?? []
  if (!selectedDay.value) {
    return list
  }
  return list.filter(diary => fmtDate(diary.createTime) === selectedDay.value)
})

// 日历默认定位到最近有日记的月份（当前月没有记录时不至于开屏空白）
const latestDiaryMonth = computed(() => {
  const days = Object.keys(markedDates.value).sort()
  return days.length ? days[days.length - 1].slice(0, 7) : undefined
})

const selectedCount = computed(() => (selectedDay.value ? markedDates.value[selectedDay.value] ?? 0 : 0))

useSeoMeta({ title: '日记', description: '日常记录与随笔时间线' })
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <h1 class="font-serif text-3xl font-bold">日记</h1>
    <p class="mt-2 text-sm text-muted">时间线上的日常与随笔</p>

    <!-- 月历筛选器 -->
    <div class="mt-6 max-w-sm">
      <CalendarCard
        :marked="markedDates"
        :selected="selectedDay"
        :focus-date="latestDiaryMonth"
        @select="selectedDay = $event"
      />
    </div>

    <!-- 当前筛选 -->
    <div v-if="selectedDay" class="mt-4 flex items-center gap-2 text-sm text-muted">
      <span class="px-2 py-1 rounded-md bg-surface-2">{{ selectedDay }} · {{ selectedCount }} 篇</span>
      <button
        type="button"
        class="underline hover:text-main focus-visible:ring-2 ring-app rounded"
        @click="selectedDay = null"
      >清除筛选</button>
    </div>

    <p v-if="pending" class="mt-8 text-muted" role="status">加载中…</p>
    <div v-else-if="error" class="mt-8 rounded-xl border border-app bg-surface px-4 py-10 text-center text-muted" role="alert">
      日记加载失败，请稍后重试
    </div>
    <div v-else-if="!data?.length" class="mt-8 rounded-xl border border-app bg-surface px-4 py-12 text-center text-muted">
      还没有日记
    </div>
    <div v-else-if="!filtered.length" class="mt-8 rounded-xl border border-app bg-surface px-4 py-12 text-center text-muted">
      这一天没有公开日记
    </div>
    <ol v-else class="mt-8 relative border-l border-app ml-3 space-y-8">
      <li v-for="diary in filtered" :key="diary.id" class="pl-6 relative">
        <span
          class="absolute -left-[7px] top-1.5 w-3.5 h-3.5 rounded-full border-2 border-app"
          :style="{ background: moodColor[diary.mood ?? ''] ?? 'var(--color-accent)' }"
          aria-hidden="true"
        />
        <div class="flex flex-wrap items-center gap-2">
          <time class="text-xs text-muted">{{ fmtDate(diary.createTime) }}</time>
          <span
            v-if="diary.mood"
            class="text-xs px-2 py-0.5 rounded-full border border-app"
            :style="{ color: moodColor[diary.mood] ?? 'var(--text-muted)' }"
          >{{ diary.mood }}</span>
        </div>
        <h2 class="mt-1 font-serif text-lg font-semibold">{{ diary.title }}</h2>
        <p class="mt-1 text-sm text-muted whitespace-pre-wrap">{{ diary.content }}</p>
      </li>
    </ol>
  </div>
</template>
