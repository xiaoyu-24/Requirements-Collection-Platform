<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Badge, Button, ConfigProvider, Input, Layout, LayoutContent, LayoutHeader, LayoutSider, Menu, MenuItem, Modal, Spin, message } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { useRoute, useRouter } from 'vue-router'

dayjs.locale('zh-cn')
import { AppstoreOutlined, BellOutlined, DashboardOutlined, DatabaseOutlined, FileTextOutlined, LogoutOutlined, PieChartOutlined, PlusOutlined, RobotOutlined, SettingOutlined, TeamOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import { themeConfig } from './theme'
import { onUnauthorized } from './api'
import { useAuth, type UserRole } from './composables/useAuth'
import { useCreateFormState } from './composables/useCreateFormState'
import { useNotifications } from './composables/useNotifications'
import LoginPage from './components/LoginPage.vue'
import PageContainer from './components/PageContainer.vue'

type PageKey = 'dashboard' | 'create' | 'list' | 'overview' | 'systems' | 'ai-config' | 'users' | 'dictionaries'

interface PageDefinition {
  key: PageKey
  routeName: string
  label: string
  description: string
  /** 前端只负责可见性；路由守卫和后端接口才是权限防线。 */
  roles: UserRole[]
}

const pages: PageDefinition[] = [
  { key: 'dashboard', routeName: 'dashboard', label: '待办工作台', description: '查看当前账号负责和协助处理的系统需求。', roles: ['HANDLER', 'ADMIN'] },
  { key: 'create', routeName: 'requirement-create', label: '填写需求', description: '填写、暂存或正式保存系统需求。', roles: ['USER', 'HANDLER', 'ADMIN'] },
  { key: 'list', routeName: 'requirement-list', label: '需求列表', description: '查看、筛选、编辑和删除全部需求。', roles: ['USER', 'HANDLER', 'ADMIN'] },
  { key: 'overview', routeName: 'requirement-overview', label: '需求概览', description: '查看未完成需求分布，也可切换查看全部需求。', roles: ['HANDLER', 'ADMIN'] },
  { key: 'systems', routeName: 'system-management', label: '系统与版本', description: '查看系统负责人、协助人及其版本信息。', roles: ['USER', 'HANDLER', 'ADMIN'] },
  { key: 'users', routeName: 'user-management', label: '人员管理', description: '维护账号、角色、重置密码和启停人员。', roles: ['ADMIN'] },
  { key: 'dictionaries', routeName: 'dictionary-management', label: '字典管理', description: '维护部门和需求类型，停用项保留历史记录。', roles: ['ADMIN'] },
  { key: 'ai-config', routeName: 'ai-config', label: 'AI 配置', description: '配置 AI 智能分析服务连接信息。', roles: ['ADMIN'] },
]

const router = useRouter()
const route = useRoute()
const { currentUser, initializing, isLoggedIn, logout, clearSession, role } = useAuth()
const { createFormDirty, setCreateFormDirty } = useCreateFormState()
const { unreadCount, refreshUnreadCount, clearUnreadCount } = useNotifications()
const globalKeyword = ref('')
const detailCanEdit = ref(false)

const visiblePages = computed(() => pages.filter((page) => role.value !== null && page.roles.includes(role.value)))
const activePage = computed<PageKey>(() => {
  const pageKey = route.meta.pageKey
  return pages.some((page) => page.key === pageKey) ? pageKey as PageKey : 'dashboard'
})
const active = computed(() => visiblePages.value.find((page) => page.key === activePage.value) ?? visiblePages.value[0] ?? pages[0])
const pageTitle = computed(() => route.name === 'user-management'
  ? ''
  : route.name === 'requirement-detail' && route.query.edit === '1'
  ? ''
  : typeof route.meta.title === 'string' ? route.meta.title : active.value.label)
const pageTitleTag = computed(() => route.name === 'requirement-detail' && route.query.edit === '1'
  ? ''
  : '')
const pageDescription = computed(() => route.name === 'user-management'
  ? ''
  : route.name === 'requirement-detail' && route.query.edit === '1'
  ? ''
  : typeof route.meta.description === 'string' ? route.meta.description : active.value.description)
const cachedViews = ['RequirementList', 'SystemManagement']

// 浏览器前进/后退和所有菜单跳转共用这一层离开确认，不让未保存内容被路由切换覆盖。
const confirmUnsavedLeave = () => new Promise<boolean>((resolve) => {
  Modal.confirm({
    title: '内容尚未保存',
    content: '当前填写内容尚未保存，确定离开当前页面吗？',
    okText: '离开页面',
    cancelText: '继续编辑',
    onOk: () => resolve(true),
    onCancel: () => resolve(false),
  })
})

const removeCreateFormGuard = router.beforeEach(async (to, from) => {
  if (from.name === 'requirement-create' && to.name !== 'requirement-create' && createFormDirty.value) {
    return await confirmUnsavedLeave()
  }
  return true
})

let notificationRefreshTimer: ReturnType<typeof window.setInterval> | undefined

const stopNotificationRefresh = () => {
  if (notificationRefreshTimer !== undefined) {
    window.clearInterval(notificationRefreshTimer)
    notificationRefreshTimer = undefined
  }
}

watch(isLoggedIn, (loggedIn) => {
  stopNotificationRefresh()
  if (!loggedIn) {
    clearUnreadCount()
    return
  }
  void refreshUnreadCount()
  notificationRefreshTimer = window.setInterval(() => void refreshUnreadCount(), 60_000)
}, { immediate: true })

onBeforeUnmount(() => {
  removeCreateFormGuard()
  stopNotificationRefresh()
})

// 令牌失效时回到登录页，同时把未保存标记清掉，避免离开确认框阻断安全跳转。
onUnauthorized(() => {
  if (!isLoggedIn.value) return
  const redirect = route.meta.requiresAuth ? route.fullPath : undefined
  clearSession()
  setCreateFormDirty(false)
  message.warning('登录状态已失效，请重新登录')
  void router.replace({ name: 'login', query: redirect ? { redirect } : undefined })
})

const handleLogout = async () => {
  if (createFormDirty.value && !(await confirmUnsavedLeave())) return
  setCreateFormDirty(false)
  await logout()
  await router.replace({ name: 'login' })
}

const handleMenuClick = ({ key }: { key: string | number }) => {
  const page = visiblePages.value.find((item) => item.key === key)
  if (page) void router.push({ name: page.routeName })
}

const openNotificationCenter = () => {
  void router.push({ name: 'notification-center' })
}

const handleRequirementEditabilityChange = (canEdit: boolean) => {
  detailCanEdit.value = canEdit
}

watch(() => [route.name, route.params.id], () => {
  detailCanEdit.value = false
})

const roleLabel = computed(() => ({
  USER: '普通用户',
  HANDLER: '需求处理员',
  ADMIN: '管理员',
}[role.value ?? 'USER']))

const searchRequirements = () => {
  const keyword = globalKeyword.value.trim()
  if (!keyword) return
  void router.push({ name: 'requirement-list', query: { keyword } })
}
</script>

<template>
  <ConfigProvider :theme="themeConfig" :locale="zhCN">
    <div v-if="initializing" class="app-loading">
      <Spin size="large" tip="正在加载…" />
    </div>
    <template v-else>
      <!-- 登录组件保持挂载，登录成功切换到主界面时可保留首次改密所需的初始密码。 -->
      <LoginPage />
      <Layout v-if="isLoggedIn" class="app-shell ant-app-shell" data-test="ant-layout">
        <LayoutHeader class="pro-header ant-layout-header">
        <div class="brand ant-brand">
          <AppstoreOutlined class="brand-icon" />
          <span class="brand-title">需求收集平台</span>
        </div>
        <div class="app-global-search">
          <Input.Search v-model:value="globalKeyword" allow-clear placeholder="搜索需求" @search="searchRequirements" />
        </div>
        <div class="app-user-box">
          <span class="app-user-name" data-test="current-user">{{ currentUser?.displayName }}</span>
          <span class="app-user-role">{{ roleLabel }}</span>
          <Button type="text" class="notification-bell" data-test="notification-bell" title="站内消息" aria-label="站内消息" @click="openNotificationCenter">
            <Badge :count="unreadCount" :overflow-count="99" :show-zero="false">
              <BellOutlined />
            </Badge>
          </Button>
          <Button type="text" data-test="logout" @click="handleLogout">
            <template #icon><LogoutOutlined /></template>
            退出
          </Button>
        </div>
        </LayoutHeader>
        <Layout class="pro-body-layout">
        <LayoutSider class="sidebar ant-sidebar" :width="200" theme="light">
          <nav aria-label="主菜单">
            <Menu theme="light" mode="inline" :selected-keys="[activePage]" @click="handleMenuClick">
              <MenuItem v-for="page in visiblePages" :key="page.key" :data-test="`nav-${page.key}`">
                <template #icon>
                  <DashboardOutlined v-if="page.key === 'dashboard'" />
                  <FileTextOutlined v-else-if="page.key === 'create'" />
                  <UnorderedListOutlined v-else-if="page.key === 'list'" />
                  <PieChartOutlined v-else-if="page.key === 'overview'" />
                  <SettingOutlined v-else-if="page.key === 'systems'" />
                  <TeamOutlined v-else-if="page.key === 'users'" />
                  <DatabaseOutlined v-else-if="page.key === 'dictionaries'" />
                  <RobotOutlined v-else />
                </template>
                {{ page.label }}
              </MenuItem>
            </Menu>
          </nav>
        </LayoutSider>
        <LayoutContent class="main-content ant-main-content" :class="{ 'requirements-list-content': route.name === 'requirement-list' }">
           <PageContainer :class="{ 'requirement-list-container': route.name === 'requirement-list' }" :title="pageTitle" :title-tag="pageTitleTag" :description="pageDescription">
            <template v-if="route.name === 'requirement-detail' && route.query.edit !== '1'" #extra>
              <div class="requirement-page-actions">
                <Button @click="router.push({ name: 'requirement-list' })">返回列表</Button>
                <Button v-if="detailCanEdit" type="primary" data-test="edit-from-detail" @click="router.replace({ name: 'requirement-detail', params: route.params, query: { ...route.query, edit: '1' } })">编辑需求</Button>
              </div>
            </template>
            <template v-else-if="route.name === 'requirement-list'" #extra>
               <Button type="primary" data-test="create-requirement" @click="router.push({ name: 'requirement-create' })"><template #icon><PlusOutlined /></template>填写需求</Button>
            </template>
            <RouterView v-slot="{ Component }">
              <KeepAlive :include="cachedViews">
                <component :is="Component" @requirement-editability-change="handleRequirementEditabilityChange" />
              </KeepAlive>
            </RouterView>
          </PageContainer>
        </LayoutContent>
        </Layout>
      </Layout>
    </template>
  </ConfigProvider>
</template>
