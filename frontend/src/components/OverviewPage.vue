<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Empty } from 'ant-design-vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'
import OverviewDonutChart from './OverviewDonutChart.vue'

type SystemCount = { systemId: number | null; systemName: string; count: number }
type ResponsibleCount = { key: string; name: string; count: number }
type OverviewScope = 'unfinished' | 'all'
type OverviewData = {
  scope: OverviewScope
  total: number
  draftCount: number
  unfinishedCount: number
  pendingEvaluationCount: number
  inProgressCount: number
  completedCount: number
  completionRate: number
  highUrgencyPendingCount: number
  systemCounts: SystemCount[]
  responsibleCounts: ResponsibleCount[]
  urgencyCounts: Record<string, number>
}
const chartColors = ['#1677ff', '#69b1ff', '#36cfc9', '#597ef7', '#73d13d', '#ffc53d', '#ff85c0', '#d9d9d9']
const urgencyMeta: Record<string, { label: string; color: string; gradient: string }> = {
  HIGH: { label: '高', color: '#ff4d4f', gradient: 'linear-gradient(90deg, #ff7875, #ff4d4f)' },
  MEDIUM: { label: '中', color: '#fa8c16', gradient: 'linear-gradient(90deg, #ffc53d, #fa8c16)' },
  LOW: { label: '低', color: '#69b1ff', gradient: 'linear-gradient(90deg, #91caff, #69b1ff)' },
}

const router = useRouter()
const route = useRoute()
const scope = computed<OverviewScope>(() => route.query.scope === 'all' ? 'all' : 'unfinished')
const isUnfinishedScope = computed(() => scope.value === 'unfinished')
const overview = ref<OverviewData>({
  scope: 'unfinished',
  total: 0,
  draftCount: 0,
  unfinishedCount: 0,
  pendingEvaluationCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  completionRate: 0,
  highUrgencyPendingCount: 0,
  systemCounts: [],
  responsibleCounts: [],
  urgencyCounts: {},
})

const requirementListRoute = (filters: Record<string, string> = {}) => ({
  name: 'requirement-list', query: { scope: scope.value, ...filters },
})
const totalLabel = computed(() => isUnfinishedScope.value ? '未完成需求' : '全部需求')
const systemSegments = computed(() => overview.value.systemCounts.map((item, index) => ({
  key: String(item.systemId ?? 'unassigned'),
  label: item.systemName,
  count: item.count,
  color: chartColors[index % chartColors.length],
  to: requirementListRoute({ systemId: item.systemId == null ? 'none' : String(item.systemId) }),
})))
const responsibleSegments = computed(() => overview.value.responsibleCounts.map((item, index) => ({
  key: item.key,
  label: item.name,
  count: item.count,
  color: item.key === 'none' ? '#bfbfbf' : chartColors[index % chartColors.length],
  to: requirementListRoute({ responsible: item.key }),
})))

const urgencySegments = computed(() => {
  const max = Math.max(...Object.values(overview.value.urgencyCounts), 0)
  return ['HIGH', 'MEDIUM', 'LOW'].map((key) => {
    const count = overview.value.urgencyCounts[key] ?? 0
    return {
      key,
      count,
      ...urgencyMeta[key],
      percent: overview.value.total > 0 ? Math.round(count / overview.value.total * 100) : 0,
      width: max > 0 ? Math.round(count / max * 100) : 0,
    }
  })
})

const loadOverview = async () => {
  loadError.value = false
  while (true) {
    const requestedScope = scope.value
    try {
      const { data } = await api.get<OverviewData>('/dashboard/overview', { params: { scope: requestedScope } })
      if (requestedScope === scope.value) {
        if (data.scope !== requestedScope) {
          throw new Error('需求概览返回的统计范围与请求不一致')
        }
        if (!Array.isArray(data.responsibleCounts)) {
          throw new Error('需求概览缺少负责人统计数据')
        }
        overview.value = data
        return
      }
    } catch (error: unknown) {
      if (requestedScope === scope.value) {
        loadError.value = true
        throw error
      }
    }
  }
}

const loadError = ref(false)
const { loaded, refresh, refreshing } = usePageRefresh('overview', loadOverview)
const scopePending = computed(() => overview.value.scope !== scope.value)

watch(scope, () => {
  if (route.name === 'requirement-overview') void refresh()
})

const setScope = (nextScope: OverviewScope) => {
  void router.push({
    name: 'requirement-overview',
    query: { scope: nextScope },
  })
}

const openUrgencyRequirements = (urgency: string) => {
  void router.push(requirementListRoute({ urgency }))
}
</script>

<template>
  <section class="overview-page">
    <ContentSkeleton v-if="!loaded || (refreshing && scopePending)" preset="cards" :rows="8" />
    <div v-else-if="loadError" class="overview-empty">
      <Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="需求概览加载失败，请重试" />
      <button type="button" class="retry-button" @click="refresh">重新加载</button>
    </div>
    <template v-else>
      <div class="overview-toolbar">
        <div>
          <span class="overview-eyebrow">需求统计范围</span>
          <strong>{{ isUnfinishedScope ? '未完成需求' : '全部需求' }}</strong>
          <span class="overview-toolbar-hint">{{ isUnfinishedScope ? '仅统计正式提交且尚未进入终态的需求' : '包含草稿和正式需求' }}</span>
        </div>
        <nav class="scope-tabs" aria-label="需求概览范围">
          <button type="button" class="scope-tab" :class="{ active: isUnfinishedScope }" @click="setScope('unfinished')">未完成</button>
          <button type="button" class="scope-tab" :class="{ active: !isUnfinishedScope }" @click="setScope('all')">全部</button>
        </nav>
      </div>

      <div class="kpi-grid">
        <RouterLink class="kpi-card total-kpi" :to="requirementListRoute()" :title="`查看${totalLabel}列表`">
          <span class="kpi-label">{{ isUnfinishedScope ? '未完成需求' : '需求总数' }}</span>
          <strong>{{ overview.total }}</strong>
          <span class="kpi-foot">{{ isUnfinishedScope ? `草稿另有 ${overview.draftCount} 条` : `含草稿 ${overview.draftCount} 条` }}</span>
        </RouterLink>
        <RouterLink class="kpi-card unfinished-kpi" :to="requirementListRoute(isUnfinishedScope ? { status: 'PENDING_EVALUATION' } : { scope: 'unfinished' })" :title="isUnfinishedScope ? '查看待评估需求' : '查看未完结需求'">
          <span class="kpi-label">{{ isUnfinishedScope ? '待评估' : '未完结' }}</span>
          <strong>{{ isUnfinishedScope ? overview.pendingEvaluationCount : overview.unfinishedCount }}</strong>
          <span class="kpi-foot">{{ isUnfinishedScope ? '等待需求处理' : `待评估 ${overview.pendingEvaluationCount} · 处理中 ${overview.inProgressCount}` }}</span>
        </RouterLink>
        <RouterLink class="kpi-card completed-kpi" :to="requirementListRoute(isUnfinishedScope ? { status: 'IN_PROGRESS' } : { status: 'COMPLETED', saveType: 'SUBMITTED' })" :title="isUnfinishedScope ? '查看处理中需求' : '查看已完成需求'">
          <span class="kpi-label">{{ isUnfinishedScope ? '处理中' : '已完成' }}</span>
          <strong>{{ isUnfinishedScope ? overview.inProgressCount : overview.completedCount }}</strong>
          <span class="kpi-foot">{{ isUnfinishedScope ? '已确认 · 开发中 · 已暂停' : `完结率 ${overview.completionRate}%` }}</span>
        </RouterLink>
        <RouterLink class="kpi-card urgent-kpi" :to="requirementListRoute({ scope: 'unfinished', urgency: 'HIGH' })" title="查看高紧急度未完成需求">
          <span class="kpi-label">{{ isUnfinishedScope ? '高紧急度未完成' : '高紧急度待处理' }}</span>
          <strong>{{ overview.highUrgencyPendingCount }}</strong>
          <span class="kpi-foot">需优先跟进</span>
        </RouterLink>
      </div>

      <div class="chart-grid">
        <section class="chart-card">
          <header class="chart-header">
            <h2>按系统分布</h2>
            <span>{{ totalLabel }} · 点击圆环、数字或图例查看列表</span>
          </header>
          <OverviewDonutChart v-if="systemSegments.length" label="按系统分布环形图" :segments="systemSegments" :total="overview.total" :total-label="totalLabel" :total-to="requirementListRoute()" />
          <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无需求数据" />
        </section>

        <section class="chart-card">
          <header class="chart-header"><h2>按负责人分布</h2><span>{{ totalLabel }} · 点击圆环、数字或图例查看列表</span></header>
          <OverviewDonutChart v-if="responsibleSegments.length" label="按负责人分布环形图" :segments="responsibleSegments" :total="overview.total" :total-label="totalLabel" :total-to="requirementListRoute()" />
          <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="isUnfinishedScope ? '暂无未完成需求' : '暂无需求数据'" />
        </section>

        <section class="chart-card urgency-chart-card">
          <header class="chart-header"><h2>按紧急程度分布</h2><span>点击条目查看对应需求列表</span></header>
          <div class="urgency-bars">
            <button v-for="segment in urgencySegments" :key="segment.key" type="button" class="urgency-row clickable" :disabled="segment.count === 0" @click="openUrgencyRequirements(segment.key)">
              <span class="urgency-label">{{ segment.label }}</span>
              <span class="urgency-track"><span class="urgency-fill" :style="{ width: `${segment.width}%`, background: segment.gradient }"><span v-if="segment.count">{{ segment.count }}</span></span></span>
              <span class="urgency-count"><b>{{ segment.count }}</b> 条 · {{ segment.percent }}%</span>
            </button>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.overview-page { width: 100%; }

.overview-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; padding: 14px 18px; border: 1px solid #e6f4ff; border-radius: 10px; background: linear-gradient(100deg, #f5faff, #fff); }
.overview-toolbar > div:first-child { display: grid; align-items: baseline; grid-template-columns: auto auto; gap: 3px 10px; min-width: 0; }
.overview-eyebrow { grid-column: 1 / -1; color: rgba(0, 0, 0, .45); font-size: 12px; }
.overview-toolbar strong { color: rgba(0, 0, 0, .88); font-size: 17px; }
.overview-toolbar-hint { overflow: hidden; color: rgba(0, 0, 0, .45); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.overview-empty { display: grid; min-height: 360px; place-content: center; justify-items: center; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.retry-button { height: 30px; padding: 0 14px; border: 1px solid #91caff; border-radius: 6px; background: #e6f4ff; color: #1677ff; cursor: pointer; font-size: 13px; }
.retry-button:hover { border-color: #1677ff; }
.scope-tabs { display: inline-flex; flex: 0 0 auto; gap: 3px; padding: 3px; border-radius: 8px; background: #f0f2f5; }
.scope-tab { min-width: 66px; height: 30px; padding: 0 13px; border: 0; border-radius: 6px; background: transparent; color: rgba(0, 0, 0, .55); cursor: pointer; font-size: 13px; transition: all .2s; }
.scope-tab:hover { color: rgba(0, 0, 0, .88); }
.scope-tab.active { background: #fff; box-shadow: 0 1px 4px rgb(0 0 0 / 8%); color: #1677ff; font-weight: 600; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.kpi-card { position: relative; display: grid; gap: 6px; overflow: hidden; min-height: 130px; padding: 18px 20px; border-radius: 10px; background: #fff; color: inherit; text-decoration: none; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); transition: box-shadow .15s, transform .15s; }
.kpi-card:hover { transform: translateY(-2px); box-shadow: 0 4px 18px rgb(22 119 255 / 12%); }
.kpi-card:focus-visible { outline: 2px solid #1677ff; outline-offset: 3px; }
.kpi-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--accent); content: ''; }
.total-kpi { --accent: #1677ff; }
.unfinished-kpi { --accent: #faad14; }
.completed-kpi { --accent: #52c41a; }
.urgent-kpi { --accent: #ff4d4f; }
.kpi-label, .kpi-foot { color: rgba(0, 0, 0, .45); font-size: 13px; }
.kpi-card strong { color: rgba(0, 0, 0, .88); font-size: 28px; font-variant-numeric: tabular-nums; line-height: 1.1; }
.kpi-foot { font-size: 12px; }

.chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.chart-card { overflow: hidden; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%); }
.urgency-chart-card { grid-column: 1 / -1; }
.chart-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 52px; padding: 14px 20px; border-bottom: 1px solid #f0f0f0; background: linear-gradient(180deg, #fafcff, #fff); }
.chart-header h2 { display: flex; align-items: center; gap: 8px; margin: 0; color: rgba(0, 0, 0, .88); font-size: 15px; font-weight: 600; }
.chart-header h2::before { width: 3px; height: 14px; border-radius: 2px; background: #1677ff; content: ''; }
.chart-header span { color: rgba(0, 0, 0, .45); font-size: 12px; }

.urgency-bars { display: grid; gap: 18px; padding: 20px 24px 24px; }
.urgency-row { display: flex; align-items: center; gap: 14px; width: 100%; padding: 0; border: 0; background: transparent; color: inherit; text-align: left; }
.urgency-row.clickable { cursor: pointer; }
.urgency-row:disabled { cursor: default; }
.urgency-label { width: 64px; flex: 0 0 64px; color: rgba(0, 0, 0, .65); font-size: 13px; }
.urgency-track { height: 22px; flex: 1; overflow: hidden; border-radius: 6px; background: #f3f5f7; }
.urgency-fill { display: flex; height: 100%; min-width: 0; align-items: center; justify-content: flex-end; border-radius: 6px; color: #fff; font-size: 12px; font-weight: 600; transition: width .3s ease; }
.urgency-fill span { padding-right: 8px; }
.urgency-count { width: 78px; flex: 0 0 78px; color: rgba(0, 0, 0, .45); font-size: 13px; text-align: right; }
.urgency-count b { color: rgba(0, 0, 0, .88); font-size: 14px; font-variant-numeric: tabular-nums; }

@media (max-width: 1200px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .chart-grid { grid-template-columns: minmax(0, 1fr); }
  .urgency-chart-card { grid-column: auto; }
}

@media (max-width: 720px) {
  .overview-toolbar { align-items: flex-start; flex-direction: column; }
  .overview-toolbar > div:first-child { width: 100%; }
  .kpi-grid { grid-template-columns: minmax(0, 1fr); }
  .chart-header { align-items: flex-start; flex-direction: column; }
}
@media (prefers-reduced-motion: reduce) {
  .kpi-card { transition: none; }
}
</style>
