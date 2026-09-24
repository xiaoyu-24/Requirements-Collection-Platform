import { computed, ref } from 'vue'
import { api } from '../api'

export type UserRole = 'USER' | 'HANDLER' | 'ADMIN'

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  departmentId: number | null
  department: string | null
  role: UserRole
  /** 兼容旧后端滚动重启期间的响应，迁移完成后不再由新接口返回。 */
  admin?: boolean
  disabled: boolean
  mustChangePassword: boolean
}

const currentUser = ref<CurrentUser | null>(null)
const initializing = ref(true)
let initializationPromise: Promise<void> | null = null

const normalizeUser = (data: CurrentUser): CurrentUser => ({
  ...data,
  role: data.role ?? (data.admin ? 'ADMIN' : 'HANDLER'),
})

/**
 * 全局登录状态。
 *
 * 令牌本身保存在 HttpOnly Cookie 里，前端读不到也不需要读；
 * 这里只保存"当前是谁"，用于菜单显示、表单带出姓名部门等。
 */
export const useAuth = () => {
  const isLoggedIn = computed(() => currentUser.value !== null)
  const role = computed<UserRole | null>(() => currentUser.value?.role ?? null)
  const isHandler = computed(() => role.value === 'HANDLER' || role.value === 'ADMIN')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const mustChangePassword = computed(() => currentUser.value?.mustChangePassword === true)

  /** 应用启动时调用一次，确认浏览器里的 Cookie 是否仍然有效。 */
  const initialize = async () => {
    if (initializationPromise) return initializationPromise
    initializing.value = true
    initializationPromise = (async () => {
      try {
        const { data } = await api.get<CurrentUser>('/auth/current-user')
        currentUser.value = normalizeUser(data)
      } catch {
        currentUser.value = null
      } finally {
        initializing.value = false
      }
    })()
    return initializationPromise
  }

  const login = async (username: string, password: string) => {
    const { data } = await api.post<CurrentUser>('/auth/login', { username, password })
    currentUser.value = normalizeUser(data)
    return currentUser.value
  }

  const logout = async () => {
    try {
      await api.post('/auth/logout')
    } finally {
      currentUser.value = null
      initializationPromise = null
    }
  }

  /** 改密后服务端会清除全部会话，因此必须重新登录。 */
  const changePassword = async (currentPassword: string, newPassword: string) => {
    await api.post('/auth/change-password', { currentPassword, newPassword })
    currentUser.value = null
    initializationPromise = null
  }

  /** 令牌失效时由 axios 拦截器调用，直接把状态置空回到登录页。 */
  const clearSession = () => {
    currentUser.value = null
    initializationPromise = null
  }

  return {
    currentUser,
    initializing,
    isLoggedIn,
    role,
    isHandler,
    isAdmin,
    mustChangePassword,
    initialize,
    login,
    logout,
    changePassword,
    clearSession,
  }
}
