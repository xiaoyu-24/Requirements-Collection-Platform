<script setup lang="ts">
/**
 * 全站统一分页组件。
 *
 * 与 ant-design-vue Pagination 的差异：
 * 1. 完全移除 «/» 快速翻页按钮（含省略号占位），页码截断处不渲染任何元素；
 * 2. 固定开启"跳至 X 页"输入框，可直接跳转到指定页；
 * 3. 统一圆角与间距，贴合页面设计语言。
 *
 * 透传所有 props / 事件（含 v-model:current、change），使用方式与原 Pagination 一致。
 */
import { Pagination } from 'ant-design-vue'

defineOptions({ inheritAttrs: false })
</script>

<template>
  <Pagination
    v-bind="$attrs"
    class="app-pagination"
    :show-size-changer="false"
    show-quick-jumper
  >
    <template #itemRender="{ type, originalElement }">
      <template v-if="type !== 'jump-prev' && type !== 'jump-next'">
        <component :is="originalElement" />
      </template>
    </template>
  </Pagination>
</template>

<style>
.app-pagination {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.app-pagination .ant-pagination-item,
.app-pagination .ant-pagination-prev,
.app-pagination .ant-pagination-next {
  border-radius: 8px;
  margin-inline-end: 0;
  transition: border-color .2s, color .2s, background .2s;
}
.app-pagination .ant-pagination-item:hover,
.app-pagination .ant-pagination-prev:hover,
.app-pagination .ant-pagination-next:hover {
  border-color: #1677ff;
}
.app-pagination .ant-pagination-item-active {
  border-radius: 8px;
  font-weight: 600;
}
.app-pagination .ant-pagination-options {
  margin-inline-start: 8px;
}
.app-pagination .ant-pagination-options-quick-jumper {
  color: rgba(0, 0, 0, .65);
  font-size: 13px;
  line-height: 32px;
}
.app-pagination .ant-pagination-options-quick-jumper input {
  border-radius: 8px;
  height: 32px;
  margin: 0 6px;
  transition: border-color .2s, box-shadow .2s;
}
.app-pagination .ant-pagination-options-quick-jumper input:hover {
  border-color: #4096ff;
}
.app-pagination .ant-pagination-options-quick-jumper input:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, .12);
  outline: none;
}
</style>
