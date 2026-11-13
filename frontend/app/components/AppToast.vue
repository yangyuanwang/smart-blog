<script setup lang="ts">
// 全局 Toast 渲染层：顶部居中堆叠，滑入动画，成功/错误/信息三型，自动消失
const { toasts, dismiss } = useToast()

const ICONS: Record<string, string> = { success: '✓', error: '!', info: 'i' }
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed top-4 left-1/2 -translate-x-1/2 z-[60] flex flex-col items-center gap-2 pointer-events-none w-full max-w-md px-4"
      aria-live="polite"
    >
      <TransitionGroup
        enter-active-class="transition duration-300 ease-out"
        enter-from-class="opacity-0 -translate-y-3 scale-95"
        enter-to-class="opacity-100 translate-y-0 scale-100"
        leave-active-class="transition duration-200 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0 -translate-y-2"
      >
        <div
          v-for="item in toasts"
          :key="item.id"
          class="pointer-events-auto w-full flex items-center gap-3 rounded-xl border border-app bg-surface/95 backdrop-blur px-4 py-3 shadow-xl"
          :role="item.type === 'error' ? 'alert' : 'status'"
        >
          <span
            class="shrink-0 w-6 h-6 rounded-full grid place-items-center text-xs font-bold text-white"
            :style="{
              background: item.type === 'success'
                ? 'linear-gradient(120deg, var(--color-accent), var(--color-accent-2))'
                : item.type === 'error' ? 'var(--color-danger)' : 'var(--color-warn)'
            }"
            aria-hidden="true"
          >{{ ICONS[item.type] }}</span>
          <p class="flex-1 text-sm leading-snug">{{ item.message }}</p>
          <button
            type="button"
            class="shrink-0 w-6 h-6 grid place-items-center rounded-md text-muted hover:text-main hover:bg-surface-2 focus-visible:ring-2 ring-app transition-colors"
            aria-label="关闭提示"
            @click="dismiss(item.id)"
          >
            <span aria-hidden="true">×</span>
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
