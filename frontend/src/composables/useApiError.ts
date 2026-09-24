import { message } from 'ant-design-vue'

export type ApiFieldErrors = Record<string, string>

export const apiErrorDetails = (error: unknown) => {
  const response = (error as { response?: { status?: number; data?: { message?: string; fieldErrors?: ApiFieldErrors } } })?.response
  const data = response?.data
  return {
    status: response?.status,
    message: data?.message?.trim() || '',
    fieldErrors: data?.fieldErrors ?? {},
  }
}

/**
 * 统一 axios 错误处理 composable。
 *
 * 用途：消除各页面 catch 块中重复的 409/404/5xx 判断，统一友好文案。
 *
 * 使用方式：
 *   const { handleError } = useApiError()
 *   try { await api.put(...) }
 *   catch (error) { handleError(error, '保存失败') }
 *
 * 注意：本 composable 不修改 api.ts，错误处理在调用方收敛，
 * 避免改变现有"局部 message"语义。
 */
export const useApiError = () => {
  const handleError = (error: unknown, fallback = '操作失败，请重试') => {
    const { status, message: serverMessage } = apiErrorDetails(error)
    if (status === 409) {
      message.error(serverMessage || '数据已被其他人修改，请刷新后重试')
    } else if (status === 404) {
      message.error(serverMessage || '数据不存在或已被删除')
    } else if (status && status >= 500) {
      message.error(serverMessage || '服务异常，请稍后重试')
    } else {
      message.error(serverMessage || fallback)
    }
  }

  return { handleError }
}
