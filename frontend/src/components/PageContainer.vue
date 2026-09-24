<script setup lang="ts">
/**
 * 统一页面头部容器：标题 + 描述 + 操作槽 + 内容区。
 *
 * 替代各页面"Card 起手"的散乱写法，与 Ant Design Pro 的 PageContainer 对齐。
 * 由 App.vue 在内容区统一包裹，各页面只负责内容（页面 Card 不再写标题）。
 */
import { computed } from 'vue'
import { Button } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import PageRefreshBar from './PageRefreshBar.vue'
import { usePageRefreshShell } from '../composables/pageRefreshShell'

defineProps<{
  title?: string
  titleTag?: string
  description?: string
}>()

const { activePageRefresh } = usePageRefreshShell()
const refreshing = computed(() => activePageRefresh.value?.refreshing.value ?? false)
const lastUpdatedLabel = computed(() => {
  const value = activePageRefresh.value?.lastUpdatedAt.value
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(value)
})
const refreshCurrentPage = () => activePageRefresh.value?.refresh()
</script>

<template>
  <div class="page-container" :class="{ 'is-refreshing': refreshing }">
    <div v-if="title || description || $slots.extra" class="page-header">
      <div class="page-header-left">
        <h1 v-if="title" class="page-title">
          <span>{{ title }}</span>
          <span v-if="titleTag" class="page-title-tag">{{ titleTag }}</span>
        </h1>
        <p v-if="description" class="page-description">{{ description }}</p>
      </div>
      <div class="page-header-extra">
        <span v-if="lastUpdatedLabel" class="page-updated-at">更新于 {{ lastUpdatedLabel }}</span>
        <Button v-if="activePageRefresh" class="page-refresh-button" type="default" :loading="refreshing" title="刷新数据" aria-label="刷新数据" @click="refreshCurrentPage">
          <template #icon><ReloadOutlined /></template>
        </Button>
        <slot name="extra" />
      </div>
    </div>
    <PageRefreshBar :active="refreshing" />
    <div class="page-body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
}

.page-container.requirement-list-container {
  margin: 0;
}

.page-container.requirement-list-container .page-header {
  padding: 0 0 20px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.page-container.requirement-list-container .page-title {
  font-size: 22px;
  font-weight: 700;
}

.page-container.requirement-list-container .page-body {
  padding-top: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 24px;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
  border-radius: 8px;
}

.page-header-left {
  min-width: 0;
  flex: 1;
}

.page-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  color: rgba(0, 0, 0, 0.88);
}

.page-title-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
}

.page-description {
  margin: 4px 0 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
}

.page-header-extra {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-updated-at {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  white-space: nowrap;
}

.page-refresh-button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  padding: 0;
  border-radius: 8px;
}

.page-refresh-button :deep(.ant-btn-icon) { margin: 0; }
.page-refresh-button :deep(.anticon) { transition: transform .2s; }
.page-refresh-button:has(.ant-btn-loading-icon) :deep(.anticon) { animation: page-refresh-spin .8s linear infinite; }

.page-body {
  min-height: 0;
  padding-top: 20px;
  transition: opacity .25s ease;
}

.page-container.is-refreshing .page-body { opacity: .55; pointer-events: none; }

@keyframes page-refresh-spin { to { transform: rotate(360deg); } }

@media (max-width: 768px) {
  .page-title {
    font-size: 18px;
  }
  .page-header {
    gap: 12px;
    padding: 12px 16px;
  }
  .page-updated-at { display: none; }
}
</style>
