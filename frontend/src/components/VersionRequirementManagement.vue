<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Alert, Breadcrumb, BreadcrumbItem, Button, Card, Checkbox, Empty, Input, Modal, Result, Select, Spin, TabPane, Tabs, Tag, Tooltip, message } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { ArrowLeftOutlined, LinkOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta } from '../constants/statusConfig'
import { urgencyMeta, urgencyOptions } from '../constants/urgencyConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { usePageRefresh } from '../composables/usePageRefresh'

type VersionItem = {
  id: number
  systemId: number
  systemName: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'INACTIVE'
  requirementCount: number
  recordVersion: number
}
type RequirementItem = {
  id: number
  title: string | null
  status: string | null
  urgency: string | null
  targetVersionId: number | null
  targetVersionName: string | null
  requesterName: string | null
  department: string | null
  updatedAt: string | null
  recordVersion: number
}
type PageData = { content: RequirementItem[]; totalElements: number; totalPages: number; page: number; size: number }
type Summary = { currentCount: number; candidateCount: number; unassignedCount: number; otherVersionCount: number; highUrgencyCount: number }
type Filters = { keyword: string; status: string; urgency: string; source: string }

const route = useRoute()
const router = useRouter()
const { handleError } = useApiError()
const versionId = computed(() => Number(route.params.versionId))
const version = ref<VersionItem | null>(null)
const summary = ref<Summary>({ currentCount: 0, candidateCount: 0, unassignedCount: 0, otherVersionCount: 0, highUrgencyCount: 0 })
const activeTab = ref<'current' | 'candidate'>('current')
const currentPage = ref(1)
const candidatePage = ref(1)
const pageSize = 10
const currentData = ref<PageData>({ content: [], totalElements: 0, totalPages: 0, page: 0, size: pageSize })
const candidateData = ref<PageData>({ content: [], totalElements: 0, totalPages: 0, page: 0, size: pageSize })
const currentLoading = ref(false)
const candidateLoading = ref(false)
const operationLoading = ref(false)
const loadError = ref('')
const currentFilters = reactive<Filters>({ keyword: '', status: '', urgency: '', source: '' })
const candidateFilters = reactive<Filters>({ keyword: '', status: '', urgency: '', source: '' })
const selectedCurrentIds = ref<number[]>([])
const selectedCandidateIds = ref<number[]>([])

const manageableStatuses = new Set(['PENDING_EVALUATION', 'CONFIRMED', 'IN_DEVELOPMENT', 'PAUSED'])
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_EVALUATION', label: '待评估' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'IN_DEVELOPMENT', label: '开发中' },
  { value: 'PAUSED', label: '暂停' },
]
const urgencyFilterOptions = [{ value: '', label: '全部紧急度' }, ...urgencyOptions]
const sourceOptions = [
  { value: '', label: '来源：全部' },
  { value: 'UNASSIGNED', label: '未绑定版本' },
  { value: 'OTHER_VERSION', label: '其他版本' },
]

const isManageable = (item: RequirementItem) => item.status !== null && manageableStatuses.has(item.status)
const currentSelectableRows = computed(() => currentData.value.content.filter(isManageable))
const allCurrentSelected = computed(() => currentSelectableRows.value.length > 0 && currentSelectableRows.value.every((item) => selectedCurrentIds.value.includes(item.id)))
const someCurrentSelected = computed(() => !allCurrentSelected.value && currentSelectableRows.value.some((item) => selectedCurrentIds.value.includes(item.id)))
const allCandidatesSelected = computed(() => candidateData.value.content.length > 0 && candidateData.value.content.every((item) => selectedCandidateIds.value.includes(item.id)))
const someCandidatesSelected = computed(() => !allCandidatesSelected.value && candidateData.value.content.some((item) => selectedCandidateIds.value.includes(item.id)))

const requestParams = (scope: 'CURRENT' | 'CANDIDATES', page: number, filters: Filters) => ({
  scope,
  page: page - 1,
  size: pageSize,
  keyword: filters.keyword.trim() || undefined,
  status: filters.status || undefined,
  urgency: filters.urgency || undefined,
  source: scope === 'CANDIDATES' ? filters.source || undefined : undefined,
})

const loadCurrent = async () => {
  currentLoading.value = true
  try {
    const { data } = await api.get(`/system-versions/${versionId.value}/requirements`, { params: requestParams('CURRENT', currentPage.value, currentFilters) })
    currentData.value = data
    selectedCurrentIds.value = []
  } finally {
    currentLoading.value = false
  }
}

const loadCandidates = async () => {
  candidateLoading.value = true
  try {
    const { data } = await api.get(`/system-versions/${versionId.value}/requirements`, { params: requestParams('CANDIDATES', candidatePage.value, candidateFilters) })
    candidateData.value = data
    selectedCandidateIds.value = []
  } finally {
    candidateLoading.value = false
  }
}

const loadPage = async () => {
  if (!Number.isSafeInteger(versionId.value) || versionId.value <= 0) {
    loadError.value = '版本编号无效'
    return
  }
  loadError.value = ''
  try {
    const [versionResponse, summaryResponse] = await Promise.all([
      api.get(`/system-versions/${versionId.value}`),
      api.get(`/system-versions/${versionId.value}/requirements/summary`),
      loadCurrent(),
    ])
    version.value = versionResponse.data
    summary.value = summaryResponse.data
    if (activeTab.value === 'candidate') await loadCandidates()
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    loadError.value = status === 404 ? '该版本不存在或已被删除' : '加载版本需求失败，请稍后重试'
    throw error
  }
}

const { loaded } = usePageRefresh('versions', loadPage)

const reloadCurrentFromFirst = async () => {
  currentPage.value = 1
  await loadCurrent()
}
const reloadCandidatesFromFirst = async () => {
  candidatePage.value = 1
  await loadCandidates()
}
const changeCurrentPage = async (page: number) => {
  currentPage.value = page
  await loadCurrent()
}
const changeCandidatePage = async (page: number) => {
  candidatePage.value = page
  await loadCandidates()
}

watch(activeTab, async (tab) => {
  selectedCurrentIds.value = []
  selectedCandidateIds.value = []
  if (tab === 'candidate') await loadCandidates()
})

const toggleCurrent = (item: RequirementItem) => {
  if (!isManageable(item)) return
  selectedCurrentIds.value = selectedCurrentIds.value.includes(item.id)
    ? selectedCurrentIds.value.filter((id) => id !== item.id)
    : [...selectedCurrentIds.value, item.id]
}
const toggleCandidate = (item: RequirementItem) => {
  selectedCandidateIds.value = selectedCandidateIds.value.includes(item.id)
    ? selectedCandidateIds.value.filter((id) => id !== item.id)
    : [...selectedCandidateIds.value, item.id]
}
const toggleAllCurrent = () => {
  selectedCurrentIds.value = allCurrentSelected.value ? [] : currentSelectableRows.value.map((item) => item.id)
}
const toggleAllCandidates = () => {
  selectedCandidateIds.value = allCandidatesSelected.value ? [] : candidateData.value.content.map((item) => item.id)
}

const selectedRows = (items: RequirementItem[], ids: number[]) => items
  .filter((item) => ids.includes(item.id))
  .map((item) => ({ id: item.id, recordVersion: item.recordVersion }))

const refreshAfterOperation = async () => {
  await Promise.all([
    api.get(`/system-versions/${versionId.value}/requirements/summary`).then(({ data }) => { summary.value = data }),
    loadCurrent(),
    loadCandidates(),
  ])
  markChanged(['requirements', 'versions', 'systems', 'dashboard', 'overview'])
}

const bindSelected = () => {
  const items = candidateData.value.content.filter((item) => selectedCandidateIds.value.includes(item.id))
  if (!items.length || !version.value) return
  const boundCount = items.filter((item) => item.targetVersionId === null).length
  const migratedCount = items.length - boundCount
  Modal.confirm({
    title: `纳入版本“${version.value.name}”`,
    content: `本次将绑定 ${boundCount} 条未分配需求，迁移 ${migratedCount} 条其他版本需求。确认后立即生效。`,
    okText: '确认纳入',
    cancelText: '取消',
    onOk: async () => {
      operationLoading.value = true
      try {
        const { data } = await api.post(`/system-versions/${versionId.value}/requirements/batch-bind`, {
          requirements: selectedRows(candidateData.value.content, selectedCandidateIds.value),
        })
        message.success(`已绑定 ${data.boundCount} 条，迁移 ${data.migratedCount} 条需求`)
        await refreshAfterOperation()
      } catch (error: unknown) {
        handleError(error, '纳入需求失败，请刷新后重试')
        throw error
      } finally {
        operationLoading.value = false
      }
    },
  })
}

const unbindSelected = () => {
  const items = currentData.value.content.filter((item) => selectedCurrentIds.value.includes(item.id))
  if (!items.length || !version.value) return
  Modal.confirm({
    title: '解除版本绑定',
    content: `确定将选中的 ${items.length} 条需求从版本“${version.value.name}”中解除吗？需求仍保留在所属系统中。`,
    okText: '确认解除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      operationLoading.value = true
      try {
        const { data } = await api.post(`/system-versions/${versionId.value}/requirements/batch-unbind`, {
          requirements: selectedRows(currentData.value.content, selectedCurrentIds.value),
        })
        message.success(`已解除 ${data.unboundCount} 条需求的版本绑定`)
        await refreshAfterOperation()
      } catch (error: unknown) {
        handleError(error, '解除版本失败，请刷新后重试')
        throw error
      } finally {
        operationLoading.value = false
      }
    },
  })
}

const formatDateTime = (value: string | null) => value ? value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 16) : '—'
const viewRequirement = (id: number) => { void router.push({ name: 'requirement-detail', params: { id } }) }
const returnToSystems = () => { void router.push({ name: 'system-management' }) }
</script>

<template>
  <section class="version-requirement-page" data-test="version-requirement-page">
    <Result v-if="loaded && loadError" status="error" :title="loadError">
      <template #extra><Button type="primary" @click="returnToSystems">返回系统与版本</Button></template>
    </Result>
    <template v-else>
      <div class="page-context-row">
        <Breadcrumb>
          <BreadcrumbItem><a @click="returnToSystems">系统与版本</a></BreadcrumbItem>
          <BreadcrumbItem>{{ version?.systemName || '加载中' }}</BreadcrumbItem>
          <BreadcrumbItem>版本需求管理</BreadcrumbItem>
        </Breadcrumb>
        <Button @click="returnToSystems"><template #icon><ArrowLeftOutlined /></template>返回系统管理</Button>
      </div>

      <Card :bordered="false" class="version-hero-card">
        <Spin :spinning="!loaded">
          <div class="version-heading">
            <span class="system-label">所属系统 {{ version?.systemName || '—' }}</span>
            <span class="heading-divider"></span>
            <strong>{{ version?.name || '版本信息加载中' }}</strong>
            <Tag :color="version?.status === 'ACTIVE' ? 'success' : 'default'">{{ version?.status === 'ACTIVE' ? '启用' : '停用' }}</Tag>
          </div>
          <p class="version-description">{{ version?.description || '暂无版本说明' }}</p>
          <div class="summary-grid">
            <div><strong>{{ summary.currentCount }}</strong><span>条</span><p>当前版本需求</p></div>
            <div class="candidate-stat"><strong>{{ summary.candidateCount }}</strong><span>条</span><p>可纳入需求（未分配 {{ summary.unassignedCount }} · 其他版本 {{ summary.otherVersionCount }}）</p></div>
            <div><strong>{{ summary.highUrgencyCount }}</strong><span>条</span><p>其中高紧急度</p></div>
          </div>
        </Spin>
      </Card>

      <Card :bordered="false" class="management-card">
        <Tabs v-model:active-key="activeTab">
          <TabPane key="current"><template #tab>当前版本需求 <span class="tab-count">{{ summary.currentCount }}</span></template></TabPane>
          <TabPane key="candidate"><template #tab>可纳入需求 <span class="tab-count">{{ summary.candidateCount }}</span></template></TabPane>
        </Tabs>

        <section v-if="activeTab === 'current'" data-test="current-requirements-tab">
          <div class="toolbar">
            <Input.Search v-model:value="currentFilters.keyword" allow-clear placeholder="搜索需求标题" @search="reloadCurrentFromFirst"><template #prefix><SearchOutlined /></template></Input.Search>
            <Select v-model:value="currentFilters.status" :options="statusOptions" @change="reloadCurrentFromFirst" />
            <Select v-model:value="currentFilters.urgency" :options="urgencyFilterOptions" @change="reloadCurrentFromFirst" />
            <span class="toolbar-spacer"></span>
            <span class="selected-info">已选 <b>{{ selectedCurrentIds.length }}</b> 条</span>
            <Button danger type="primary" :disabled="selectedCurrentIds.length === 0" :loading="operationLoading" @click="unbindSelected">解除版本绑定</Button>
          </div>
          <div class="table-wrap" :class="{ loading: currentLoading }">
            <table>
              <thead><tr><th class="checkbox-cell"><Checkbox :checked="allCurrentSelected" :indeterminate="someCurrentSelected" @change="toggleAllCurrent" /></th><th>需求标题</th><th>状态</th><th>紧急程度</th><th>当前版本</th><th>更新时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in currentData.content" :key="item.id" :class="{ terminal: !isManageable(item) }">
                  <td class="checkbox-cell"><Tooltip :title="isManageable(item) ? '' : '终态需求仅供查看，不能迁移或解除'"><span><Checkbox :checked="selectedCurrentIds.includes(item.id)" :disabled="!isManageable(item)" @change="toggleCurrent(item)" /></span></Tooltip></td>
                  <td><Button type="link" class="requirement-link" @click="viewRequirement(item.id)">{{ item.title || `需求 #${item.id}` }}</Button></td>
                  <td><Tag :color="requirementStatusMeta(item.status || '').color">{{ requirementStatusMeta(item.status || '').label }}</Tag><span v-if="!isManageable(item)" class="terminal-note">只读</span></td>
                  <td><Tag :color="urgencyMeta(item.urgency).color">{{ urgencyMeta(item.urgency).label }}</Tag></td>
                  <td>{{ item.targetVersionName || '—' }}</td><td>{{ formatDateTime(item.updatedAt) }}</td>
                  <td><Button type="link" size="small" @click="viewRequirement(item.id)">查看详情</Button></td>
                </tr>
              </tbody>
            </table>
            <Empty v-if="!currentLoading && !currentData.content.length" description="当前版本暂无需求" />
          </div>
          <div class="pagination-row"><span>共 {{ currentData.totalElements }} 条</span><AppPagination :current="currentPage" :page-size="pageSize" :total="currentData.totalElements" :show-size-changer="false" @change="changeCurrentPage" /></div>
        </section>

        <section v-else data-test="candidate-requirements-tab">
          <Alert v-if="version?.status === 'INACTIVE'" class="inactive-alert" type="warning" show-icon message="当前版本已停用，不能纳入需求；已有需求仍可查看和解除。" />
          <div class="toolbar">
            <Input.Search v-model:value="candidateFilters.keyword" allow-clear placeholder="搜索需求标题" @search="reloadCandidatesFromFirst"><template #prefix><SearchOutlined /></template></Input.Search>
            <Select v-model:value="candidateFilters.status" :options="statusOptions" @change="reloadCandidatesFromFirst" />
            <Select v-model:value="candidateFilters.urgency" :options="urgencyFilterOptions" @change="reloadCandidatesFromFirst" />
            <Select v-model:value="candidateFilters.source" :options="sourceOptions" @change="reloadCandidatesFromFirst" />
            <span class="toolbar-spacer"></span>
            <span class="selected-info">已选 <b>{{ selectedCandidateIds.length }}</b> 条</span>
            <Button type="primary" :disabled="selectedCandidateIds.length === 0 || version?.status !== 'ACTIVE'" :loading="operationLoading" @click="bindSelected"><template #icon><LinkOutlined /></template>纳入当前版本</Button>
          </div>
          <div class="table-wrap" :class="{ loading: candidateLoading }">
            <table>
              <thead><tr><th class="checkbox-cell"><Checkbox :checked="allCandidatesSelected" :indeterminate="someCandidatesSelected" @change="toggleAllCandidates" /></th><th>需求标题</th><th>状态</th><th>紧急程度</th><th>当前版本</th><th>更新时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in candidateData.content" :key="item.id">
                  <td class="checkbox-cell"><Checkbox :checked="selectedCandidateIds.includes(item.id)" @change="toggleCandidate(item)" /></td>
                  <td><Button type="link" class="requirement-link" @click="viewRequirement(item.id)">{{ item.title || `需求 #${item.id}` }}</Button></td>
                  <td><Tag :color="requirementStatusMeta(item.status || '').color">{{ requirementStatusMeta(item.status || '').label }}</Tag></td>
                  <td><Tag :color="urgencyMeta(item.urgency).color">{{ urgencyMeta(item.urgency).label }}</Tag></td>
                  <td><span :class="{ muted: !item.targetVersionName }">{{ item.targetVersionName || '未绑定版本' }}</span></td><td>{{ formatDateTime(item.updatedAt) }}</td>
                  <td><Button type="link" size="small" @click="viewRequirement(item.id)">查看详情</Button></td>
                </tr>
              </tbody>
            </table>
            <Empty v-if="!candidateLoading && !candidateData.content.length" description="暂无可纳入需求" />
          </div>
          <div class="pagination-row"><span>共 {{ candidateData.totalElements }} 条</span><AppPagination :current="candidatePage" :page-size="pageSize" :total="candidateData.totalElements" :show-size-changer="false" @change="changeCandidatePage" /></div>
        </section>
      </Card>
    </template>
  </section>
</template>

<style scoped>
.version-requirement-page { min-width: 0; }
.page-context-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.page-context-row a { color: rgba(0, 0, 0, .65); }
.version-hero-card, .management-card { overflow: hidden; border-radius: 8px; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.version-hero-card { margin-bottom: 20px; background: linear-gradient(180deg, #f7faff 0%, #fff 100%); }
.version-hero-card :deep(.ant-card-body) { padding: 22px 28px 18px; }
.version-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.version-heading strong { font-size: 20px; }
.system-label, .version-description { color: rgba(0, 0, 0, .45); font-size: 13px; }
.heading-divider { width: 1px; height: 18px; background: #d9d9d9; }
.version-description { max-width: 860px; margin: 10px 0 0; color: rgba(0, 0, 0, .65); line-height: 1.8; white-space: pre-wrap; }
.summary-grid { display: flex; flex-wrap: wrap; gap: 40px; margin-top: 16px; }
.summary-grid > div { min-width: 130px; }
.summary-grid strong { font-size: 22px; font-variant-numeric: tabular-nums; }
.summary-grid span { margin-left: 2px; color: rgba(0, 0, 0, .45); font-size: 13px; }
.summary-grid p { margin: 2px 0 0; color: rgba(0, 0, 0, .45); font-size: 12px; }
.summary-grid .candidate-stat strong { color: #1677ff; }
.management-card :deep(.ant-card-body) { padding: 0; }
.management-card :deep(.ant-tabs-nav) { margin: 0; padding: 10px 16px 0; }
.management-card :deep(.ant-tabs-content-holder) { display: none; }
.tab-count { margin-left: 5px; padding: 1px 7px; border-radius: 9px; background: #f0f2f5; color: rgba(0, 0, 0, .45); font-size: 11px; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 14px 16px; }
.toolbar :deep(.ant-input-search) { width: 260px; }
.toolbar :deep(.ant-select) { min-width: 132px; }
.toolbar-spacer { flex: 1; }
.selected-info { color: rgba(0, 0, 0, .45); font-size: 13px; white-space: nowrap; }
.selected-info b { color: #1677ff; }
.inactive-alert { margin: 14px 16px 0; }
.table-wrap { min-height: 160px; overflow-x: auto; transition: opacity .2s; }
.table-wrap.loading { opacity: .55; pointer-events: none; }
table { width: 100%; min-width: 880px; border-collapse: collapse; }
th { padding: 12px 14px; background: #fafbfc; border-top: 1px solid #f0f0f0; border-bottom: 1px solid #f0f0f0; color: rgba(0, 0, 0, .65); font-size: 13px; font-weight: 600; text-align: left; white-space: nowrap; }
td { padding: 13px 14px; border-bottom: 1px solid #f0f0f0; vertical-align: middle; white-space: nowrap; }
tbody tr:hover { background: #fafcff; }
tbody tr.terminal { background: #fafafa; color: rgba(0, 0, 0, .45); }
.checkbox-cell { width: 42px; text-align: center; }
.requirement-link { max-width: 320px; height: auto; padding: 0; overflow: hidden; text-overflow: ellipsis; }
.terminal-note { margin-left: 6px; color: rgba(0, 0, 0, .45); font-size: 12px; }
.muted { color: rgba(0, 0, 0, .45); }
.pagination-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; color: rgba(0, 0, 0, .45); font-size: 13px; }
@media (max-width: 768px) {
  .page-context-row { align-items: flex-start; flex-direction: column; }
  .version-hero-card :deep(.ant-card-body) { padding: 18px 16px; }
  .summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
  .toolbar { align-items: stretch; }
  .toolbar :deep(.ant-input-search), .toolbar :deep(.ant-select) { width: 100%; }
  .toolbar-spacer { display: none; }
  .selected-info { flex: 1; align-self: center; }
  .pagination-row { align-items: flex-start; flex-direction: column; }
}
@media (max-width: 420px) { .summary-grid { grid-template-columns: 1fr; } }
</style>
