// 全局 Toast 通知：useState 跨路由持久，Teleport 到 body 统一渲染
// 用法: const toast = useToast(); toast.success('已保存'); toast.error('失败原因')
export interface ToastItem {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

let counter = 0

export function useToast () {
  const toasts = useState<ToastItem[]>('app-toasts', () => [])

  function push (type: ToastItem['type'], message: string, duration = 3800) {
    if (!message) {
      return
    }
    const id = ++counter
    toasts.value.push({ id, type, message })
    // 最多同时 4 条，旧的先移除
    while (toasts.value.length > 4) {
      toasts.value.shift()
    }
    setTimeout(() => dismiss(id), duration)
  }

  function dismiss (id: number) {
    toasts.value = toasts.value.filter(item => item.id !== id)
  }

  return {
    toasts,
    dismiss,
    success: (message: string) => push('success', message),
    error: (message: string) => push('error', message, 4600),
    info: (message: string) => push('info', message)
  }
}
