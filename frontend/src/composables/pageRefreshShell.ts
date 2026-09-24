import { computed, shallowRef, type Ref } from 'vue'

export type PageRefreshHandle = {
  refreshing: Ref<boolean>
  loaded: Ref<boolean>
  lastUpdatedAt: Ref<Date | null>
  refresh: () => void
}

// 保留页面句柄中的 Ref，避免普通 ref 深度解包后丢失状态引用。
// 嵌套页面（如需求详情中的编辑器）卸载后，恢复父页面的刷新入口。
const pageRefreshHandles = shallowRef<PageRefreshHandle[]>([])
const activePageRefresh = computed(() => pageRefreshHandles.value.at(-1) ?? null)

export const usePageRefreshShell = () => ({ activePageRefresh })

export const activatePageRefresh = (handle: PageRefreshHandle) => {
  pageRefreshHandles.value = [...pageRefreshHandles.value.filter((item) => item !== handle), handle]
}

export const deactivatePageRefresh = (handle: PageRefreshHandle) => {
  pageRefreshHandles.value = pageRefreshHandles.value.filter((item) => item !== handle)
}
