import { Modal } from 'ant-design-vue'

/**
 * Modal.confirm 封装 composable，便于测试 mock。
 *
 * 用途：
 * - confirmDelete：业务级删除确认（跨实体、需异步），okType danger
 * - confirmLeave：离开页面/丢弃草稿等确认
 *
 * 使用方式：
 *   const { confirmDelete } = useConfirm()
 *   confirmDelete({
 *     title: '确认删除该需求？',
 *     content: '删除后不可恢复',
 *     onOk: async () => { await api.delete(...) },
 *   })
 *
 * 测试 mock：
 *   vi.mock('@/composables/useConfirm', () => ({
 *     useConfirm: () => ({
 *       confirmDelete: vi.fn(({ onOk }) => onOk()),
 *       confirmLeave: vi.fn(({ onOk }) => onOk()),
 *     }),
 *   }))
 */
type ConfirmDeleteOptions = {
  title: string
  content?: string
  onOk: () => void | Promise<void>
}

type ConfirmLeaveOptions = {
  title: string
  content: string
  onOk: () => void
}

export const useConfirm = () => {
  const confirmDelete = (opts: ConfirmDeleteOptions) =>
    Modal.confirm({
      title: opts.title,
      content: opts.content,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: opts.onOk,
    })

  const confirmLeave = (opts: ConfirmLeaveOptions) =>
    Modal.confirm({
      title: opts.title,
      content: opts.content,
      okText: '确定',
      cancelText: '取消',
      onOk: opts.onOk,
    })

  return { confirmDelete, confirmLeave }
}
