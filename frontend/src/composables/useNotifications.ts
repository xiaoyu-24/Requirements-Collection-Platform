import { ref } from 'vue'
import { api } from '../api'

const unreadCount = ref(0)

const refreshUnreadCount = async () => {
  try {
    const { data } = await api.get<{ count: number }>('/notifications/unread-count')
    unreadCount.value = Number.isFinite(data?.count) ? data.count : 0
  } catch {
    unreadCount.value = 0
  }
}

const clearUnreadCount = () => {
  unreadCount.value = 0
}

export const useNotifications = () => ({
  unreadCount,
  refreshUnreadCount,
  clearUnreadCount,
})
