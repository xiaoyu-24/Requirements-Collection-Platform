import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { subscribeToRefresh, type RefreshTarget } from './refreshBus'
import { activatePageRefresh, deactivatePageRefresh, type PageRefreshHandle } from './pageRefreshShell'

type RefreshOptions = {
  isPaused?: () => boolean
}

export const usePageRefresh = (
  target: RefreshTarget,
  load: () => void | Promise<void>,
  options: RefreshOptions = {},
) => {
  let active = true
  let mounted = false
  const refreshing = ref(false)
  const loaded = ref(false)
  const lastUpdatedAt = ref<Date | null>(null)

  const refresh = async () => {
    if (!active || options.isPaused?.() || refreshing.value) return
    refreshing.value = true
    try {
      await load()
      loaded.value = true
      lastUpdatedAt.value = new Date()
    } catch {
      // 首次失败也退出骨架屏，页面保留空态或错误态供用户重试。
      loaded.value = true
      message.error('数据刷新失败，请稍后重试')
    } finally {
      refreshing.value = false
    }
  }

  const handle: PageRefreshHandle = { refreshing, loaded, lastUpdatedAt, refresh: () => { void refresh() } }

  const stopListening = subscribeToRefresh(target, () => { void refresh() })

  onMounted(() => {
    active = true
    mounted = true
    activatePageRefresh(handle)
    void refresh()
  })
  onActivated(() => {
    active = true
    activatePageRefresh(handle)
    if (mounted) void refresh()
  })
  onDeactivated(() => {
    active = false
    deactivatePageRefresh(handle)
  })
  onBeforeUnmount(() => {
    active = false
    deactivatePageRefresh(handle)
    stopListening()
  })

  return { refresh, refreshing, loaded, lastUpdatedAt }
}
