<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button, Card, Empty, Segmented, Tag, message } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { BellOutlined, CheckOutlined, ClockCircleOutlined, ExclamationCircleOutlined, FileTextOutlined, MessageOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { useNotifications } from '../composables/useNotifications'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'

type NotificationType = 'NEW_REQUIREMENT' | 'ASSIGNED' | 'STATUS_CHANGED' | 'PROGRESS_UPDATED' | 'OVERDUE' | 'STALE'
type NotificationItem = {
  id: number
  type: NotificationType
  requirementId: number | null
  title: string
  content: string
  read: boolean
  createdAt: string
}
type NotificationPage = {
  content: NotificationItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

const router = useRouter()
const { unreadCount, refreshUnreadCount } = useNotifications()
const items = ref<NotificationItem[]>([])
const loading = ref(false)
const markingAllRead = ref(false)
const filter = ref<'all' | 'unread'>('all')
const page = ref(0)
const pageSize = 20
const total = ref(0)

const filterOptions = [
  { label: '全部消息', value: 'all' },
  { label: '仅未读', value: 'unread' },
]

const unreadOnly = computed(() => filter.value === 'unread')
const typeMeta = (type: NotificationType) => ({
  NEW_REQUIREMENT: { label: '新需求', color: 'blue', icon: FileTextOutlined },
  ASSIGNED: { label: '待处理', color: 'purple', icon: BellOutlined },
  STATUS_CHANGED: { label: '状态更新', color: 'cyan', icon: SyncOutlined },
  PROGRESS_UPDATED: { label: '进度更新', color: 'green', icon: MessageOutlined },
  OVERDUE: { label: '已超期', color: 'red', icon: ExclamationCircleOutlined },
  STALE: { label: '无进展', color: 'orange', icon: ClockCircleOutlined },
}[type])

const formatDateTime = (value: string) => value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 16)

const loadNotifications = async () => {
  loading.value = true
  try {
    const { data } = await api.get<NotificationPage>('/notifications', {
      params: { page: page.value, size: pageSize, unreadOnly: unreadOnly.value },
    })
    items.value = Array.isArray(data.content) ? data.content : []
    total.value = data.totalElements ?? 0
  } catch (error) {
    throw error
  } finally {
    loading.value = false
  }
}

const changePage = (current: number) => {
  page.value = current - 1
  void refresh()
}

const openNotification = async (item: NotificationItem) => {
  if (!item.read) {
    try {
      await api.patch(`/notifications/${item.id}/read`)
      item.read = true
      await refreshUnreadCount()
    } catch {
      message.error('标记消息已读失败')
      return
    }
  }
  if (item.requirementId) {
    await router.push({ name: 'requirement-detail', params: { id: item.requirementId } })
  }
}

const markAllRead = async () => {
  markingAllRead.value = true
  try {
    await api.patch('/notifications/read-all')
    if (unreadOnly.value) {
      items.value = []
      total.value = 0
    } else {
      items.value.forEach((item) => { item.read = true })
    }
    await refreshUnreadCount()
    message.success('已将全部消息标为已读')
  } catch {
    message.error('全部标为已读失败')
  } finally {
    markingAllRead.value = false
  }
}

watch(filter, () => {
  page.value = 0
  void refresh()
})

const { loaded, refresh } = usePageRefresh('notifications', async () => {
  await loadNotifications()
  await refreshUnreadCount()
})
</script>

<template>
  <section class="notification-center" data-test="notification-center">
    <ContentSkeleton v-if="!loaded" preset="table" :rows="5" />
    <template v-else>
    <Card :bordered="false">
      <div class="notification-toolbar">
        <Segmented v-model:value="filter" :options="filterOptions" data-test="notification-filter" />
        <Button data-test="mark-all-notifications-read" :disabled="unreadCount === 0" :loading="markingAllRead" @click="markAllRead">
          <template #icon><CheckOutlined /></template>
          全部标为已读
        </Button>
      </div>

      <div v-if="items.length" class="notification-list" :class="{ 'is-loading': loading }">
        <button
          v-for="item in items"
          :key="item.id"
          class="notification-row"
          :class="{ 'is-unread': !item.read }"
          type="button"
          :data-test="`notification-${item.id}`"
          @click="openNotification(item)"
        >
          <span class="notification-indicator" aria-hidden="true">
            <span class="notification-icon"><component :is="typeMeta(item.type).icon" /></span>
            <span v-if="!item.read" class="notification-unread-dot"></span>
          </span>
          <span class="notification-main">
            <span class="notification-row-heading">
              <strong>{{ item.title }}</strong>
              <Tag :color="typeMeta(item.type).color">{{ typeMeta(item.type).label }}</Tag>
            </span>
            <span class="notification-content">{{ item.content }}</span>
          </span>
          <time class="notification-time">{{ formatDateTime(item.createdAt) }}</time>
        </button>
      </div>
      <Empty v-else-if="!loading" :description="unreadOnly ? '暂无未读消息' : '暂无站内消息'" />

      <div v-if="total > pageSize" class="notification-pagination">
        <AppPagination :current="page + 1" :page-size="pageSize" :total="total" :show-size-changer="false" @change="changePage" />
      </div>
    </Card>
    </template>
  </section>
</template>

<style scoped>
.notification-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.notification-list {
  border-top: 1px solid #f0f0f0;
}

.notification-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  width: 100%;
  gap: 12px;
  padding: 16px 8px;
  border: 0;
  border-bottom: 1px solid #f0f0f0;
  background: #ffffff;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.notification-row:hover { background: #fafafa; }
.notification-row.is-unread { background: #f6faff; }

.notification-indicator { position: relative; display: grid; place-items: center; width: 28px; height: 28px; }
.notification-icon { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 4px; background: #e6f4ff; color: #1677ff; font-size: 15px; }
.notification-unread-dot { position: absolute; top: 1px; right: 0; width: 7px; height: 7px; border: 1px solid #ffffff; border-radius: 50%; background: #ff4d4f; }

.notification-main { display: grid; min-width: 0; gap: 6px; }
.notification-row-heading { display: flex; align-items: center; min-width: 0; gap: 8px; }
.notification-row-heading strong { overflow: hidden; color: rgba(0, 0, 0, 0.88); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.notification-content { overflow: hidden; color: rgba(0, 0, 0, 0.65); font-size: 13px; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.notification-time { align-self: start; color: rgba(0, 0, 0, 0.45); font-size: 12px; white-space: nowrap; }
.notification-pagination { display: flex; justify-content: flex-end; margin-top: 16px; }

@media (max-width: 768px) {
  .notification-toolbar { align-items: flex-start; flex-direction: column; }
  .notification-row { grid-template-columns: 28px minmax(0, 1fr); }
  .notification-time { grid-column: 2; }
}
</style>
