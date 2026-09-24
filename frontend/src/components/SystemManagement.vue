<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Button, Card, Empty, Form, FormItem, Input, Modal, Popconfirm, Select, Tag, message } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { TagsOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { entityStatusMeta } from '../constants/statusConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { useFormErrors } from '../composables/useFormErrors'
import { usePageRefresh } from '../composables/usePageRefresh'
import { useAuth } from '../composables/useAuth'
import ContentSkeleton from './ContentSkeleton.vue'

type SystemStatus = 'ACTIVE' | 'INACTIVE'
type SystemItem = {
  id: number
  name: string
  ownerName: string
  ownerUserId: number | null
  collaborators: string[]
  collaboratorUserIds: number[]
  status: SystemStatus
  recordVersion: number
  versionCount: number
  requirementCount: number
}
type VersionItem = {
  id: number
  systemId: number
  name: string
  description: string | null
  status: SystemStatus
  requirementCount: number
  recordVersion: number
}
type ActiveUser = { id: number; username: string; displayName: string }

const router = useRouter()
const { isHandler } = useAuth()
const { handleError } = useApiError()
const { errors, clearErrors, setError, applyServerErrors } = useFormErrors()

const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const activeUsers = ref<ActiveUser[]>([])
const loading = ref(true)
const versionLoading = ref(false)
const selectedSystemId = ref<number | null>(null)
const nameFilter = ref('')
const ownerFilter = ref('')
const collaboratorFilter = ref('')
const statusFilter = ref('')
const systemPage = ref(1)
const versionPage = ref(1)
const systemPageSize = 5
const versionPageSize = 5
const systemFormMode = ref<'create' | 'edit' | null>(null)
const systemForm = reactive({ id: 0, name: '', ownerUserId: undefined as number | undefined, collaboratorUserIds: [] as number[], recordVersion: 0 })
const versionFormMode = ref<'create' | 'edit' | null>(null)
const versionForm = reactive({ id: 0, name: '', description: '', recordVersion: 0 })
const migrationSourceId = ref<number | null>(null)
const unassignedMigrationTarget = '__unassigned__'
const migrationTargetId = ref<number | typeof unassignedMigrationTarget>(unassignedMigrationTarget)

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'ACTIVE', label: '启用' },
  { value: 'INACTIVE', label: '停用' },
]
const activeUserOptions = computed(() => activeUsers.value.map((user) => ({
  value: user.id,
  label: `${user.displayName}（${user.username}）`,
})))
const selectedSystem = computed(() => systems.value.find((system) => system.id === selectedSystemId.value) ?? null)
const filteredSystems = computed(() => {
  const normalizedName = nameFilter.value.trim().toLowerCase()
  const normalizedOwner = ownerFilter.value.trim().toLowerCase()
  const normalizedCollaborator = collaboratorFilter.value.trim().toLowerCase()
  return systems.value.filter((system) => {
    const matchesName = !normalizedName || system.name.toLowerCase().includes(normalizedName)
    const matchesOwner = !normalizedOwner || system.ownerName.toLowerCase().includes(normalizedOwner)
    const matchesCollaborator = !normalizedCollaborator || system.collaborators.some((name) => name.toLowerCase().includes(normalizedCollaborator))
    const matchesStatus = !statusFilter.value || system.status === statusFilter.value
    return matchesName && matchesOwner && matchesCollaborator && matchesStatus
  })
})
const systemTotalPages = computed(() => Math.max(1, Math.ceil(filteredSystems.value.length / systemPageSize)))
const versionTotalPages = computed(() => Math.max(1, Math.ceil(versions.value.length / versionPageSize)))
const pagedSystems = computed(() => filteredSystems.value.slice((systemPage.value - 1) * systemPageSize, systemPage.value * systemPageSize))
const pagedVersions = computed(() => versions.value.slice((versionPage.value - 1) * versionPageSize, versionPage.value * versionPageSize))

watch([nameFilter, ownerFilter, collaboratorFilter, statusFilter], () => { systemPage.value = 1 })
watch(() => filteredSystems.value.length, () => {
  systemPage.value = Math.min(systemPage.value, systemTotalPages.value)
})
watch(() => versions.value.length, () => {
  versionPage.value = Math.min(versionPage.value, versionTotalPages.value)
})
const migrationTargets = computed(() => systems.value.filter((system) => system.id !== migrationSourceId.value && system.status === 'ACTIVE'))
const systemFormOpen = computed({
  get: () => systemFormMode.value !== null,
  set: (open: boolean) => { if (!open) systemFormMode.value = null },
})
const versionFormOpen = computed({
  get: () => versionFormMode.value !== null,
  set: (open: boolean) => { if (!open) versionFormMode.value = null },
})
const migrationOpen = computed({
  get: () => migrationSourceId.value !== null,
  set: (open: boolean) => { if (!open) migrationSourceId.value = null },
})

const loadSystemData = async () => {
  loading.value = true
  try {
    const systemsResponse = await api.get('/systems')
    systems.value = Array.isArray(systemsResponse.data) ? systemsResponse.data : []
    if (isHandler.value) {
      const { data } = await api.get('/users/active')
      activeUsers.value = Array.isArray(data) ? data : []
    } else {
      activeUsers.value = []
    }
  } finally {
    loading.value = false
  }
}

const loadVersions = async (systemId: number) => {
  versionLoading.value = true
  try {
    const { data } = await api.get(`/systems/${systemId}/versions`)
    versions.value = Array.isArray(data) ? data : []
  } finally {
    versionLoading.value = false
  }
}

const selectSystem = async (systemId: number, resetVersionPage = true) => {
  selectedSystemId.value = systemId
  versionFormMode.value = null
  if (resetVersionPage) versionPage.value = 1
  await loadVersions(systemId)
}

const refreshWorkspace = async () => {
  await loadSystemData()
  const currentSystem = systems.value.find((system) => system.id === selectedSystemId.value)
  const nextSystemId = currentSystem?.id ?? systems.value[0]?.id
  if (nextSystemId === undefined) {
    selectedSystemId.value = null
    versions.value = []
    versionFormMode.value = null
    return
  }
  await selectSystem(nextSystemId, false)
}

const showCreateSystem = () => {
  Object.assign(systemForm, { id: 0, name: '', ownerUserId: undefined, collaboratorUserIds: [], recordVersion: 0 })
  systemFormMode.value = 'create'
  clearErrors()
}

const showEditSystem = (system: SystemItem) => {
  Object.assign(systemForm, {
    id: system.id,
    name: system.name,
    ownerUserId: system.ownerUserId ?? undefined,
    collaboratorUserIds: [...(system.collaboratorUserIds ?? [])],
    recordVersion: system.recordVersion,
  })
  systemFormMode.value = 'edit'
  clearErrors()
}

const saveSystem = async () => {
  clearErrors()
  if (!systemForm.name.trim()) setError('name', '请输入系统名称')
  if (systemForm.ownerUserId === undefined) setError('ownerUserId', '请选择负责人账号')
  if (Object.keys(errors).length > 0) {
    message.warning('请先补充标记的必填项')
    return
  }
  const ownerUserId = systemForm.ownerUserId
  if (ownerUserId === undefined) return
  if (new Set(systemForm.collaboratorUserIds).size !== systemForm.collaboratorUserIds.length) {
    message.warning('同一系统的协助账号不能重复')
    return
  }
  if (systemForm.collaboratorUserIds.includes(ownerUserId)) {
    message.warning('负责人不能同时作为协助人')
    return
  }
  const body = { name: systemForm.name.trim(), ownerUserId, collaboratorUserIds: systemForm.collaboratorUserIds }
  let createdSystemId: number | null = null
  try {
    if (systemFormMode.value === 'create') {
      const { data } = await api.post('/systems', body)
      if (Number.isInteger(data?.id)) {
        selectedSystemId.value = data.id
        createdSystemId = data.id
      }
      message.success('新增系统成功')
    } else {
      await api.put(`/systems/${systemForm.id}`, { ...body, recordVersion: systemForm.recordVersion })
      message.success('系统信息已更新')
    }
    systemFormMode.value = null
    await refreshWorkspace()
    if (createdSystemId !== null) {
      const createdIndex = filteredSystems.value.findIndex((system) => system.id === createdSystemId)
      if (createdIndex >= 0) systemPage.value = Math.floor(createdIndex / systemPageSize) + 1
    }
    markChanged(['requirements', 'versions', 'dashboard', 'overview'])
  } catch (error: unknown) {
    const details = applyServerErrors(error)
    handleError(error, details.message || '保存系统失败，请检查名称是否重复')
  }
}

const toggleSystem = async (system: SystemItem) => {
  const status: SystemStatus = system.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/systems/${system.id}/status`, { status, recordVersion: system.recordVersion })
    message.success(status === 'ACTIVE' ? '系统已启用' : '系统已停用')
    await refreshWorkspace()
    markChanged(['requirements', 'versions', 'dashboard', 'overview'])
  } catch {
    message.error('更新系统状态失败')
  }
}

const deleteSystem = async (system: SystemItem) => {
  try {
    await api.delete(`/systems/${system.id}`)
    message.success('系统已删除')
    await refreshWorkspace()
    markChanged(['requirements', 'versions', 'dashboard', 'overview'])
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) {
      migrationSourceId.value = system.id
      migrationTargetId.value = unassignedMigrationTarget
      message.warning('该系统存在关联需求，请先迁移需求')
    } else {
      message.error('删除系统失败')
    }
  }
}

const migrateRequirements = async () => {
  if (migrationSourceId.value === null) return
  try {
    const targetSystemId = migrationTargetId.value === unassignedMigrationTarget ? null : migrationTargetId.value
    const { data } = await api.post(`/systems/${migrationSourceId.value}/migrate`, { targetSystemId })
    message.success(`已迁移 ${data.migratedCount} 条需求`)
    migrationSourceId.value = null
    await refreshWorkspace()
    markChanged(['requirements', 'versions', 'dashboard', 'overview'])
  } catch {
    message.error('迁移需求失败，请检查目标系统状态')
  }
}

const viewRequirements = (systemId: number) => {
  void router.push({ name: 'requirement-list', query: { systemId: String(systemId) } })
}

const showCreateVersion = () => {
  if (!selectedSystem.value) return
  Object.assign(versionForm, { id: 0, name: '', description: '', recordVersion: 0 })
  versionFormMode.value = 'create'
  clearErrors()
}

const showEditVersion = (version: VersionItem) => {
  Object.assign(versionForm, {
    id: version.id,
    name: version.name,
    description: version.description ?? '',
    recordVersion: version.recordVersion,
  })
  versionFormMode.value = 'edit'
  clearErrors()
}

const saveVersion = async () => {
  clearErrors()
  if (selectedSystemId.value === null) setError('systemId', '请选择系统')
  if (!versionForm.name.trim()) setError('name', '请输入版本名称')
  if (Object.keys(errors).length > 0) {
    message.warning('请先补充标记的必填项')
    return
  }
  const systemId = selectedSystemId.value
  if (systemId === null) return
  let createdVersionId: number | null = null
  try {
    const body = { name: versionForm.name.trim(), description: versionForm.description.trim() || null }
    if (versionFormMode.value === 'create') {
      const { data } = await api.post(`/systems/${systemId}/versions`, body)
      if (Number.isInteger(data?.id)) createdVersionId = data.id
      message.success('新增版本成功')
    } else {
      await api.put(`/system-versions/${versionForm.id}`, { ...body, recordVersion: versionForm.recordVersion })
      message.success('版本信息已更新')
    }
    versionFormMode.value = null
    await refreshWorkspace()
    if (createdVersionId !== null) {
      const createdIndex = versions.value.findIndex((version) => version.id === createdVersionId)
      if (createdIndex >= 0) versionPage.value = Math.floor(createdIndex / versionPageSize) + 1
    }
    markChanged(['requirements', 'dashboard', 'overview'])
  } catch (error: unknown) {
    const details = applyServerErrors(error)
    message.error(details.message || '保存版本失败，请稍后重试')
  }
}

const toggleVersion = async (version: VersionItem) => {
  const status: SystemStatus = version.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await api.patch(`/system-versions/${version.id}/status`, { status, recordVersion: version.recordVersion })
    message.success(status === 'ACTIVE' ? '版本已启用' : '版本已停用')
    await refreshWorkspace()
    markChanged(['requirements', 'dashboard', 'overview'])
  } catch {
    message.error('更新版本状态失败')
  }
}

const manageVersionRequirements = (versionId: number) => {
  void router.push({ name: 'version-requirement-management', params: { versionId } })
}

const deleteVersion = async (version: VersionItem) => {
  try {
    await api.delete(`/system-versions/${version.id}`)
    message.success('版本已删除')
    await refreshWorkspace()
    markChanged(['requirements', 'dashboard', 'overview'])
  } catch {
    message.error('删除版本失败；若已关联需求，请先调整需求版本')
  }
}

const { loaded } = usePageRefresh('systems', refreshWorkspace)
</script>

<template>
  <section class="system-page" data-test="system-page">
    <ContentSkeleton v-if="!loaded" preset="cards" :rows="4" />
    <div class="system-workspace">
      <Card v-show="loaded" class="workspace-panel systems-panel" :bordered="false">
        <div class="workspace-panel-header">
          <div class="panel-heading"><span class="panel-heading-mark"></span><h2>系统列表</h2><span class="panel-count">共 {{ filteredSystems.length }} 个</span></div>
          <Button v-if="isHandler" type="primary" data-test="create-system" @click="showCreateSystem">新增系统</Button>
        </div>
        <div class="systems-filter-bar">
          <Input v-model:value="nameFilter" data-test="name-filter" allow-clear placeholder="按系统名称筛选" />
          <Input v-model:value="ownerFilter" data-test="owner-filter" allow-clear placeholder="按负责人筛选" />
          <Select v-model:value="statusFilter" data-test="status-filter" :options="statusOptions" />
          <Input v-model:value="collaboratorFilter" data-test="collaborator-filter" allow-clear placeholder="按协助人筛选" />
        </div>
        <div :class="{ 'is-panel-loading': loading }">
          <template v-if="filteredSystems.length">
            <div class="system-card-list">
              <article v-for="system in pagedSystems" :key="system.id" class="system-card" :class="{ selected: selectedSystemId === system.id }" tabindex="0" @click="selectSystem(system.id)" @keydown.enter="selectSystem(system.id)">
                <div class="system-card-title-row"><strong>{{ system.name }}</strong><Tag :color="entityStatusMeta(system.status).color">{{ entityStatusMeta(system.status).label }}</Tag></div>
                <div class="system-card-meta"><span>负责人 <b>{{ system.ownerName || '—' }}</b></span><span>协助人 <b>{{ system.collaborators.join('、') || '—' }}</b></span><span>版本 <b>{{ system.versionCount }}</b></span><span>关联需求 <b>{{ system.requirementCount }}</b></span></div>
                <div class="system-card-actions" @click.stop>
                  <Button v-if="isHandler" type="link" size="small" :data-test="`edit-system-${system.id}`" @click="showEditSystem(system)">编辑</Button>
                  <Button type="link" size="small" :data-test="`view-requirements-${system.id}`" @click="viewRequirements(system.id)">查看需求</Button>
                  <Button v-if="isHandler" type="link" size="small" :data-test="`toggle-system-${system.id}`" @click="toggleSystem(system)">{{ system.status === 'ACTIVE' ? '停用' : '启用' }}</Button>
                  <Popconfirm v-if="isHandler" :title="`确定删除系统“${system.name}”吗？`" ok-text="删除" cancel-text="取消" @confirm="deleteSystem(system)"><Button danger type="link" size="small">删除</Button></Popconfirm>
                </div>
              </article>
            </div>
            <div class="pagination-row"><span>共 {{ filteredSystems.length }} 个</span><AppPagination v-model:current="systemPage" :page-size="systemPageSize" :total="filteredSystems.length" :show-size-changer="false" :show-less-items="true" responsive /></div>
          </template>
          <Empty v-else-if="!loading" class="workspace-empty" description="暂无符合条件的系统" />
        </div>
      </Card>

      <Card v-show="loaded" class="workspace-panel versions-panel" :bordered="false">
        <div class="workspace-panel-header version-panel-header">
          <div class="panel-heading"><span class="panel-heading-mark"></span><h2>版本管理</h2><span class="panel-count">共 {{ selectedSystem ? versions.length : 0 }} 个</span></div>
          <Button v-if="isHandler" type="primary" data-test="create-version" :disabled="!selectedSystem" @click="showCreateVersion">新增版本</Button>
        </div>
        <template v-if="selectedSystem">
          <div class="current-system-notice">当前系统：<strong>{{ selectedSystem.name }}</strong><span>点击左侧其他系统可切换</span></div>
          <div :class="{ 'is-panel-loading': versionLoading }">
            <template v-if="versions.length">
              <div class="version-card-list">
                <article v-for="version in pagedVersions" :key="version.id" class="version-card">
                  <span class="version-icon"><TagsOutlined /></span>
                  <div class="version-main"><strong>{{ version.name }}</strong><span class="version-description">{{ version.description || '暂无版本说明' }}</span><span>关联需求 {{ version.requirementCount }} 条</span></div>
                  <Tag :color="entityStatusMeta(version.status).color">{{ entityStatusMeta(version.status).label }}</Tag>
                  <div v-if="isHandler" class="version-card-actions"><Button type="link" size="small" @click="manageVersionRequirements(version.id)">管理需求</Button><Button type="link" size="small" @click="showEditVersion(version)">编辑</Button><Button type="link" size="small" @click="toggleVersion(version)">{{ version.status === 'ACTIVE' ? '停用' : '启用' }}</Button><Popconfirm :title="`确定删除版本“${version.name}”吗？`" ok-text="删除" cancel-text="取消" @confirm="deleteVersion(version)"><Button danger type="link" size="small">删除</Button></Popconfirm></div>
                </article>
              </div>
              <div class="pagination-row"><span>共 {{ versions.length }} 个</span><AppPagination v-model:current="versionPage" :page-size="versionPageSize" :total="versions.length" :show-size-changer="false" :show-less-items="true" responsive /></div>
            </template>
            <Empty v-else-if="!versionLoading" class="workspace-empty" description="暂无版本，请新增。" />
          </div>
        </template>
        <Empty v-else class="version-empty" description="请先在左侧选择一个系统，再维护其版本。" />
      </Card>
    </div>

    <Modal v-model:open="systemFormOpen" :title="systemFormMode === 'create' ? '新增系统' : '编辑系统'" :footer="null" destroy-on-close :get-container="false" @cancel="systemFormMode = null">
      <Form data-test="system-modal" layout="vertical" @submit.prevent="saveSystem">
        <p class="field-requirement-legend" data-test="system-field-legend">带 <span class="field-required">*</span> 的项目为必填项，选填项目可根据实际情况填写。</p>
        <FormItem data-test="system-name-field" label="系统名称" required :validate-status="errors.name ? 'error' : undefined" :help="errors.name"><Input v-model:value="systemForm.name" data-test="system-name" placeholder="请输入系统名称" /></FormItem>
        <FormItem label="负责人账号" required :validate-status="errors.ownerUserId ? 'error' : undefined" :help="errors.ownerUserId"><Select v-model:value="systemForm.ownerUserId" data-test="system-owner" :options="activeUserOptions" placeholder="请选择启用账号" show-search option-filter-prop="label" /></FormItem>
        <FormItem data-test="system-collaborators-field" label="协助账号" extra="可从启用账号中多选；负责人不能同时作为协助人"><Select v-model:value="systemForm.collaboratorUserIds" data-test="system-collaborators" mode="multiple" :options="activeUserOptions" placeholder="请选择协助账号" show-search option-filter-prop="label" /></FormItem>
        <div class="modal-actions"><Button html-type="button" @click="systemFormMode = null">取消</Button><Button type="primary" html-type="submit">保存</Button></div>
      </Form>
    </Modal>

    <Modal v-model:open="versionFormOpen" :title="versionFormMode === 'create' ? '新增版本' : '编辑版本'" :footer="null" destroy-on-close :get-container="false" @cancel="versionFormMode = null">
      <Form data-test="version-modal" layout="vertical" @submit.prevent="saveVersion">
        <FormItem label="版本名称" required :validate-status="errors.name ? 'error' : undefined" :help="errors.name"><Input v-model:value="versionForm.name" data-test="version-name" placeholder="请输入版本名称" /></FormItem>
        <FormItem label="版本说明" extra="选填，最多 2000 字"><Input.TextArea v-model:value="versionForm.description" data-test="version-description" :rows="5" :maxlength="2000" show-count placeholder="说明版本目标、范围或交付计划" /></FormItem>
        <div class="modal-actions"><Button html-type="button" @click="versionFormMode = null">取消</Button><Button type="primary" html-type="submit">保存</Button></div>
      </Form>
    </Modal>

    <Modal v-model:open="migrationOpen" title="迁移关联需求" :footer="null" destroy-on-close :get-container="false" @cancel="migrationSourceId = null">
      <Form data-test="migration-modal" layout="vertical" @submit.prevent="migrateRequirements">
        <FormItem label="迁移目标" required><Select v-model:value="migrationTargetId" :options="[{ value: unassignedMigrationTarget, label: '暂无系统' }, ...migrationTargets.map((system) => ({ value: system.id, label: system.name }))]" /></FormItem>
        <div class="modal-actions"><Button html-type="button" @click="migrationSourceId = null">取消</Button><Button type="primary" html-type="submit">确认迁移</Button></div>
      </Form>
    </Modal>
  </section>
</template>

<style scoped>
.system-workspace { display: grid; grid-template-columns: minmax(0, 7fr) minmax(0, 5fr); gap: 20px; align-items: start; }
.workspace-panel { overflow: hidden; border-radius: 8px; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.is-panel-loading { opacity: .55; pointer-events: none; transition: opacity .25s ease; }
.workspace-panel :deep(.ant-card-body) { padding: 0; }
.workspace-panel-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 20px; border-bottom: 1px solid #f0f0f0; background: linear-gradient(180deg, #fafcff, #fff); }
.panel-heading { display: flex; align-items: center; min-width: 0; gap: 8px; }
.panel-heading-mark { width: 3px; height: 15px; flex: 0 0 auto; border-radius: 2px; background: #1677ff; }
.panel-heading h2 { margin: 0; color: rgba(0, 0, 0, 0.88); font-size: 15px; font-weight: 600; }
.panel-count { color: rgba(0, 0, 0, 0.45); font-size: 12px; }
.systems-filter-bar { display: grid; grid-template-columns: minmax(130px, 1fr) minmax(130px, 1fr) 116px minmax(130px, 1fr); gap: 10px; padding: 14px 20px; border-bottom: 1px solid #f0f0f0; }
.system-card-list, .version-card-list { display: grid; gap: 8px; padding: 8px 12px 12px; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 16px; border-top: 1px solid #f0f0f0; color: rgba(0, 0, 0, .45); font-size: 13px; }
.system-card { position: relative; padding: 14px 16px; border: 1px solid #f0f0f0; border-radius: 8px; cursor: pointer; outline: none; transition: border-color .2s, box-shadow .2s, background .2s; }
.system-card:hover, .system-card:focus-visible { border-color: #91caff; }
.system-card.selected { border-color: #1677ff; background: linear-gradient(180deg, #f7fbff, #fff); box-shadow: 0 2px 10px rgb(22 119 255 / 12%); }
.system-card.selected::before { position: absolute; top: 12px; bottom: 12px; left: -1px; width: 3px; border-radius: 2px; background: #1677ff; content: ''; }
.system-card-title-row { display: flex; align-items: center; gap: 10px; }
.system-card-title-row strong { min-width: 0; overflow: hidden; color: rgba(0, 0, 0, 0.88); font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.system-card-meta { display: flex; flex-wrap: wrap; gap: 8px 14px; margin-top: 8px; color: rgba(0, 0, 0, 0.45); font-size: 12px; }
.system-card-meta b { color: rgba(0, 0, 0, 0.65); font-weight: 500; }
.system-card-actions { display: flex; flex-wrap: wrap; gap: 2px; margin-top: 10px; padding-top: 8px; border-top: 1px dashed #f0f0f0; }
.system-card-actions :deep(.ant-btn), .version-card-actions :deep(.ant-btn) { padding-inline: 8px; }
.current-system-notice { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin: 14px 20px 0; padding: 8px 12px; border-radius: 8px; background: #e6f4ff; color: #0958d9; font-size: 13px; }
.current-system-notice span { color: #69b1ff; font-size: 12px; }
.version-card { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border: 1px solid #f0f0f0; border-radius: 8px; transition: border-color .2s, background .2s; }
.version-card:hover { border-color: #91caff; background: #fafcff; }
.version-icon { display: grid; width: 34px; height: 34px; flex: 0 0 auto; place-items: center; border-radius: 8px; background: #e6f4ff; color: #1677ff; font-size: 16px; }
.version-main { display: grid; flex: 1; min-width: 0; gap: 3px; }
.version-main strong { overflow: hidden; color: rgba(0, 0, 0, 0.88); font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.version-main span { color: rgba(0, 0, 0, 0.45); font-size: 12px; }
.version-card-actions { display: flex; flex: 0 0 auto; gap: 2px; }
.workspace-empty, .version-empty { padding: 40px 0; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; }
@media (max-width: 1200px) { .system-workspace { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .systems-filter-bar { grid-template-columns: 1fr 1fr; padding: 12px 16px; } .workspace-panel-header { padding: 14px 16px; } .current-system-notice { margin-inline: 16px; } .version-card { align-items: flex-start; flex-wrap: wrap; } .version-card-actions { width: 100%; } .pagination-row { align-items: flex-start; flex-direction: column; } }
@media (max-width: 480px) { .systems-filter-bar { grid-template-columns: 1fr; } .system-card, .version-card { padding: 12px; } .system-card-actions { margin-inline: -4px; } }
</style>
