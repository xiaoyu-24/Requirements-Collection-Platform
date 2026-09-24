<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Button, Input, Modal, Switch, Table, Tag, message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'

interface ConfigItem {
  id: number
  name: string
  enabled: boolean
  isActive: boolean
  serviceUrl: string | null
  modelName: string | null
  apiKeyMask: string | null
}

const configs = ref<ConfigItem[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const modalTitle = ref('新增 AI 配置')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  name: '',
  serviceUrl: '',
  modelName: '',
  apiKey: '',
  enabled: true,
})
const apiKeyMask = ref('')

const columns = [
  { title: '配置名称', dataIndex: 'name', key: 'name' },
  { title: '模型', dataIndex: 'modelName', key: 'modelName' },
  { title: 'API 地址', dataIndex: 'serviceUrl', key: 'serviceUrl', ellipsis: true },
  { title: '操作', key: 'action', width: 280 },
]

const loadConfigs = async () => {
  loading.value = true
  try {
    const { data } = await api.get('/ai-config')
    configs.value = data
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  modalTitle.value = '新增 AI 配置'
  form.name = ''
  form.serviceUrl = ''
  form.modelName = ''
  form.apiKey = ''
  form.enabled = true
  apiKeyMask.value = ''
  modalOpen.value = true
}

const openEdit = (record: ConfigItem) => {
  editingId.value = record.id
  modalTitle.value = '编辑 AI 配置'
  form.name = record.name
  form.serviceUrl = record.serviceUrl ?? ''
  form.modelName = record.modelName ?? ''
  form.apiKey = ''
  form.enabled = record.enabled
  apiKeyMask.value = record.apiKeyMask ?? ''
  modalOpen.value = true
}

const saveConfig = async () => {
  if (!form.name.trim()) {
    message.warning('请填写配置名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await api.post('/ai-config', {
        name: form.name,
        enabled: form.enabled,
        serviceUrl: form.serviceUrl || null,
        modelName: form.modelName || null,
        apiKey: form.apiKey || null,
      })
      message.success('配置已创建')
    } else {
      await api.put(`/ai-config/${editingId.value}`, {
        name: form.name,
        enabled: form.enabled,
        serviceUrl: form.serviceUrl || null,
        modelName: form.modelName || null,
        apiKey: form.apiKey || null,
      })
      message.success('配置已保存')
    }
    modalOpen.value = false
    await refresh()
  } catch (error: unknown) {
    const msg = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    message.error(msg || '保存失败')
  } finally {
    saving.value = false
  }
}

const activateConfig = async (record: ConfigItem) => {
  try {
    await api.post(`/ai-config/${record.id}/activate`)
    message.success(`已切换使用「${record.name}」`)
    await refresh()
  } catch (error: unknown) {
    const msg = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    message.error(msg || '切换失败')
  }
}

const toggleEnabled = async (record: ConfigItem) => {
  try {
    await api.put(`/ai-config/${record.id}`, {
      name: record.name,
      enabled: !record.enabled,
      serviceUrl: record.serviceUrl,
      modelName: record.modelName,
      apiKey: null,
    })
    message.success(record.enabled ? '已停用配置' : '已启用配置')
    await refresh()
  } catch (error: unknown) {
    const msg = (error as { response?: { data?: { message?: string } } }).response?.data?.message
    message.error(msg || '操作失败')
  }
}

const { loaded, refresh } = usePageRefresh('aiConfig', loadConfigs)
</script>

<template>
  <section class="ai-config-page">
    <ContentSkeleton v-if="!loaded" preset="table" :rows="4" />
    <template v-else>
    <div class="page-toolbar">
      <Button type="primary" @click="openCreate"><PlusOutlined /> 新增 AI 配置</Button>
    </div>

    <Table
      :columns="columns"
      :data-source="configs"
      :pagination="false"
      row-key="id"
      size="middle"
      class="config-table"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <span class="config-name">{{ (record as ConfigItem).name }}</span>
          <Tag v-if="(record as ConfigItem).isActive" color="success" class="active-badge">当前使用</Tag>
          <Tag v-if="!(record as ConfigItem).enabled" color="default" class="active-badge">已停用</Tag>
        </template>
        <template v-else-if="column.key === 'modelName'">
          {{ (record as ConfigItem).modelName || '—' }}
        </template>
        <template v-else-if="column.key === 'serviceUrl'">
          {{ (record as ConfigItem).serviceUrl || '—' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="action-btns">
            <Button size="small" @click="openEdit(record as ConfigItem)">编辑配置</Button>
            <Button
              size="small"
              :type="(record as ConfigItem).isActive ? 'default' : 'primary'"
              :disabled="(record as ConfigItem).isActive"
              ghost
              @click="activateConfig(record as ConfigItem)"
            >切换使用</Button>
            <Button
              size="small"
              :danger="(record as ConfigItem).enabled"
              @click="toggleEnabled(record as ConfigItem)"
            >{{ (record as ConfigItem).enabled ? '停用配置' : '启用配置' }}</Button>
          </div>
        </template>
      </template>
    </Table>

    <Modal
      v-model:open="modalOpen"
      :title="modalTitle"
      :footer="null"
      :width="520"
      class="config-modal"
      destroy-on-close
    >
      <div class="modal-form">
        <div class="form-field">
          <label class="field-label"><span class="required">*</span> 配置名称</label>
          <Input v-model:value="form.name" placeholder="例如：生产视觉模型" />
        </div>
        <div class="form-field">
          <label class="field-label"><span class="required">*</span> AI Base URL</label>
          <Input v-model:value="form.serviceUrl" placeholder="https://api.example.com/v1" />
        </div>
        <div class="form-field">
          <label class="field-label"><span class="required">*</span> 模型名称</label>
          <Input v-model:value="form.modelName" placeholder="例如：gpt-4o-mini" />
        </div>
        <div class="form-field">
          <label class="field-label"><span class="required">*</span> API Key</label>
          <Input.Password
            v-model:value="form.apiKey"
            :placeholder="apiKeyMask ? '已配置（输入新值可替换）' : '请输入 API Key'"
          />
        </div>
        <div class="form-field switch-field">
          <label class="field-label">启用状态</label>
          <Switch v-model:checked="form.enabled" />
        </div>
        <div class="modal-actions">
          <Button @click="modalOpen = false">取消</Button>
          <Button type="primary" :loading="saving" @click="saveConfig">保存配置</Button>
        </div>
      </div>
    </Modal>
    </template>
  </section>
</template>

<style scoped>
.ai-config-page {
  width: 100%;
}

.page-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.config-table {
  background: #fff;
  border-radius: 8px;
}

.config-name {
  font-weight: 500;
}

.active-badge {
  margin-left: 8px;
}

.action-btns {
  display: flex;
  gap: 8px;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 8px 0;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.88);
}

.required {
  color: #ff4d4f;
  margin-right: 2px;
}

.switch-field :deep(.ant-switch) {
  align-self: flex-start;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
</style>

<style>
.config-modal .ant-modal-content {
  border-radius: 12px;
}
</style>
