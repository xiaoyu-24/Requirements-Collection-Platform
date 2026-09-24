<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { Button, Card, Empty, Form, FormItem, Input, Modal, Select, Table, Tag, message } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { FilterOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta, requirementStatusOptions, saveTypeMeta } from '../constants/statusConfig'
import { apiErrorDetails, useApiError } from '../composables/useApiError'
import { urgencyMeta, urgencyOptions } from '../constants/urgencyConfig'
import { markChanged } from '../composables/refreshBus'
import { useDictionaryOptions } from '../composables/useDictionaryOptions'
import { usePageRefresh } from '../composables/usePageRefresh'
import { useAuth } from '../composables/useAuth'
import ContentSkeleton from './ContentSkeleton.vue'

type SystemItem = { id: number; name: string; status: string }
type VersionItem = { id: number; name: string; status: string }
type ResponsibleItem = { key: string; name: string; count: number }
type Item = {
  id: number; title: string; typeId: number | null; type: string | null; requesterName: string | null; departmentId: number | null; department: string | null; status: string | null
  submittedAt: string | null; systemId: number | null; targetVersionId: number | null; targetVersionName?: string | null; periodStartDate: string | null; periodEndDate: string | null
  completedAt?: string | null; handledBy?: string | null; completionDescription?: string | null
  systemOwnerName?: string | null; assigneeName?: string | null
  content?: string | null; urgency?: string | null; saveType?: string; updatedAt?: string | null; statusUpdatedAt?: string | null; recordVersion?: number
  requesterUserId?: number | null; systemName?: string | null
}

type Scope = 'unfinished' | 'all'
type FilterKey = 'keyword' | 'systemId' | 'targetVersionId' | 'departmentId' | 'requesterName' | 'responsible' | 'typeId' | 'status' | 'urgency' | 'saveType'
type FilterChip = { key: FilterKey; label: string; value: string }

const filters = reactive<Record<FilterKey | 'scope', string>>({
  keyword: '', systemId: '', targetVersionId: '', departmentId: '', requesterName: '', responsible: '', typeId: '', status: '', urgency: '', saveType: '', scope: 'unfinished',
})
const routeFilterKeys: Array<FilterKey | 'scope'> = ['keyword', 'systemId', 'targetVersionId', 'departmentId', 'requesterName', 'responsible', 'typeId', 'status', 'urgency', 'saveType', 'scope']
const router = useRouter()
const route = useRoute()
let syncingRouteFilters = false
let keywordTimer: ReturnType<typeof setTimeout> | undefined
const systems = ref<SystemItem[]>([])
const versions = ref<VersionItem[]>([])
const responsibles = ref<ResponsibleItem[]>([])
const items = ref<Item[]>([])
const total = ref(0)
const currentPage = ref(0)
const scopeCounts = ref({ unfinished: 0, all: 0 })
const pageSize = 10
const loading = ref(false)
const sortBy = ref('')
const sortDirection = ref<'asc' | 'desc'>('asc')
const filterPanelOpen = ref(false)
const keywordInput = ref<{ focus?: () => void } | null>(null)
const { departments, requirementTypes, loadDictionaryOptions } = useDictionaryOptions()
const { currentUser, isHandler } = useAuth()
const { handleError } = useApiError()

const progressModalOpen = ref(false)
const progressLoading = ref(false)
const progressForm = ref({ content: '', status: '' })
const progressError = ref('')
const progressTarget = ref<Item | null>(null)
const progressStatusMeta = computed(() => {
  const item = progressTarget.value
  if (!item) return { label: '—', color: 'default' }
  return item.status ? requirementStatusMeta(item.status) : saveTypeMeta(item.saveType ?? 'DRAFT')
})

const systemFilterOptions = computed(() => [
  { label: '全部系统', value: '' },
  { label: '暂无系统', value: 'none' },
  ...systems.value.map((system) => ({ label: system.name, value: String(system.id) })),
])
const versionFilterOptions = computed(() => [
  { label: '全部版本', value: '' },
  ...versions.value.map((version) => ({ label: version.name, value: String(version.id) })),
])
const responsibleOptions = computed(() => {
  const options = responsibles.value.map(item => ({ label: item.name, value: item.key }))
  if (filters.responsible && !options.some(option => option.value === filters.responsible)) {
    const label = filters.responsible === 'none' ? '未分配负责人'
      : filters.responsible.startsWith('name:') ? filters.responsible.slice(5) : '所选负责人（暂无需求）'
    options.push({ label, value: filters.responsible })
  }
  return [{ label: '全部负责人', value: '' }, ...options]
})
const departmentFilterOptions = computed(() => [
  { label: '全部部门', value: '' },
  ...departments.value.map((department) => ({ label: department.name, value: String(department.id) })),
])
const typeOptions = computed(() => [
  { label: '全部类型', value: '' },
  ...requirementTypes.value.map((type) => ({ label: type.name, value: String(type.id) })),
])
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '待评估', value: 'PENDING_EVALUATION' },
  { label: '处理中（已确认、开发中、暂停）', value: 'IN_PROGRESS' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '开发中', value: 'IN_DEVELOPMENT' },
  { label: '暂停', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' },
]
const saveTypeOptions = [
  { label: '全部', value: '' },
  { label: '正式需求', value: 'SUBMITTED' },
  { label: '草稿', value: 'DRAFT' },
]
const urgencyFilterOptions = [{ label: '全部紧急程度', value: '' }, ...urgencyOptions]
const requirementColumns = computed<TableColumnsType<Item>>(() => [
  { title: '类型', key: 'type', width: 90 },
  { title: '需求标题', key: 'title', width: 240 },
  { title: '所属系统 / 版本', key: 'systemVersion', width: 150 },
  { title: '填写人 / 部门', key: 'requester', width: 140 },
  { title: '负责人', key: 'responsible', width: 140 },
  { title: '周期', key: 'period', width: 180 },
  { title: '紧急程度', key: 'urgency', width: 90, sorter: true, sortOrder: sortBy.value === 'urgency' ? (sortDirection.value === 'asc' ? 'ascend' : 'descend') : null },
  { title: '状态', key: 'status', width: 100 },
  { title: '填写时间', key: 'submittedAt', width: 110 },
  { title: '操作', key: 'actions', fixed: 'right' as const, width: 260 },
])
const tableRecord = (record: Record<string, unknown>) => record as Item
const systemName = (item: Item) => item.systemId === null ? '暂无系统' : item.systemName ?? systems.value.find((system) => system.id === item.systemId)?.name ?? `系统 #${item.systemId}`
const versionName = (id: number | null, name?: string | null) => id === null ? '—' : name ?? versions.value.find((version) => version.id === id)?.name ?? `版本 #${id}`
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const match = value.match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] ?? value.slice(0, 10)
}
const contentSummary = (content: string | null | undefined) => {
  const normalized = content?.replace(/\s+/g, ' ').trim() ?? ''
  return normalized.length > 50 ? `${normalized.slice(0, 50)}...` : normalized
}
const statusTagClass = (status: string | null, saveType?: string) => {
  if (!status) return saveType === 'DRAFT' ? 'list-tag-gray' : 'list-tag-blue'
  return ({
    PENDING_EVALUATION: 'list-tag-gold',
    CONFIRMED: 'list-tag-blue',
    IN_DEVELOPMENT: 'list-tag-blue',
    PAUSED: 'list-tag-orange',
    COMPLETED: 'list-tag-green',
    REJECTED: 'list-tag-red',
    CLOSED: 'list-tag-gray',
  } as Record<string, string>)[status] ?? 'list-tag-gray'
}
const urgencyClass = (urgency: string | null | undefined) => `urgency-${(urgency ?? 'MEDIUM').toLowerCase()}`

const systemFilterLabel = (value: string) => value === 'none' ? '暂无系统' : systems.value.find((item) => String(item.id) === value)?.name ?? value
const optionLabel = (options: Array<{ label: string; value: string }>, value: string) => options.find((item) => item.value === value)?.label ?? value
const activeFilterCount = computed(() => routeFilterKeys
  .filter((key): key is FilterKey => key !== 'keyword' && key !== 'scope')
  .filter((key) => Boolean(filters[key])).length)
const filterChips = computed<FilterChip[]>(() => {
  const chips: FilterChip[] = []
  if (filters.keyword) chips.push({ key: 'keyword', label: '关键词', value: filters.keyword })
  if (filters.systemId) chips.push({ key: 'systemId', label: '所属系统', value: systemFilterLabel(filters.systemId) })
  if (filters.targetVersionId) chips.push({ key: 'targetVersionId', label: '目标版本', value: optionLabel(versionFilterOptions.value, filters.targetVersionId) })
  if (filters.departmentId) chips.push({ key: 'departmentId', label: '部门', value: optionLabel(departmentFilterOptions.value, filters.departmentId) })
  if (filters.requesterName) chips.push({ key: 'requesterName', label: '填写人', value: filters.requesterName })
  if (filters.responsible) chips.push({ key: 'responsible', label: '负责人', value: optionLabel(responsibleOptions.value, filters.responsible) })
  if (filters.typeId) chips.push({ key: 'typeId', label: '类型', value: optionLabel(typeOptions.value, filters.typeId) })
  if (filters.status) chips.push({ key: 'status', label: '状态', value: optionLabel(statusOptions, filters.status) })
  if (filters.urgency) chips.push({ key: 'urgency', label: '紧急程度', value: optionLabel(urgencyFilterOptions, filters.urgency) })
  if (filters.saveType) chips.push({ key: 'saveType', label: '保存类型', value: optionLabel(saveTypeOptions, filters.saveType) })
  return chips
})

const loadSystems = async () => {
  const { data } = await api.get('/systems')
  systems.value = Array.isArray(data) ? data : []
}

const loadResponsibles = async () => {
  const { data } = await api.get('/requirements/responsibles')
  responsibles.value = Array.isArray(data) ? data : []
}

const loadVersions = async (clearSelectedVersion = true) => {
  if (clearSelectedVersion) filters.targetVersionId = ''
  versions.value = []
  if (!filters.systemId || filters.systemId === 'none') return
  const { data } = await api.get(`/systems/${filters.systemId}/versions`)
  versions.value = Array.isArray(data) ? data : []
}

const queryParams = (page: number, scopeOverride?: Scope) => {
  const params: Record<string, string | number | boolean> = { page, size: pageSize }
  const scope = scopeOverride ?? filters.scope as Scope
  if (filters.systemId === 'none') params.unassignedSystem = true
  else if (filters.systemId) params.systemId = Number(filters.systemId)
  if (filters.targetVersionId) params.targetVersionId = Number(filters.targetVersionId)
  if (filters.departmentId) params.departmentId = Number(filters.departmentId)
  if (filters.typeId) params.typeId = Number(filters.typeId)
  if (scope === 'unfinished') params.unfinishedOnly = true
  if (sortBy.value === 'urgency') {
    params.sortBy = sortBy.value
    params.sortDirection = sortDirection.value
  }
  if (filters.status === 'IN_PROGRESS') params.inProgressOnly = true
  else if (filters.status) params.status = filters.status
  for (const key of ['keyword', 'requesterName', 'responsible', 'urgency', 'saveType'] as const) {
    if (filters[key]) params[key] = filters[key]
  }
  return params
}

const queryRequirements = async (page = 0) => {
  loading.value = true
  try {
    const { data } = await api.get('/requirements/page', { params: queryParams(page) })
    items.value = Array.isArray(data.content) ? data.content : []
    total.value = data.totalElements
    currentPage.value = page
  } finally {
    loading.value = false
  }
}

const loadScopeCounts = async () => {
  const base = queryParams(0)
  delete base.unfinishedOnly
  const [unfinishedResponse, allResponse] = await Promise.all([
    api.get('/requirements/page', { params: { ...base, size: 1, unfinishedOnly: true } }),
    api.get('/requirements/page', { params: { ...base, size: 1 } }),
  ])
  scopeCounts.value = {
    unfinished: Number(unfinishedResponse.data.totalElements ?? 0),
    all: Number(allResponse.data.totalElements ?? 0),
  }
}

const routeQueryForFilters = (page: number) => {
  const query: Record<string, string> = {}
  for (const key of routeFilterKeys) {
    if (filters[key]) query[key] = filters[key]
  }
  if (sortBy.value === 'urgency') {
    query.sortBy = sortBy.value
    query.sortDirection = sortDirection.value
  }
  if (page > 0) query.page = String(page + 1)
  return query
}

const querySignature = (query: Record<string, unknown>) => JSON.stringify(
  Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => [key, Array.isArray(value) ? value.join(',') : String(value)])
    .sort(([left], [right]) => left.localeCompare(right)),
)

const applyFilters = async (page = 0) => {
  const query = routeQueryForFilters(page)
  if (route.name === 'requirement-list' && querySignature(route.query) === querySignature(query)) {
    await refresh()
    return
  }
  await router.push({ name: 'requirement-list', query })
}

const resetFilters = () => {
  Object.assign(filters, { keyword: '', systemId: '', targetVersionId: '', departmentId: '', requesterName: '', responsible: '', typeId: '', status: '', urgency: '', saveType: '' })
  versions.value = []
  void applyFilters()
}

const clearFilter = (key: FilterKey) => {
  filters[key] = ''
  if (key === 'systemId') {
    filters.targetVersionId = ''
    versions.value = []
  }
  void applyFilters()
}

const setScope = (scope: Scope) => {
  filters.scope = scope
  if (scope === 'unfinished' && ['COMPLETED', 'REJECTED', 'CLOSED'].includes(filters.status)) filters.status = ''
  void applyFilters()
}

const onStatusChange = (status: unknown) => {
  if (typeof status === 'string' && ['COMPLETED', 'REJECTED', 'CLOSED'].includes(status)) filters.scope = 'all'
  void applyFilters()
}

const onSystemChange = async () => {
  await loadVersions()
  await applyFilters()
}

const scheduleTextFilter = () => {
  if (syncingRouteFilters) return
  if (keywordTimer !== undefined) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => { void applyFilters() }, 300)
}

const handleTableChange = (_pagination: unknown, _filters: unknown, sorter: { field?: unknown; order?: unknown } | Array<{ field?: unknown; order?: unknown }>) => {
  const currentSorter = Array.isArray(sorter) ? sorter[0] : sorter
  if (currentSorter?.field !== 'urgency') return
  if (currentSorter.order === 'ascend') {
    sortBy.value = 'urgency'
    sortDirection.value = 'asc'
  } else if (currentSorter.order === 'descend') {
    sortBy.value = 'urgency'
    sortDirection.value = 'desc'
  } else {
    sortBy.value = ''
  }
  void applyFilters()
}

const viewDetails = (id: number) => { void router.push({ name: 'requirement-detail', params: { id } }) }
const editDetails = (id: number) => { void router.push({ name: 'requirement-detail', params: { id }, query: { edit: '1' } }) }

const deleteRequirement = (item: Item) => {
  Modal.confirm({
    title: '删除需求',
    content: `确定删除需求“${item.title || '未命名草稿'}”吗？`,
    okText: '删除',
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.delete(`/requirements/${item.id}`)
        message.success('需求已删除')
        await queryRequirements(currentPage.value)
        await loadScopeCounts()
        markChanged(['requirements', 'dashboard', 'overview'])
      } catch (error: unknown) {
        message.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除需求失败，请稍后重试')
      }
    },
  })
}

const owns = (item: Item) => currentUser.value?.id === item.requesterUserId

const openProgressModal = (item: Item) => {
  progressTarget.value = item
  progressForm.value = { content: '', status: '' }
  progressError.value = ''
  progressModalOpen.value = true
}

const saveProgress = async () => {
  const item = progressTarget.value
  if (!item) return
  const content = progressForm.value.content.trim()
  progressError.value = ''
  if (!content) {
    progressError.value = '请输入进展内容'
    return
  }

  progressLoading.value = true
  try {
    await api.post(`/requirements/${item.id}/progresses`, {
      content,
      status: progressForm.value.status || null,
      recordVersion: item.recordVersion ?? 0,
    })
    progressModalOpen.value = false
    message.success('进展已更新')
    markChanged(['requirements', 'dashboard', 'overview'])
    await queryRequirements(currentPage.value)
    await loadScopeCounts()
  } catch (error: unknown) {
    progressError.value = apiErrorDetails(error).fieldErrors.content || ''
    handleError(error, '填写进度失败，请稍后重试')
  } finally {
    progressLoading.value = false
  }
}

const canEdit = (item: Item) => isHandler.value || (
  owns(item)
  && (item.saveType === 'DRAFT' || ['PENDING_EVALUATION', 'CONFIRMED'].includes(item.status ?? ''))
)
const canDelete = (item: Item) => isHandler.value || (owns(item) && (item.saveType === 'DRAFT' || item.status === 'PENDING_EVALUATION'))
const routeQueryValue = (value: unknown) => Array.isArray(value) ? value[0] ?? '' : typeof value === 'string' ? value : ''
const defaultScopeForRoute = () => {
  const status = routeQueryValue(route.query.status)
  const saveType = routeQueryValue(route.query.saveType)
  return ['COMPLETED', 'REJECTED', 'CLOSED'].includes(status) || saveType === 'DRAFT' ? 'all' : 'unfinished'
}
const routePage = () => {
  const page = Number(routeQueryValue(route.query.page))
  return Number.isSafeInteger(page) && page > 0 ? page - 1 : 0
}

const syncFiltersFromRoute = async () => {
  if (route.name !== 'requirement-list') return
  if (!routeQueryValue(route.query.scope)) {
    await router.replace({ name: 'requirement-list', query: { ...route.query, scope: defaultScopeForRoute() } })
  }
  syncingRouteFilters = true
  for (const key of routeFilterKeys) filters[key] = routeQueryValue(route.query[key])
  filters.scope = filters.scope === 'all' ? 'all' : 'unfinished'
  sortBy.value = routeQueryValue(route.query.sortBy) === 'urgency' ? 'urgency' : ''
  sortDirection.value = routeQueryValue(route.query.sortDirection) === 'desc' ? 'desc' : 'asc'
  await loadVersions(false)
  await nextTick()
  syncingRouteFilters = false
  await Promise.all([queryRequirements(routePage()), loadScopeCounts()])
}

watch(() => filters.keyword, scheduleTextFilter)
watch(() => filters.requesterName, scheduleTextFilter)
watch(() => route.fullPath, () => {
  if (route.name === 'requirement-list' && loaded.value) void refresh()
})

const { loaded, refresh, refreshing } = usePageRefresh('requirements', async () => {
  await Promise.all([loadSystems(), loadDictionaryOptions(), loadResponsibles()])
  await syncFiltersFromRoute()
})

const toggleFilterPanel = () => { filterPanelOpen.value = !filterPanelOpen.value }
const handleShortcut = (event: KeyboardEvent) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    keywordInput.value?.focus?.()
  }
}
onMounted(() => window.addEventListener('keydown', handleShortcut))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleShortcut)
  if (keywordTimer !== undefined) clearTimeout(keywordTimer)
})
</script>

<template>
  <section class="list-page">
    <ContentSkeleton v-if="!loaded" preset="table" :rows="5" />
    <template v-else>
      <section class="filter-surface">
        <nav class="scope-tabs" aria-label="需求范围">
          <button type="button" class="scope-tab" :class="{ active: filters.scope === 'unfinished' }" @click="setScope('unfinished')">未完结 <span class="tab-count">{{ scopeCounts.unfinished }}</span></button>
          <button type="button" class="scope-tab" :class="{ active: filters.scope === 'all' }" @click="setScope('all')">全部 <span class="tab-count">{{ scopeCounts.all }}</span></button>
        </nav>
        <div class="tabs-divider"></div>
        <div class="filter-bar">
          <Input ref="keywordInput" v-model:value="filters.keyword" class="keyword-input" allow-clear placeholder="搜索标题或需求内容…" @press-enter="applyFilters()">
            <template #prefix><SearchOutlined /></template>
          </Input>
          <Button class="filter-toggle" :class="{ active: filterPanelOpen || activeFilterCount > 0 }" @click="toggleFilterPanel">
            <template #icon><FilterOutlined /></template>
            筛选
            <span v-if="activeFilterCount" class="filter-badge">{{ activeFilterCount }}</span>
          </Button>
        </div>
        <div v-if="filterChips.length" class="chip-row">
          <button v-for="chip in filterChips" :key="chip.key" type="button" class="filter-chip" @click="clearFilter(chip.key)">
            {{ chip.label }}：{{ chip.value }} <span aria-hidden="true">×</span>
          </button>
          <button type="button" class="clear-all" @click="resetFilters">清空全部</button>
        </div>
        <div v-if="filterPanelOpen" class="filter-panel">
          <div class="filter-grid">
            <label class="filter-field"><span>需求状态</span><Select v-model:value="filters.status" :options="statusOptions" @change="onStatusChange" /></label>
            <label class="filter-field"><span>紧急程度</span><Select v-model:value="filters.urgency" :options="urgencyFilterOptions" @change="() => applyFilters()" /></label>
            <label class="filter-field"><span>所属系统</span><Select v-model:value="filters.systemId" data-test="system-filter" :options="systemFilterOptions" @change="onSystemChange" /></label>
            <label class="filter-field"><span>目标版本</span><Select v-model:value="filters.targetVersionId" :options="versionFilterOptions" :disabled="!filters.systemId || filters.systemId === 'none'" @change="() => applyFilters()" /></label>
            <label class="filter-field"><span>部门</span><Select v-model:value="filters.departmentId" data-test="department-filter" :options="departmentFilterOptions" @change="() => applyFilters()" /></label>
            <label class="filter-field"><span>填写人</span><Input v-model:value="filters.requesterName" placeholder="请输入填写人" allow-clear @press-enter="applyFilters()" /></label>
            <label class="filter-field"><span>负责人</span><Select v-model:value="filters.responsible" data-test="responsible-filter" :options="responsibleOptions" show-search option-filter-prop="label" @change="() => applyFilters()" /></label>
            <label class="filter-field"><span>类型</span><Select v-model:value="filters.typeId" :options="typeOptions" @change="() => applyFilters()" /></label>
            <label class="filter-field"><span>保存类型</span><Select v-model:value="filters.saveType" :options="saveTypeOptions" @change="() => applyFilters()" /></label>
          </div>
          <div class="filter-panel-footer">
            <span>选择后立即生效，无需点击查询</span>
            <Button @click="resetFilters">重置</Button>
            <Button type="primary" @click="filterPanelOpen = false">完成</Button>
          </div>
        </div>
      </section>

      <Card class="list-table-card" :bordered="false">
        <Table class="requirement-table" :columns="requirementColumns" :data-source="items" :pagination="false" :scroll="{ x: 1500 }" :row-key="(item: Item) => item.id" @change="handleTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'type'"><span class="list-tag list-tag-gray">{{ record.type || '—' }}</span></template>
            <template v-else-if="column.key === 'title'"><div class="requirement-title-cell"><RouterLink class="requirement-title-link" :to="{ name: 'requirement-detail', params: { id: tableRecord(record).id } }">{{ record.title || '未命名草稿' }}</RouterLink><span v-if="tableRecord(record).content" class="requirement-content-summary" :title="tableRecord(record).content ?? ''">{{ contentSummary(tableRecord(record).content) }}</span></div></template>
            <template v-else-if="column.key === 'systemVersion'">{{ systemName(tableRecord(record)) }} / {{ versionName(record.targetVersionId, record.targetVersionName) }}</template>
            <template v-else-if="column.key === 'requester'">{{ record.requesterName || '—' }} / {{ record.department || '—' }}</template>
            <template v-else-if="column.key === 'responsible'"><span class="responsible-cell"><span>{{ record.assigneeName || record.systemOwnerName || '—' }}</span><small v-if="record.assigneeName && record.systemOwnerName">系统负责人：{{ record.systemOwnerName }}</small></span></template>
            <template v-else-if="column.key === 'period'">{{ record.periodStartDate && record.periodEndDate ? `${record.periodStartDate} 至 ${record.periodEndDate}` : '—' }}</template>
            <template v-else-if="column.key === 'urgency'"><span :class="['urgency-badge', urgencyClass(record.urgency)]">
              {{ urgencyMeta(record.urgency).label }}
            </span></template>
            <template v-else-if="column.key === 'status'"><span :class="['list-tag', statusTagClass(record.status, record.saveType)]"><span class="tag-dot"></span>{{ record.status ? requirementStatusMeta(record.status).label : saveTypeMeta(record.saveType ?? 'DRAFT').label }}</span></template>
            <template v-else-if="column.key === 'submittedAt'">{{ formatShanghai(record.submittedAt || record.updatedAt) }}</template>
            <template v-else-if="column.key === 'actions'"><div class="table-actions"><Button type="link" size="small" :data-test="`view-${record.id}`" @click="viewDetails(tableRecord(record).id)">查看详情</Button><Button v-if="canEdit(tableRecord(record))" type="link" size="small" :data-test="`edit-${record.id}`" @click="editDetails(tableRecord(record).id)">编辑</Button><Button v-if="isHandler" type="link" size="small" :data-test="`progress-${record.id}`" @click="openProgressModal(tableRecord(record))">填写进度</Button><Button v-if="canDelete(tableRecord(record))" danger type="link" size="small" :data-test="`delete-${record.id}`" @click="deleteRequirement(tableRecord(record))">删除</Button></div></template>
          </template>
          <template #emptyText><Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无需求" /></template>
        </Table>
        <div class="pagination-bar"><span>共 {{ total }} 条</span><AppPagination class="list-pagination" :current="currentPage + 1" :total="total" :page-size="pageSize" :show-size-changer="false" :disabled="refreshing" @change="(page) => applyFilters(page - 1)" /></div>
      </Card>

      <Modal v-model:open="progressModalOpen" :title="`填写进度：${progressTarget?.title || '未命名草稿'}`" :footer="null" :mask-closable="false" destroy-on-close :get-container="false" @cancel="progressModalOpen = false">
        <Form class="progress-update-form" layout="vertical" :model="progressForm" @submit.prevent="saveProgress">
          <FormItem label="当前状态"><Tag :color="progressStatusMeta.color">{{ progressStatusMeta.label }}</Tag></FormItem>
          <FormItem label="更新后状态（可选）">
            <Select v-model:value="progressForm.status" data-test="progress-status" allow-clear placeholder="不修改状态" :options="requirementStatusOptions" />
          </FormItem>
          <FormItem label="进展内容" required :validate-status="progressError ? 'error' : undefined" :help="progressError || undefined">
            <Input.TextArea v-model:value="progressForm.content" data-test="progress-content" :rows="6" placeholder="请输入当前进展、处理结果或补充说明" />
          </FormItem>
          <div class="progress-form-actions">
            <Button html-type="button" @click="progressModalOpen = false">取消</Button>
            <Button type="primary" html-type="submit" data-test="save-progress" :loading="progressLoading">提交进度</Button>
          </div>
        </Form>
      </Modal>
    </template>
  </section>
</template>

<style scoped>
.list-page { width: 100%; }
.filter-surface, .list-table-card { overflow: hidden; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.filter-surface { margin-bottom: 16px; }
.scope-tabs { display: flex; align-items: center; gap: 4px; padding: 10px 16px 0; }
.scope-tab { position: relative; padding: 10px 14px; border: 0; border-radius: 8px 8px 0 0; background: transparent; color: rgba(0, 0, 0, .65); cursor: pointer; font-size: 14px; transition: color .2s; }
.scope-tab:hover { color: rgba(0, 0, 0, .88); }
.scope-tab.active { color: #1677ff; font-weight: 600; }
.scope-tab.active::after { position: absolute; right: 12px; bottom: 0; left: 12px; height: 2.5px; border-radius: 2px; background: #1677ff; content: ''; }
.scope-tab .tab-count { display: inline-grid; min-width: 22px; height: 20px; margin-left: 5px; place-items: center; padding: 1px 7px; border-radius: 9px; background: #f0f2f5; color: rgba(0, 0, 0, .45); font-size: 11px; font-weight: 500; }
.scope-tab.active .tab-count { background: #e6f4ff; color: #1677ff; }
.tabs-divider { border-bottom: 1px solid #f0f0f0; }
.filter-bar { display: flex; align-items: center; gap: 10px; padding: 14px 16px; }
.keyword-input { max-width: 380px; height: 34px; flex: 1; }
.keyword-input :deep(.ant-input-affix-wrapper) { height: 34px; padding: 0 12px; }
.keyword-input :deep(.ant-input) { height: 32px; font-size: 13px; }
.keyword-input :deep(.ant-input-affix-wrapper) { display: flex; align-items: center; border-radius: 8px; }
.keyword-input :deep(.ant-input) { height: auto; padding: 0; line-height: 22px; }
.keyword-input :deep(.ant-input-prefix) { display: inline-flex; align-items: center; margin-right: 8px; }
.filter-toggle { position: relative; height: 34px; padding: 0 16px; }
.filter-toggle.active { border-color: #1677ff; background: #e6f4ff; color: #1677ff; }
.filter-badge { position: absolute; top: -7px; right: -7px; display: grid; min-width: 17px; height: 17px; place-items: center; padding: 0 4px; border: 2px solid #fff; border-radius: 10px; background: #1677ff; color: #fff; font-size: 10px; font-weight: 600; }
.chip-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 0 16px 14px; }
.filter-chip, .clear-all { border: 0; cursor: pointer; font-size: 12.5px; }
.filter-chip { height: 26px; padding: 0 10px; border: 1px solid #91caff; border-radius: 13px; background: #e6f4ff; color: #0958d9; }
.filter-chip:hover { border-color: #1677ff; }
.filter-chip span { margin-left: 5px; color: #69b1ff; font-size: 14px; }
.clear-all { color: rgba(0, 0, 0, .45); }
.clear-all:hover { color: #ff4d4f; }
.filter-panel { padding: 16px; border-top: 1px dashed #f0f0f0; background: #fafbfc; }
.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px 16px; }
.filter-field { display: grid; gap: 6px; min-width: 0; color: rgba(0, 0, 0, .45); font-size: 12px; }
.filter-field :deep(.ant-select), .filter-field :deep(.ant-input) { width: 100%; }
.filter-field :deep(.ant-select-selector), .filter-field :deep(.ant-input) { height: 32px !important; border-radius: 8px !important; font-size: 13px; }
.filter-field :deep(.ant-select-selection-item), .filter-field :deep(.ant-select-selection-placeholder) { line-height: 30px !important; }
.filter-panel-footer { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 14px; }
.filter-panel-footer > span { margin-right: auto; color: rgba(0, 0, 0, .45); font-size: 12px; }
.list-table-card :deep(.ant-card-body) { padding: 0; }
.requirement-table :deep(.ant-table-thead > tr > th) { padding: 12px 16px; border-top: 1px solid #f0f0f0; border-bottom: 1px solid #f0f0f0; background: #fafbfc; color: rgba(0, 0, 0, .65); font-size: 13px; font-weight: 600; white-space: nowrap; }
.requirement-table :deep(.ant-table-tbody > tr) { transition: background .15s; }
.requirement-table :deep(.ant-table-tbody > tr:hover > td) { background: #fafcff !important; }
.requirement-table :deep(.ant-table-tbody > tr > td) { padding: 13px 16px; border-bottom: 1px solid #f0f0f0; font-size: 14px; vertical-align: middle; }
.requirement-table :deep(.ant-table-tbody > tr:last-child > td) { border-bottom: 0; }
.requirement-title-cell { display: grid; gap: 4px; min-width: 0; }
.requirement-title-link { overflow: hidden; color: #1677ff; font-weight: 500; text-decoration: none; text-overflow: ellipsis; white-space: nowrap; }
.requirement-title-link:hover { text-decoration: underline; }
.requirement-content-summary { overflow: hidden; color: rgba(0, 0, 0, .55); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.responsible-cell { display: grid; gap: 2px; min-width: 0; color: rgba(0, 0, 0, .75); }
.responsible-cell small { overflow: hidden; color: rgba(0, 0, 0, .45); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.list-tag { display: inline-flex; align-items: center; gap: 5px; height: 22px; padding: 0 9px; border: 1px solid transparent; border-radius: 11px; font-size: 12px; font-weight: 500; white-space: nowrap; }
.list-tag-gray { border-color: #e8e8e8; background: #f5f5f5; color: rgba(0, 0, 0, .65); }
.list-tag-gold { border-color: #ffe58f; background: #fffbe6; color: #d48806; }
.list-tag-blue { border-color: #91caff; background: #e6f4ff; color: #1677ff; }
.list-tag-green { border-color: #b7eb8f; background: #f6ffed; color: #389e0d; }
.list-tag-orange { border-color: #ffd591; background: #fff7e6; color: #d46b08; }
.list-tag-red { border-color: #ffccc7; background: #fff1f0; color: #cf1322; }
.tag-dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.urgency-badge { display: inline-grid; min-width: 22px; height: 22px; place-items: center; padding: 0 6px; border-radius: 6px; font-size: 12px; font-weight: 600; }
.urgency-high { background: #fff1f0; color: #cf1322; }
.urgency-medium { background: #fff7e6; color: #d46b08; }
.urgency-low { background: #f5f5f5; color: rgba(0, 0, 0, .65); }
.table-actions { display: flex; gap: 2px; align-items: center; white-space: nowrap; }
.table-actions :deep(.ant-btn) { height: 26px; padding: 2px 8px; border-radius: 6px; color: #1677ff; font-size: 13px; }
.table-actions :deep(.ant-btn:hover) { background: #e6f4ff; }
.table-actions :deep(.ant-btn-dangerous) { color: #ff4d4f; }
.table-actions :deep(.ant-btn-dangerous:hover) { background: #fff1f0; }
.progress-form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.pagination-bar { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; color: rgba(0, 0, 0, .45); font-size: 13px; }
.list-pagination :deep(.ant-pagination-item) { border-radius: 7px; }
.list-pagination :deep(.ant-pagination-item-active) { border-color: #1677ff; background: #e6f4ff; }
.list-pagination :deep(.ant-pagination-item-active a) { color: #1677ff; font-weight: 600; }

@media (max-width: 1200px) { .filter-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 900px) { .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) {
  .filter-bar { align-items: stretch; flex-direction: column; }
  .keyword-input { max-width: none; }
  .filter-grid { grid-template-columns: minmax(0, 1fr); }
  .filter-panel-footer { align-items: flex-end; flex-wrap: wrap; }
  .filter-panel-footer > span { width: 100%; margin-right: 0; }
}
</style>
