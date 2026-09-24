<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Button, Card, Empty, Input, Modal, Select, SelectOption, Table, Tag, message } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { CheckCircleOutlined, EditOutlined, LockOutlined, PlusOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { useDictionaryOptions, type DictionaryItem } from '../composables/useDictionaryOptions'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'
import type { UserRole } from '../composables/useAuth'

interface User {
  id: number
  username: string
  displayName: string
  departmentId: number | null
  department: string | null
  role: UserRole
  disabled: boolean
  mustChangePassword: boolean
}

const roleMeta: Record<UserRole, { label: string; color: string; className: string }> = {
  USER: { label: '普通用户', color: 'default', className: 'normal' },
  HANDLER: { label: '需求处理员', color: 'orange', className: 'handler' },
  ADMIN: { label: '管理员', color: 'blue', className: 'admin' },
}

const users = ref<User[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const modalTitle = ref('新增人员')
const editingId = ref<number | null>(null)
const editingUser = ref<User | null>(null)
const saving = ref(false)
const search = ref('')
const roleFilter = ref<UserRole | ''>('')
const statusFilter = ref<'ACTIVE' | 'DISABLED' | ''>('')
const departmentFilter = ref<number | 'UNASSIGNED' | ''>('')
const allDepartments = ref<DictionaryItem[]>([])
const currentPage = ref(1)
const pageSize = 10

const form = reactive({
  username: '',
  displayName: '',
  departmentId: '',
  role: 'USER' as UserRole,
})

const { departments, loadDictionaryOptions } = useDictionaryOptions()
const departmentFilterOptions = computed(() => [
  { label: '全部部门', value: '' },
  { label: '未设置部门', value: 'UNASSIGNED' },
  ...allDepartments.value.map((department) => ({
    label: department.disabled ? `${department.name}（已停用）` : department.name,
    value: department.id,
  })),
])

const columns = [
  { title: '人员', key: 'person', width: 220 },
  { title: '部门', dataIndex: 'department', key: 'department', width: 160 },
  { title: '角色', key: 'role', width: 140 },
  { title: '状态', key: 'status', width: 120 },
  { title: '操作', key: 'action', width: 270 },
]

const getRoleMeta = (value: { role?: unknown }) => roleMeta[value.role as UserRole] ?? roleMeta.USER
const tableUser = (value: Record<string, unknown>) => value as unknown as User

const filteredUsers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return users.value.filter((user) => {
    const matchKeyword = !keyword || user.username.toLowerCase().includes(keyword) || user.displayName.toLowerCase().includes(keyword)
    const matchRole = !roleFilter.value || user.role === roleFilter.value
    const matchStatus = !statusFilter.value || (statusFilter.value === 'DISABLED' ? user.disabled : !user.disabled)
    const matchDepartment = departmentFilter.value === ''
      || (departmentFilter.value === 'UNASSIGNED' ? user.departmentId === null : user.departmentId === departmentFilter.value)
    return matchKeyword && matchRole && matchStatus && matchDepartment
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize)))
const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredUsers.value.slice(start, start + pageSize)
})

watch([search, roleFilter, statusFilter, departmentFilter], () => { currentPage.value = 1 })
watch(() => filteredUsers.value.length, () => {
  currentPage.value = Math.min(currentPage.value, totalPages.value)
})

const roleHint = computed(() => {
  if (form.role === 'USER') return '普通用户只能填写和跟进自己创建的需求。'
  if (form.role === 'HANDLER') return '需求处理员可以处理全部需求，并担任系统负责人或协助人。'
  return '管理员拥有需求处理员全部权限，并可管理人员、字典和 AI 配置。'
})

const roleChangeWarning = computed(() => {
  if (!editingUser.value || editingUser.value.role === form.role) return ''
  if (form.role === 'USER') return '降级为普通用户后，将不能访问处理工作台，但仍可查看自己的需求和站内通知；若仍绑定系统负责人或协助人，保存会被拦截。'
  if (editingUser.value.role === 'USER') return '升级后可查看、编辑全部需求，并可被设置为系统负责人或协助人。'
  return '角色变更会立即影响该账号下次请求的菜单与接口权限。'
})

const loadUsers = async () => {
  loading.value = true
  try {
    const { data } = await api.get<User[]>('/users')
    users.value = Array.isArray(data) ? data : []
  } catch {
    throw new Error('加载人员列表失败')
  } finally {
    loading.value = false
  }
}

const loadAllDepartments = async () => {
  const { data } = await api.get<DictionaryItem[]>('/dictionaries', { params: { category: 'DEPARTMENT' } })
  allDepartments.value = Array.isArray(data) ? data : []
}

const loadUsersAndDictionaries = async () => {
  await Promise.all([loadUsers(), loadDictionaryOptions(), loadAllDepartments()])
}
const { loaded, refresh, refreshing, lastUpdatedAt } = usePageRefresh('users', loadUsersAndDictionaries)
const lastUpdatedLabel = computed(() => lastUpdatedAt.value
  ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(lastUpdatedAt.value)
  : '')

const resetForm = () => {
  form.username = ''
  form.displayName = ''
  form.departmentId = ''
  form.role = 'USER'
}

const openCreateModal = () => {
  modalTitle.value = '新增人员'
  editingId.value = null
  editingUser.value = null
  resetForm()
  modalOpen.value = true
}

const openEditModal = (user: User) => {
  modalTitle.value = '编辑人员'
  editingId.value = user.id
  editingUser.value = user
  form.username = user.username
  form.displayName = user.displayName
  form.departmentId = user.departmentId == null ? '' : String(user.departmentId)
  form.role = user.role
  modalOpen.value = true
}

const handleSave = async () => {
  if (!form.username.trim() || !form.displayName.trim()) {
    message.warning('账号和姓名为必填项')
    return
  }
  saving.value = true
  const creatingUser = editingId.value === null
  try {
    const payload = {
      username: form.username.trim(),
      displayName: form.displayName.trim(),
      departmentId: form.departmentId ? Number(form.departmentId) : null,
      role: form.role,
    }
    if (creatingUser) {
      await api.post('/users', payload)
      modalOpen.value = false
      Modal.success({
        title: '用户已创建',
        content: `${form.displayName.trim()} 的账号已创建，初始密码为 888888。首次登录时需要修改密码。`,
        okText: '知道了',
      })
    } else {
      await api.put(`/users/${editingId.value}`, payload)
      modalOpen.value = false
      message.success('人员信息已更新')
    }
    await refresh()
    if (creatingUser) {
      const createdIndex = filteredUsers.value.findIndex((user) => user.username === payload.username)
      currentPage.value = createdIndex >= 0 ? Math.floor(createdIndex / pageSize) + 1 : totalPages.value
    }
  } catch (error: unknown) {
    const data = (error as { response?: { data?: { message?: string } } })?.response?.data
    const msg = data?.message && data.message.trim().length > 0 ? data.message : '保存人员失败'
    message.error(msg)
  } finally {
    saving.value = false
  }
}

const handleResetPassword = async (userId: number, displayName: string) => {
  Modal.confirm({
    title: '重置密码',
    content: `确定要重置 ${displayName} 的密码吗？重置后该用户此前的登录会全部失效。`,
    okText: '确定重置',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.post(`/users/${userId}/reset-password`)
        Modal.success({
          title: '密码已重置',
          content: `${displayName} 的初始密码已重置为 888888。首次登录时需要修改密码。`,
          okText: '知道了',
        })
        await refresh()
      } catch {
        message.error('重置密码失败')
      }
    },
  })
}

const handleToggleDisabled = async (user: Pick<User, 'id' | 'disabled'>) => {
  const nextDisabled = !user.disabled
  const actionText = nextDisabled ? '停用' : '启用'
  try {
    await api.patch(`/users/${user.id}/disabled`, { disabled: nextDisabled })
    message.success(`已${actionText}该账号`)
    await refresh()
  } catch (error: unknown) {
    const data = (error as { response?: { data?: { message?: string } } })?.response?.data
    message.error(data?.message?.trim() || `${actionText}失败`)
  }
}
</script>

<template>
  <section class="users-page" data-test="user-management">
    <ContentSkeleton v-if="!loaded" preset="table" :rows="5" />
    <template v-else>
    <div class="users-toolbar-head">
      <div>
        <h2 class="users-page-title">人员管理</h2>
        <p class="users-page-subtitle">维护账号与角色；仅需求处理员和管理员可担任系统负责人</p>
      </div>
      <div class="users-toolbar-actions">
        <span v-if="lastUpdatedLabel" class="users-updated-at">更新于 {{ lastUpdatedLabel }}</span>
        <Button class="users-refresh-button" :loading="refreshing" title="刷新数据" aria-label="刷新数据" @click="refresh">
          <template #icon><ReloadOutlined /></template>
        </Button>
        <Button type="primary" @click="openCreateModal"><template #icon><PlusOutlined /></template>新增人员</Button>
      </div>
    </div>

    <Card class="users-surface" :bordered="false">
      <template #title><span class="panel-title">人员列表 <span class="panel-count">共 {{ users.length }} 人</span></span></template>
      <template #extra><span class="panel-filter-count">当前显示 {{ filteredUsers.length }} 人</span></template>
      <div class="users-filter-bar">
        <Input v-model:value="search" class="search-input" allow-clear placeholder="按账号 / 姓名筛选" />
        <Select v-model:value="roleFilter" class="role-filter" allow-clear placeholder="全部角色" :options="[{ label: '全部角色', value: '' }, ...Object.entries(roleMeta).map(([value, meta]) => ({ label: meta.label, value }))]" />
        <Select v-model:value="statusFilter" class="status-filter" allow-clear placeholder="全部状态" :options="[{ label: '全部状态', value: '' }, { label: '正常', value: 'ACTIVE' }, { label: '已停用', value: 'DISABLED' }]" />
        <Select v-model:value="departmentFilter" class="department-filter" placeholder="全部部门" :options="departmentFilterOptions" show-search option-filter-prop="label" />
      </div>
      <Table :columns="columns" :data-source="pagedUsers" row-key="id" :pagination="false" :scroll="{ x: 900 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'person'">
            <div class="person-cell"><span class="person-avatar">{{ record.displayName.slice(0, 1) }}</span><span><strong>{{ record.displayName }}</strong><small>{{ record.username }}</small></span></div>
          </template>
          <template v-else-if="column.key === 'role'"><Tag class="role-tag" :class="getRoleMeta(record).className" :color="getRoleMeta(record).color"><span class="tag-dot"></span>{{ getRoleMeta(record).label }}</Tag></template>
          <template v-else-if="column.key === 'status'">
            <Tag v-if="record.disabled" color="default"><StopOutlined /> 已停用</Tag>
            <Tag v-else-if="record.mustChangePassword" color="orange">需改密</Tag>
            <Tag v-else color="success"><CheckCircleOutlined /> 正常</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Button type="link" size="small" @click="openEditModal(tableUser(record))"><template #icon><EditOutlined /></template>编辑</Button>
            <Button type="link" size="small" @click="handleResetPassword(record.id, record.displayName)"><template #icon><LockOutlined /></template>重置密码</Button>
            <Button type="link" size="small" :danger="!record.disabled" @click="handleToggleDisabled({ id: record.id, disabled: record.disabled })">{{ record.disabled ? '启用' : '停用' }}</Button>
          </template>
        </template>
        <template #emptyText><Empty description="暂无匹配人员" /></template>
      </Table>
      <div v-if="filteredUsers.length" class="pagination-row">
        <span>共 {{ filteredUsers.length }} 人</span>
        <AppPagination v-model:current="currentPage" :page-size="pageSize" :total="filteredUsers.length" :show-size-changer="false" :show-less-items="true" responsive />
      </div>
    </Card>

    <Modal v-model:open="modalOpen" :title="modalTitle" :confirm-loading="saving" width="560px" @ok="handleSave">
      <div class="user-form">
        <div class="form-field"><label><span class="required-mark">*</span>账号</label><Input v-model:value="form.username" :disabled="editingId !== null" placeholder="用于登录，创建后不可修改" autocomplete="off" /></div>
        <div class="form-field"><label><span class="required-mark">*</span>姓名</label><Input v-model:value="form.displayName" placeholder="请输入真实姓名" /></div>
        <div class="form-field"><label>部门</label><Select v-model:value="form.departmentId" class="department-select" :list-height="128" :list-item-height="32" popup-class-name="user-department-select-popup" placeholder="请选择部门" allow-clear><SelectOption v-for="department in departments" :key="department.id" :value="String(department.id)">{{ department.name }}</SelectOption></Select></div>
        <div class="form-field"><label><span class="required-mark">*</span>角色</label><div class="role-picker"><button v-for="role in (['USER', 'HANDLER', 'ADMIN'] as UserRole[])" :key="role" type="button" class="role-option" :class="{ selected: form.role === role }" @click="form.role = role"><span class="role-option-name"><span class="radio"></span>{{ roleMeta[role].label }}</span><span class="role-option-desc">{{ role === 'USER' ? '仅填写和跟进自己的需求' : role === 'HANDLER' ? '处理全部需求，可任系统负责人' : '含人员、字典、AI 配置管理' }}</span></button></div><p class="role-hint">{{ roleChangeWarning || roleHint }}</p></div>
      </div>
    </Modal>
    </template>
  </section>
</template>

<style scoped>
.users-page { min-width: 0; }
.users-toolbar-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.users-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.users-updated-at { color: rgba(0, 0, 0, 0.45); font-size: 12px; white-space: nowrap; }
.users-refresh-button { display: grid; width: 34px; height: 34px; place-items: center; padding: 0; border-radius: 8px; }
.users-refresh-button :deep(.ant-btn-icon) { margin: 0; }
.users-page-title { margin: 0; color: rgba(0, 0, 0, 0.88); font-size: 22px; line-height: 1.35; font-weight: 700; }
.users-page-subtitle { margin: 6px 0 0; color: rgba(0, 0, 0, 0.45); font-size: 13px; }
.panel-count, .panel-filter-count { color: rgba(0, 0, 0, 0.45); font-size: 12px; font-weight: 500; }
.users-surface { overflow: hidden; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.users-surface :deep(.ant-card-head) { background: linear-gradient(180deg, #fafcff, #fff); }
.panel-title { display: inline-flex; align-items: center; gap: 8px; }
.panel-title::before { width: 3px; height: 14px; border-radius: 2px; background: #1677ff; content: ''; }
.users-filter-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: -8px -24px 16px; padding: 14px 24px; border-bottom: 1px solid #f0f0f0; }
.search-input { width: 220px; }
.role-filter, .status-filter { width: 140px; }
.department-filter { width: 180px; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; border-top: 1px solid #f0f0f0; color: rgba(0, 0, 0, .45); font-size: 13px; }
.person-cell { display: flex; align-items: center; gap: 10px; }
.person-cell strong, .person-cell small { display: block; }
.person-cell strong { font-weight: 500; }
.person-cell small { margin-top: 2px; color: rgba(0, 0, 0, 0.45); font-size: 12px; }
.person-avatar { display: grid; place-items: center; width: 30px; height: 30px; flex-shrink: 0; border-radius: 50%; background: #e6f4ff; color: #1677ff; font-size: 12px; font-weight: 600; }
.role-tag { display: inline-flex; align-items: center; gap: 5px; border-radius: 11px; font-size: 12px; }
.role-tag.normal { color: rgba(0, 0, 0, 0.65); }
.role-tag.handler { color: #d46b08; }
.role-tag.admin { color: #1677ff; }
.tag-dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.user-form { display: grid; gap: 16px; padding: 8px 0; }
.form-field label { display: block; margin-bottom: 8px; color: rgba(0, 0, 0, 0.65); font-size: 13px; font-weight: 500; }
.required-mark { margin-right: 4px; color: #ff4d4f; }
.department-select { width: 100%; }
.role-picker { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.role-option { min-height: 102px; padding: 12px; border: 1.5px solid #e8e8e8; border-radius: 10px; background: #fff; text-align: left; cursor: pointer; transition: border-color .2s, box-shadow .2s, background .2s; }
.role-option:hover { border-color: #91caff; }
.role-option.selected { border-color: #1677ff; background: #f7fbff; box-shadow: 0 2px 8px rgb(22 119 255 / 12%); }
.role-option-name { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; }
.radio { display: grid; place-items: center; width: 14px; height: 14px; flex-shrink: 0; border: 1.5px solid #d9d9d9; border-radius: 50%; }
.selected .radio { border-color: #1677ff; }
.selected .radio::after { width: 7px; height: 7px; border-radius: 50%; background: #1677ff; content: ''; }
.role-option-desc { display: block; margin-top: 6px; color: rgba(0, 0, 0, 0.45); font-size: 11.5px; line-height: 1.6; }
.role-hint { margin: 10px 0 0; padding: 8px 12px; border: 1px solid #ffd591; border-radius: 8px; background: #fff7e6; color: #d46b08; font-size: 12px; }
@media (max-width: 680px) { .users-toolbar-head { flex-direction: column; } .users-toolbar-actions { width: 100%; } .users-toolbar-actions :deep(.ant-btn-primary) { margin-left: auto; } .users-updated-at { display: none; } .users-filter-bar { margin-right: -16px; margin-left: -16px; padding-right: 16px; padding-left: 16px; } .search-input, .role-filter, .status-filter, .department-filter { width: 100%; } .pagination-row { align-items: flex-start; flex-direction: column; } .role-picker { grid-template-columns: 1fr; } }
</style>
