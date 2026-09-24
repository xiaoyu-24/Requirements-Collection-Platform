<script setup lang="ts">
import { computed } from 'vue'

type SkeletonPreset = 'table' | 'cards' | 'detail' | 'form'

const props = withDefaults(defineProps<{
  preset?: SkeletonPreset
  rows?: number
}>(), {
  preset: 'table',
  rows: 5,
})

const rowIndexes = computed(() => Array.from({ length: props.rows }, (_, index) => index))
</script>

<template>
  <div class="content-skeleton" :class="`is-${preset}`" aria-label="正在加载数据">
    <template v-if="preset === 'cards'">
      <div v-for="index in rowIndexes" :key="index" class="skeleton-card">
        <span class="skeleton-block skeleton-title"></span>
        <span class="skeleton-block skeleton-line wide"></span>
        <span class="skeleton-block skeleton-line"></span>
      </div>
    </template>
    <template v-else-if="preset === 'detail' || preset === 'form'">
      <section class="skeleton-surface">
        <span class="skeleton-block skeleton-heading"></span>
        <div v-for="index in rowIndexes" :key="index" class="skeleton-form-row">
          <span class="skeleton-block skeleton-label"></span>
          <span class="skeleton-block skeleton-control"></span>
        </div>
      </section>
    </template>
    <template v-else>
      <section class="skeleton-surface skeleton-table">
        <div class="skeleton-table-head">
          <span v-for="index in 5" :key="index" class="skeleton-block"></span>
        </div>
        <div v-for="index in rowIndexes" :key="index" class="skeleton-table-row">
          <span class="skeleton-block skeleton-avatar"></span>
          <span class="skeleton-block skeleton-cell wide"></span>
          <span class="skeleton-block skeleton-cell"></span>
          <span class="skeleton-block skeleton-cell"></span>
          <span class="skeleton-block skeleton-cell short"></span>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.content-skeleton { width: 100%; }
.skeleton-surface, .skeleton-card {
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%);
}
.skeleton-block {
  display: block;
  border-radius: 6px;
  background: linear-gradient(90deg, #f2f4f7 25%, #e8ecf1 37%, #f2f4f7 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}
.skeleton-table-head, .skeleton-table-row {
  display: grid;
  grid-template-columns: 30px 2fr 1fr 1fr .7fr;
  align-items: center;
  gap: 18px;
  min-height: 52px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f0f0;
}
.skeleton-table-head { min-height: 48px; background: #fafbfc; }
.skeleton-table-head .skeleton-block { height: 12px; }
.skeleton-table-row { min-height: 58px; }
.skeleton-avatar { width: 30px; height: 30px; border-radius: 50%; }
.skeleton-cell { height: 14px; }
.skeleton-cell.wide { width: 82%; }
.skeleton-cell.short { width: 58%; }
.is-cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.skeleton-card { min-height: 160px; padding: 20px; }
.skeleton-title { width: 42%; height: 18px; margin-bottom: 20px; }
.skeleton-line { width: 72%; height: 13px; margin-top: 12px; }
.skeleton-line.wide { width: 92%; }
.skeleton-surface { padding: 24px; }
.skeleton-heading { width: 180px; height: 20px; margin-bottom: 26px; }
.skeleton-form-row { display: grid; grid-template-columns: 100px minmax(0, 1fr); align-items: center; gap: 18px; min-height: 54px; }
.skeleton-label { width: 64px; height: 13px; }
.skeleton-control { height: 36px; }
@keyframes skeleton-shimmer { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
@media (max-width: 768px) {
  .is-cards { grid-template-columns: 1fr; }
  .skeleton-table-head, .skeleton-table-row { grid-template-columns: 30px 2fr 1fr; gap: 12px; padding: 0 14px; }
  .skeleton-table-head .skeleton-block:nth-child(n + 4), .skeleton-table-row .skeleton-cell:nth-last-child(-n + 2) { display: none; }
  .skeleton-surface { padding: 16px; }
  .skeleton-form-row { grid-template-columns: 1fr; gap: 8px; padding-block: 8px; }
}
</style>
