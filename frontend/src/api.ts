import axios from 'axios'

export const api = axios.create({ baseURL: '/api' })

/**
 * 401 统一处理：令牌失效或被停用时，通知应用回到登录页。
 *
 * 登录令牌保存在 HttpOnly Cookie 中，由浏览器自动携带，因此这里不需要附加请求头。
 * 附件预览和下载走 img/iframe/a 标签，也是靠同一个 Cookie 通过鉴权。
 */
const unauthorizedHandlers = new Set<() => void>()

export const onUnauthorized = (handler: () => void) => {
  unauthorizedHandlers.add(handler)
  return () => unauthorizedHandlers.delete(handler)
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    const url: string = error?.config?.url ?? ''
    // 登录接口自身返回的 401 是"账号密码不正确"，应由登录页就地提示，不触发跳转。
    if (status === 401 && !url.includes('/auth/login')) {
      unauthorizedHandlers.forEach((handler) => handler())
    }
    return Promise.reject(error)
  },
)
