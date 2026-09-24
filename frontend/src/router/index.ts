import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth, type UserRole } from '../composables/useAuth'
import AiConfigPage from '../components/AiConfigPage.vue'
import Dashboard from '../components/Dashboard.vue'
import DictionaryManagement from '../components/DictionaryManagement.vue'
import LoginPage from '../components/LoginPage.vue'
import NotificationCenter from '../components/NotificationCenter.vue'
import OverviewPage from '../components/OverviewPage.vue'
import RequirementForm from '../components/RequirementForm.vue'
import RequirementDetail from '../components/RequirementDetail.vue'
import RequirementList from '../components/RequirementList.vue'
import SystemManagement from '../components/SystemManagement.vue'
import UserManagement from '../components/UserManagement.vue'
import VersionRequirementManagement from '../components/VersionRequirementManagement.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: UserRole[]
    guestOnly?: boolean
    pageKey?: string
    title?: string
    description?: string
  }
}

const protectedPage = (pageKey: string, title: string, description: string, roles: UserRole[] = ['USER', 'HANDLER', 'ADMIN']) => ({
  requiresAuth: true,
  roles,
  pageKey,
  title,
  description,
})

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
    meta: { guestOnly: true },
  },
  {
    path: '/',
    name: 'dashboard',
    component: Dashboard,
    meta: protectedPage('dashboard', '待办工作台', '查看当前账号负责和协助处理的系统需求。', ['HANDLER', 'ADMIN']),
  },
  {
    path: '/requirements/new',
    name: 'requirement-create',
    component: RequirementForm,
    meta: protectedPage('create', '填写需求', '填写、暂存或正式保存系统需求。'),
  },
  {
    path: '/requirements',
    name: 'requirement-list',
    component: RequirementList,
    meta: protectedPage('list', '需求列表', '查看、筛选、编辑和删除全部需求。'),
  },
  {
    path: '/overview',
    name: 'requirement-overview',
    component: OverviewPage,
    meta: protectedPage('overview', '需求概览', '查看未完成需求分布，也可切换查看全部需求。', ['HANDLER', 'ADMIN']),
  },
  {
    path: '/requirements/:id(\\d+)',
    name: 'requirement-detail',
    component: RequirementDetail,
    meta: protectedPage('list', '需求详情', '查看需求详情、附件，并可直接编辑。'),
  },
  {
    path: '/notifications',
    name: 'notification-center',
    component: NotificationCenter,
    meta: protectedPage('notifications', '站内消息', '查看需求动态与待处理提醒。'),
  },
  {
    path: '/systems',
    name: 'system-management',
    component: SystemManagement,
    meta: protectedPage('systems', '系统与版本', '查看系统、负责人、协助人和版本信息。'),
  },
  {
    path: '/versions',
    name: 'version-management',
    redirect: { name: 'system-management' },
  },
  {
    path: '/versions/:versionId(\\d+)',
    name: 'version-requirement-management',
    component: VersionRequirementManagement,
    meta: protectedPage('systems', '版本需求管理', '集中绑定、迁移或解除当前版本的需求。', ['HANDLER', 'ADMIN']),
  },
  {
    path: '/admin/users',
    name: 'user-management',
    component: UserManagement,
    meta: protectedPage('users', '人员管理', '维护账号、重置密码和启停人员。', ['ADMIN']),
  },
  {
    path: '/admin/dictionaries',
    name: 'dictionary-management',
    component: DictionaryManagement,
    meta: protectedPage('dictionaries', '字典管理', '维护部门和需求类型，停用项保留历史记录。', ['ADMIN']),
  },
  {
    path: '/admin/ai-config',
    name: 'ai-config',
    component: AiConfigPage,
    meta: protectedPage('ai-config', 'AI 配置', '配置 AI 智能分析服务连接信息。', ['ADMIN']),
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: { name: 'dashboard' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const { initialize, isHandler, isLoggedIn, role } = useAuth()
  await initialize()

  const homeRoute = () => isHandler.value ? { name: 'dashboard' as const } : { name: 'requirement-list' as const }

  if (to.meta.guestOnly && isLoggedIn.value) return homeRoute()

  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return {
      name: 'login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
    }
  }

  if (to.meta.roles && (!role.value || !to.meta.roles.includes(role.value))) {
    return { name: 'requirement-list' }
  }

  return true
})

export default router
