<script setup lang="ts">
import { computed, ref } from 'vue'
import { Card, Empty, Tag } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'
import { requirementStatusMeta } from '../constants/statusConfig'
import { urgencyMeta } from '../constants/urgencyConfig'

interface WorkbenchItem {
  id: number
  title: string
  requesterName: string
  systemName: string
  status: string
  urgency: string
  updatedAt: string
}

interface WorkbenchData {
  owned: WorkbenchItem[]
  assisting: WorkbenchItem[]
}

const router = useRouter()
const workbench = ref<WorkbenchData>({ owned: [], assisting: [] })
const ownedPage = ref(1)
const assistingPage = ref(1)
const pageSize = 6
const hasAssisting = computed(() => workbench.value.assisting.length > 0)
const pagedOwned = computed(() => workbench.value.owned.slice((ownedPage.value - 1) * pageSize, ownedPage.value * pageSize))
const pagedAssisting = computed(() => workbench.value.assisting.slice((assistingPage.value - 1) * pageSize, assistingPage.value * pageSize))

const loadDashboard = async () => {
  const { data } = await api.get<WorkbenchData>('/dashboard/workbench')
  workbench.value = data
  ownedPage.value = Math.min(ownedPage.value, Math.max(1, Math.ceil(data.owned.length / pageSize)))
  assistingPage.value = Math.min(assistingPage.value, Math.max(1, Math.ceil(data.assisting.length / pageSize)))
}

const { loaded } = usePageRefresh('dashboard', loadDashboard)

const openRequirement = (id: number) => {
  void router.push({ name: 'requirement-detail', params: { id } })
}

const formatUpdatedAt = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}).format(new Date(value))
</script>

<template>
  <section class="dashboard-shell" :class="{ 'has-assisting': hasAssisting }">
    <ContentSkeleton v-if="!loaded" preset="cards" :rows="6" />
    <div v-else class="workbench-grid" :class="{ 'with-assist': hasAssisting }">
      <Card class="workbench-card owned-card" :bordered="false">
        <template #title>
          <span class="workbench-heading">我负责的系统需求 <span>共 {{ workbench.owned.length }} 条 · 全部未完结</span></span>
        </template>
        <template v-if="workbench.owned.length">
          <div class="workbench-list">
            <button v-for="item in pagedOwned" :key="item.id" class="workbench-row" type="button" @click="openRequirement(item.id)">
              <span class="workbench-main">
                <span class="workbench-title">{{ item.title || '未命名需求' }}</span>
                <span class="workbench-meta">
                  <span class="workbench-system">{{ item.systemName }}</span>
                  <span>提出人：{{ item.requesterName || '—' }}</span>
                  <span>更新于 {{ formatUpdatedAt(item.updatedAt) }}</span>
                </span>
              </span>
              <span class="workbench-right">
                <span class="urgency-chip" :class="`urgency-${item.urgency.toLowerCase()}`">{{ urgencyMeta(item.urgency).label }}</span>
                <Tag class="status-tag" :color="requirementStatusMeta(item.status).color"><span></span>{{ requirementStatusMeta(item.status).label }}</Tag>
              </span>
            </button>
          </div>
          <div v-if="workbench.owned.length > pageSize" class="workbench-pagination">
            <AppPagination v-model:current="ownedPage" :page-size="pageSize" :total="workbench.owned.length" :show-size-changer="false" :show-less-items="true" responsive />
          </div>
        </template>
        <Empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无待处理需求" />
      </Card>

      <Card v-if="hasAssisting" class="workbench-card assisting-card" :bordered="false">
        <template #title>
          <span class="workbench-heading assist-heading">我协助处理的 <span>{{ workbench.assisting.length }} 条</span></span>
        </template>
        <div class="assisting-list">
          <button v-for="item in pagedAssisting" :key="item.id" class="assisting-row" type="button" @click="openRequirement(item.id)">
            <span class="workbench-main">
              <span class="workbench-title">{{ item.title || '未命名需求' }}</span>
              <span class="workbench-meta"><span class="workbench-system">{{ item.systemName }}</span><span>更新于 {{ formatUpdatedAt(item.updatedAt) }}</span></span>
            </span>
            <Tag class="status-tag" :color="requirementStatusMeta(item.status).color"><span></span>{{ requirementStatusMeta(item.status).label }}</Tag>
          </button>
        </div>
        <div v-if="workbench.assisting.length > pageSize" class="workbench-pagination">
          <AppPagination v-model:current="assistingPage" :page-size="pageSize" :total="workbench.assisting.length" :show-size-changer="false" :show-less-items="true" responsive />
        </div>
      </Card>
    </div>
  </section>
</template>

<style scoped>
.dashboard-shell { width: 100%; }

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.workbench-grid.with-assist { grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr); }

.workbench-card {
  overflow: hidden;
  border-radius: 10px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%);
}

.workbench-card :deep(.ant-card-head) {
  min-height: 52px;
  padding: 0 20px;
  border-bottom-color: #f0f0f0;
  background: linear-gradient(180deg, #fafcff, #fff);
}

.workbench-card :deep(.ant-card-head-title) { padding: 15px 0; }
.workbench-card :deep(.ant-card-body) { padding: 0; }

.workbench-heading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 15px;
  font-weight: 600;
}

.workbench-heading::before {
  width: 3px;
  height: 14px;
  border-radius: 2px;
  background: #1677ff;
  content: '';
}

.workbench-heading span { color: rgba(0, 0, 0, 0.45); font-size: 12px; font-weight: 400; }
.assist-heading::before { background: #b37feb; }

.workbench-list { min-height: 0; }
.workbench-pagination { display: flex; justify-content: flex-end; padding: 12px 16px; border-top: 1px solid #f0f0f0; }

.workbench-row,
.assisting-row {
  display: flex;
  align-items: center;
  width: 100%;
  border: 0;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: background-color .15s ease;
}

.workbench-row { min-height: 78px; gap: 16px; padding: 15px 20px; }
.assisting-row { min-height: 70px; gap: 12px; padding: 13px 16px; }
.workbench-row:last-child, .assisting-row:last-child { border-bottom: 0; }
.workbench-row:hover, .assisting-row:hover { background: #fafcff; }
.workbench-row:hover .workbench-title, .assisting-row:hover .workbench-title { color: #1677ff; }

.workbench-main { display: grid; min-width: 0; flex: 1; gap: 4px; }
.workbench-title { overflow: hidden; color: rgba(0, 0, 0, 0.88); font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.assisting-row .workbench-title { font-size: 13.5px; font-weight: 500; }

.workbench-meta { display: flex; flex-wrap: wrap; gap: 10px; overflow: hidden; color: rgba(0, 0, 0, 0.45); font-size: 12px; }
.workbench-system { color: #1677ff; font-weight: 500; }
.workbench-right { display: flex; align-items: center; flex-shrink: 0; gap: 10px; }

.urgency-chip {
  display: inline-grid;
  min-width: 22px;
  height: 22px;
  place-items: center;
  padding: 0 6px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.urgency-high { background: #fff1f0; color: #ff4d4f; }
.urgency-medium { background: #fff7e6; color: #d46b08; }
.urgency-low { background: #f5f5f5; color: rgba(0, 0, 0, .55); }

.status-tag { display: inline-flex; align-items: center; margin-inline-end: 0; white-space: nowrap; }
.status-tag span { width: 5px; height: 5px; margin-right: 5px; border-radius: 50%; background: currentcolor; }

@media (max-width: 1100px) {
  .workbench-grid.with-assist { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 640px) {
  .workbench-row { align-items: flex-start; }
  .workbench-right { flex-direction: column; align-items: flex-end; }
}
</style>
