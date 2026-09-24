<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Button, Card, Input, Modal, Table, Tabs, TabPane, Tag, message } from 'ant-design-vue'
import AppPagination from './AppPagination.vue'
import { EditOutlined, PlusOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { useDictionaryOptions, type DictionaryCategory, type DictionaryItem } from '../composables/useDictionaryOptions'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'

const category = ref<DictionaryCategory>('DEPARTMENT')
const items = ref<DictionaryItem[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const modalMode = ref<'create' | 'rename'>('create')
const editingItem = ref<DictionaryItem | null>(null)
const saving = ref(false)
const currentPage = ref(1)
const pageSize = 10
const form = reactive({ name: '' })
const { loadDictionaryOptions } = useDictionaryOptions()

const categoryLabel = computed(() => category.value === 'DEPARTMENT' ? '部门' : '需求类型')
const modalTitle = computed(() => modalMode.value === 'create' ? `新增${categoryLabel.value}` : `改名${categoryLabel.value}`)
const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '状态', key: 'status', width: 120 },
  { title: '操作', key: 'actions', width: 220 },
]
const tableRecord = (record: Record<string, unknown>) => record as unknown as DictionaryItem
const totalPages = computed(() => Math.max(1, Math.ceil(items.value.length / pageSize)))
const pagedItems = computed(() => items.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))

watch(() => items.value.length, () => {
  currentPage.value = Math.min(currentPage.value, totalPages.value)
})

const loadItems = async () => {
  loading.value = true
  try {
    const { data } = await api.get<DictionaryItem[]>('/dictionaries', { params: { category: category.value } })
    items.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

const refreshAfterMutation = async () => {
  await Promise.all([refresh(), loadDictionaryOptions(true)])
}

const changeCategory = async (nextCategory: string | number) => {
  category.value = nextCategory as DictionaryCategory
  currentPage.value = 1
  await refresh()
}

const openCreate = () => {
  modalMode.value = 'create'
  editingItem.value = null
  form.name = ''
  modalOpen.value = true
}

const openRename = (item: DictionaryItem) => {
  modalMode.value = 'rename'
  editingItem.value = item
  form.name = item.name
  modalOpen.value = true
}

const save = async () => {
  const name = form.name.trim()
  if (!name) {
    message.warning('请输入名称')
    return
  }
  saving.value = true
  let createdItemId: number | null = null
  try {
    if (modalMode.value === 'create') {
      const { data } = await api.post('/dictionaries', { category: category.value, name })
      if (Number.isInteger(data?.id)) createdItemId = data.id
      message.success(`${categoryLabel.value}已新增`)
    } else if (editingItem.value) {
      await api.put(`/dictionaries/${editingItem.value.id}`, {
        name,
        recordVersion: editingItem.value.recordVersion,
      })
      message.success(`${categoryLabel.value}名称已更新`)
    }
    modalOpen.value = false
    await refreshAfterMutation()
    if (createdItemId !== null) {
      const createdIndex = items.value.findIndex((item) => item.id === createdItemId)
      if (createdIndex >= 0) currentPage.value = Math.floor(createdIndex / pageSize) + 1
    }
  } catch (error: unknown) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    message.error(response?.data?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

const toggleDisabled = (item: DictionaryItem) => {
  const nextDisabled = !item.disabled
  const action = nextDisabled ? '停用' : '启用'
  Modal.confirm({
    title: `${action}${item.name}`,
    content: nextDisabled
      ? `停用后，${categoryLabel.value}不会出现在新建表单和 AI 回填中；已关联的历史数据仍会保留。`
      : `启用后，${categoryLabel.value}将重新出现在新建表单和 AI 回填中。`,
    okText: `确认${action}`,
    cancelText: '取消',
    onOk: async () => {
      try {
        await api.patch(`/dictionaries/${item.id}/disabled`, {
          disabled: nextDisabled,
          recordVersion: item.recordVersion,
        })
        message.success(`已${action}${item.name}`)
        await refreshAfterMutation()
      } catch (error: unknown) {
        const response = (error as { response?: { data?: { message?: string } } }).response
        message.error(response?.data?.message || `${action}失败，请刷新后重试`)
      }
    },
  })
}

const { loaded, refresh } = usePageRefresh('dictionaries', loadItems)
</script>

<template>
  <section class="dictionary-management" data-test="dictionary-management">
    <ContentSkeleton v-if="!loaded" preset="table" :rows="5" />
    <template v-else>
    <Card :bordered="false">
      <template #title>
        <div class="dictionary-header">
          <span>字典管理</span>
          <Button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            新增{{ categoryLabel }}
          </Button>
        </div>
      </template>

      <Tabs :active-key="category" @change="changeCategory">
        <TabPane key="DEPARTMENT" tab="部门" />
        <TabPane key="REQUIREMENT_TYPE" tab="需求类型" />
      </Tabs>

      <Table :columns="columns" :data-source="pagedItems" row-key="id" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <Tag v-if="record.disabled" color="default"><StopOutlined /> 已停用</Tag>
            <Tag v-else color="success"><CheckCircleOutlined /> 启用中</Tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <Button type="link" size="small" @click="openRename(tableRecord(record))">
              <template #icon><EditOutlined /></template>
              改名
            </Button>
            <Button type="link" size="small" :danger="!record.disabled" @click="toggleDisabled(tableRecord(record))">
              {{ record.disabled ? '启用' : '停用' }}
            </Button>
          </template>
        </template>
      </Table>
      <div v-if="items.length" class="pagination-row">
        <span>共 {{ items.length }} 条</span>
        <AppPagination v-model:current="currentPage" :page-size="pageSize" :total="items.length" :show-size-changer="false" :show-less-items="true" responsive />
      </div>
    </Card>

    <Modal v-model:open="modalOpen" :title="modalTitle" :confirm-loading="saving" @ok="save">
      <div class="dictionary-form-field">
        <label for="dictionary-name">名称</label>
        <Input id="dictionary-name" v-model:value="form.name" :maxlength="50" @press-enter="save" />
      </div>
    </Modal>
    </template>
  </section>
</template>

<style scoped>
.dictionary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dictionary-form-field {
  display: grid;
  gap: 8px;
  padding: 12px 0;
}

.dictionary-form-field label {
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 16px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

@media (max-width: 640px) {
  .pagination-row { align-items: flex-start; flex-direction: column; }
}
</style>
