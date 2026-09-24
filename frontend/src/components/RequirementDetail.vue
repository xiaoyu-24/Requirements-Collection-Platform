<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button, Card, Form, FormItem, Input, Modal, Result, Select, Space, Tag, Timeline, TimelineItem, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { requirementStatusMeta, requirementStatusOptions, saveTypeMeta } from '../constants/statusConfig'
import { urgencyMeta } from '../constants/urgencyConfig'
import { markChanged } from '../composables/refreshBus'
import { useApiError } from '../composables/useApiError'
import { apiErrorDetails } from '../composables/useApiError'
import { useAuth } from '../composables/useAuth'
import { usePageRefresh } from '../composables/usePageRefresh'
import ContentSkeleton from './ContentSkeleton.vue'
import RequirementEditor from './RequirementEditor.vue'

const emit = defineEmits<{
  'requirement-editability-change': [canEdit: boolean]
}>()

type SystemItem = { id: number; name: string; ownerName?: string | null }
type Attachment = {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  previewStatus?: string
  previewAvailable?: boolean
  previewContentType?: string | null
  previewErrorMessage?: string | null
}
type Requirement = {
  id: number
  title: string | null
  type: string | null
  urgency?: string | null
  requesterName: string | null
  requesterUserId?: number | null
  department: string | null
  status: string | null
  submittedAt: string | null
  updatedAt?: string | null
  systemId: number | null
  systemName?: string | null
  systemOwnerName?: string | null
  targetVersionId: number | null
  targetVersionName?: string | null
  periodStartDate: string | null
  periodEndDate: string | null
  completedAt?: string | null
  completionDescription?: string | null
  content?: string | null
  saveType?: string
  recordVersion: number
}
type Progress = {
  id: number
  content: string
  authorId: number
  authorName: string
  status: string | null
  createdAt: string
}
type VersionChange = {
  id: number
  fromVersionName: string | null
  toVersionName: string | null
  action: 'BIND' | 'MIGRATE' | 'UNBIND'
  operatorName: string
  createdAt: string
}

const route = useRoute()
const router = useRouter()
const requirement = ref<Requirement | null>(null)
const systems = ref<SystemItem[]>([])
const attachments = ref<Attachment[]>([])
const progresses = ref<Progress[]>([])
const versionChanges = ref<VersionChange[]>([])
const previewAttachment = ref<Attachment | null>(null)
const loading = ref(true)
const loadError = ref('')
const editing = computed(() => route.query.edit === '1')
const progressModalOpen = ref(false)
const progressLoading = ref(false)
const progressForm = ref({ content: '', status: '' })
const progressError = ref('')
const { handleError } = useApiError()
const { currentUser, isHandler } = useAuth()
let previewRefreshTimer: ReturnType<typeof setTimeout> | undefined

const requirementId = computed(() => Number(route.params.id))
const canEdit = computed(() => isHandler.value || (
  requirement.value?.requesterUserId === currentUser.value?.id
  && (requirement.value?.saveType === 'DRAFT' || ['PENDING_EVALUATION', 'CONFIRMED'].includes(requirement.value?.status ?? ''))
))
const previewOpen = computed({
  get: () => previewAttachment.value !== null,
  set: (open: boolean) => { if (!open) previewAttachment.value = null },
})
const systemName = computed(() => {
  if (!requirement.value || requirement.value.systemId === null) return '暂无系统'
  return requirement.value.systemName
    ?? systems.value.find((system) => system.id === requirement.value?.systemId)?.name
    ?? `系统 #${requirement.value.systemId}`
})
const systemOwnerName = computed(() => {
  if (!requirement.value || requirement.value.systemId === null) return '未设置'
  return requirement.value.systemOwnerName
    ?? systems.value.find((system) => system.id === requirement.value?.systemId)?.ownerName
    ?? '未设置'
})
const formatShanghai = (value: string | null | undefined) => {
  if (!value) return '—'
  const match = value.match(/^(\d{4}-\d{2}-\d{2})/)
  return match?.[1] ?? value.slice(0, 10)
}
const formatShanghaiDateTime = (value: string | null | undefined) => {
  if (!value) return '—'
  return value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 16)
}
const periodText = computed(() => requirement.value?.periodStartDate && requirement.value.periodEndDate
  ? `${requirement.value.periodStartDate} 至 ${requirement.value.periodEndDate}`
  : '—')
const statusLabel = computed(() => requirement.value?.status
  ? requirementStatusMeta(requirement.value.status).label
  : saveTypeMeta(requirement.value?.saveType ?? 'DRAFT').label)
const statusColor = computed(() => requirement.value?.status
  ? requirementStatusMeta(requirement.value.status).color
  : saveTypeMeta(requirement.value?.saveType ?? 'DRAFT').color)
const versionChangeText = (change: VersionChange) => {
  if (change.action === 'BIND') return `绑定至版本“${change.toVersionName || '未知版本'}”`
  if (change.action === 'MIGRATE') return `从版本“${change.fromVersionName || '未知版本'}”迁移至“${change.toVersionName || '未知版本'}”`
  return `解除版本“${change.fromVersionName || '未知版本'}”的绑定`
}
const versionChangeColor = (action: VersionChange['action']) => ({ BIND: 'blue', MIGRATE: 'orange', UNBIND: 'default' }[action])

const clearPreviewRefresh = () => {
  if (previewRefreshTimer !== undefined) clearTimeout(previewRefreshTimer)
  previewRefreshTimer = undefined
}
const schedulePreviewRefresh = () => {
  clearPreviewRefresh()
  if (!requirement.value || !attachments.value.some((item) => item.previewStatus === 'PENDING' || item.previewStatus === 'CONVERTING')) return
  previewRefreshTimer = setTimeout(refreshAttachments, 2000)
}
const refreshAttachments = async () => {
  if (!requirement.value) return
  try {
    const { data } = await api.get(`/requirements/${requirement.value.id}/attachments`)
    attachments.value = Array.isArray(data) ? data : []
  } finally {
    schedulePreviewRefresh()
  }
}

const loadRequirement = async () => {
  if (!Number.isSafeInteger(requirementId.value) || requirementId.value <= 0) {
    loadError.value = '需求编号无效'
    loading.value = false
    return
  }

  loading.value = true
  loadError.value = ''
  emit('requirement-editability-change', false)
  clearPreviewRefresh()
  try {
    const systemRequest = isHandler.value
      ? api.get('/systems')
      : Promise.resolve({ data: [] as SystemItem[] })
    const [detail, attachmentList, systemList, progressList, versionHistory] = await Promise.all([
      api.get(`/requirements/${requirementId.value}`),
      api.get(`/requirements/${requirementId.value}/attachments`),
      systemRequest,
      api.get(`/requirements/${requirementId.value}/progresses`),
      api.get(`/requirements/${requirementId.value}/version-history`),
    ])
    requirement.value = detail.data
    emit('requirement-editability-change', canEdit.value)
    attachments.value = Array.isArray(attachmentList.data) ? attachmentList.data : []
    systems.value = Array.isArray(systemList.data) ? systemList.data : []
    progresses.value = Array.isArray(progressList.data) ? progressList.data : []
    versionChanges.value = Array.isArray(versionHistory.data) ? versionHistory.data : []
    schedulePreviewRefresh()
  } catch (error: unknown) {
    emit('requirement-editability-change', false)
    const status = (error as { response?: { status?: number } }).response?.status
    if (!requirement.value) {
      loadError.value = status === 404 ? '该需求不存在或已被删除' : '加载需求详情失败，请稍后重试'
    }
    throw error
  } finally {
    loading.value = false
  }
}

const canPreview = (attachment: Attachment) => attachment.previewAvailable
  ?? (attachment.contentType === 'application/pdf' || attachment.contentType.startsWith('image/'))
const previewStateLabel = (attachment: Attachment) => attachment.previewStatus === 'FAILED' && attachment.previewErrorMessage
  ? `预览生成失败：${attachment.previewErrorMessage}`
  : ({ PENDING: '等待生成预览', CONVERTING: '正在生成预览', FAILED: '预览生成失败', UNAVAILABLE: '预览不可用' }[attachment.previewStatus ?? ''] ?? '')
const openAttachmentPreview = (attachment: Attachment) => {
  if (canPreview(attachment)) previewAttachment.value = attachment
}
const retryAttachmentPreview = async (attachment: Attachment) => {
  try {
    const { data } = await api.post(`/attachments/${attachment.id}/preview/retry`)
    const index = attachments.value.findIndex((item) => item.id === attachment.id)
    if (index >= 0) attachments.value[index] = data
    schedulePreviewRefresh()
    message.success('已提交预览生成任务')
  } catch {
    message.error('重新生成预览失败')
  }
}
const returnToList = () => {
  void router.push({ name: 'requirement-list' })
}
const setEditQuery = async (enabled: boolean) => {
  const query = { ...route.query }
  if (enabled) query.edit = '1'
  else delete query.edit
  await router.replace({ name: 'requirement-detail', params: { id: requirementId.value }, query })
}
const finishEditing = () => {
  void setEditQuery(false)
}
const cancelEditing = () => {
  void setEditQuery(false)
}
const openProgressUpdate = () => {
  progressForm.value = { content: '', status: '' }
  progressError.value = ''
  progressModalOpen.value = true
}
const saveProgressUpdate = async () => {
  if (!requirement.value) return
  const content = progressForm.value.content.trim()
  progressError.value = ''
  if (!content) {
    progressError.value = '请输入进展内容'
    return
  }

  progressLoading.value = true
  try {
    await api.post(`/requirements/${requirement.value.id}/progresses`, {
      content,
      status: progressForm.value.status || null,
      recordVersion: requirement.value.recordVersion,
    })
    progressModalOpen.value = false
    message.success('进展已更新')
    markChanged(['requirements', 'dashboard', 'overview'])
    await refresh()
  } catch (error: unknown) {
    progressError.value = apiErrorDetails(error).fieldErrors.content || ''
    handleError(error, '更新进度失败，请稍后重试')
  } finally {
    progressLoading.value = false
  }
}

// 直达编辑路由由编辑器独立加载，避免先加载详情再加载编辑数据造成连续刷新。
const { loaded, refresh } = usePageRefresh('requirementDetail', loadRequirement, { isPaused: () => editing.value })

watch(() => route.params.id, () => {
  if (!editing.value && loaded.value) void refresh()
})
watch(editing, (isEditing, wasEditing) => {
  if (!isEditing && wasEditing) void refresh()
})
onBeforeUnmount(() => {
  clearPreviewRefresh()
  emit('requirement-editability-change', false)
})
</script>

<template>
  <section class="requirement-detail-page" data-test="requirement-detail-page">
    <RequirementEditor v-if="editing" :key="requirementId" :requirement-id="requirementId" @cancel="cancelEditing" @return-list="returnToList" @saved="finishEditing" />

    <ContentSkeleton v-else-if="!loaded && !loadError" preset="detail" :rows="6" />
    <template v-else>
      <Result v-if="loadError" status="error" :title="loadError">
        <template #extra><Button type="primary" @click="returnToList">返回需求列表</Button></template>
      </Result>

      <Card v-else-if="requirement" :bordered="false" class="requirement-detail-card" :class="{ 'is-detail-loading': loading }">
          <section class="detail-hero" data-test="detail-core-fields">
            <span class="detail-hero-label">需求标题</span>
            <div class="detail-hero-title-row">
              <strong class="detail-requirement-title">{{ requirement.title || '未命名草稿' }}</strong>
              <Tag color="blue">{{ requirement.type || '—' }}</Tag>
              <Tag :color="statusColor">{{ statusLabel }}</Tag>
              <Tag :color="urgencyMeta(requirement.urgency).color">{{ urgencyMeta(requirement.urgency).label }}</Tag>
            </div>
            <div class="detail-hero-meta">
              <span>所属系统 <strong>{{ systemName }}</strong></span>
              <span>负责人 <strong>{{ systemOwnerName }}</strong></span>
              <span>目标版本 <strong>{{ requirement.targetVersionId === null ? '—' : requirement.targetVersionName ?? `版本 #${requirement.targetVersionId}` }}</strong></span>
              <span>填写人 / 部门 <strong>{{ requirement.requesterName || '—' }} / {{ requirement.department || '—' }}</strong></span>
              <span>填写时间 <strong>{{ formatShanghai(requirement.submittedAt || requirement.updatedAt) }}</strong></span>
            </div>
          </section>

          <section class="detail-info">
            <div class="detail-info-grid">
              <div class="detail-info-item">
                <span class="detail-field-label">需求周期</span>
                <span>{{ periodText }}</span>
              </div>
              <div class="detail-info-item">
                <span class="detail-field-label">最后修改</span>
                <span>{{ formatShanghai(requirement.updatedAt) }}</span>
              </div>
              <div v-if="requirement.completedAt" class="detail-info-item">
                <span class="detail-field-label">完成时间</span>
                <span>{{ formatShanghai(requirement.completedAt) }}</span>
              </div>
            </div>
          </section>

          <section class="detail-section detail-content-section">
            <h2>需求内容</h2>
            <div class="detail-content-field">
              <p class="detail-content">{{ requirement.content || '—' }}</p>
            </div>
          </section>

          <section class="detail-section" data-test="progress-timeline-section">
            <div class="detail-section-heading">
              <h2>进展记录</h2>
              <Button v-if="isHandler" type="primary" data-test="open-progress-update" @click="openProgressUpdate">更新进度</Button>
            </div>
            <Timeline v-if="progresses.length" class="progress-timeline">
              <TimelineItem v-for="progress in progresses" :key="progress.id">
                <div class="progress-meta">
                  <strong>{{ progress.authorName }}</strong>
                  <span>{{ formatShanghaiDateTime(progress.createdAt) }}</span>
                </div>
                <p class="progress-content">{{ progress.content }}</p>
                <Tag v-if="progress.status" :color="requirementStatusMeta(progress.status).color">状态更新为 {{ requirementStatusMeta(progress.status).label }}</Tag>
              </TimelineItem>
            </Timeline>
            <p v-else class="detail-empty">暂无进展记录</p>
          </section>

          <section v-if="requirement.completionDescription" class="detail-section">
            <h2>完成情况</h2>
            <p class="detail-content">{{ requirement.completionDescription }}</p>
          </section>
          <section v-if="versionChanges.length" class="detail-section" data-test="version-history-section">
            <h2>版本变更记录</h2>
            <Timeline class="progress-timeline">
              <TimelineItem v-for="change in versionChanges" :key="change.id" :color="versionChangeColor(change.action)">
                <div class="progress-meta"><strong>{{ change.operatorName }}</strong><span>{{ formatShanghaiDateTime(change.createdAt) }}</span></div>
                <p class="progress-content">{{ versionChangeText(change) }}</p>
              </TimelineItem>
            </Timeline>
          </section>
          <section class="detail-section">
            <h2>附件</h2>
            <ul v-if="attachments.length" class="attachment-list">
              <li v-for="attachment in attachments" :key="attachment.id">
                <div class="attachment-row">
                  <span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span>
                  <span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span>
                  <Space wrap>
                    <Button v-if="canPreview(attachment)" type="link" :data-test="`attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</Button>
                    <Button v-else-if="attachment.previewStatus === 'FAILED'" type="link" :data-test="`attachment-retry-preview-${attachment.id}`" @click="retryAttachmentPreview(attachment)">重新生成</Button>
                    <a :data-test="`attachment-download-${attachment.id}`" :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a>
                  </Space>
                </div>
                <img v-if="attachment.contentType.startsWith('image/')" :data-test="`attachment-preview-${attachment.id}`" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`">
              </li>
            </ul>
            <p v-else class="detail-empty">无附件</p>
          </section>
      </Card>
    </template>

    <Modal v-model:open="previewOpen" :title="previewAttachment?.originalName" :footer="null" destroy-on-close :get-container="false" width="1100px" wrap-class-name="attachment-preview-modal">
      <section v-if="previewAttachment" class="attachment-preview-dialog" data-test="attachment-preview-dialog">
        <p v-if="!previewAttachment.contentType.startsWith('image/')" class="preview-disclaimer">在线预览由系统生成，版式可能与原文件略有差异，请以下载的原文件为准。</p>
        <img v-if="previewAttachment.previewContentType?.startsWith('image/') || previewAttachment.contentType.startsWith('image/')" :src="`/api/attachments/${previewAttachment.id}/preview`" :alt="`${previewAttachment.originalName} 在线预览`">
        <iframe v-else data-test="attachment-preview-frame" :src="`/api/attachments/${previewAttachment.id}/preview`" :title="`${previewAttachment.originalName} 在线预览`"></iframe>
        <div class="form-actions"><a data-test="attachment-preview-download" :href="`/api/attachments/${previewAttachment.id}`" :download="previewAttachment.originalName">下载原文件</a></div>
      </section>
    </Modal>

    <Modal v-model:open="progressModalOpen" title="更新进度" :footer="null" :mask-closable="false" destroy-on-close :get-container="false" @cancel="progressModalOpen = false">
      <Form class="progress-update-form" layout="vertical" :model="progressForm" @submit.prevent="saveProgressUpdate">
        <FormItem label="当前状态"><Tag :color="statusColor">{{ statusLabel }}</Tag></FormItem>
        <FormItem label="更新后状态（可选）">
          <Select v-model:value="progressForm.status" data-test="progress-status" allow-clear placeholder="不修改状态" :options="requirementStatusOptions" />
        </FormItem>
        <FormItem label="进展内容" required :validate-status="progressError ? 'error' : undefined" :help="progressError || undefined">
          <Input.TextArea v-model:value="progressForm.content" data-test="progress-content" :rows="6" placeholder="请输入当前进展、处理结果或补充说明" />
        </FormItem>
        <div class="progress-form-actions">
          <Space>
            <Button html-type="button" @click="progressModalOpen = false">取消</Button>
            <Button type="primary" html-type="submit" data-test="save-progress" :loading="progressLoading">提交进度</Button>
          </Space>
        </div>
      </Form>
    </Modal>

  </section>
</template>

<style scoped>
.requirement-detail-page {
  min-width: 0;
}

.requirement-detail-card :deep(.ant-card-body) {
  padding: 0;
}

.requirement-detail-card.is-detail-loading {
  opacity: .55;
  pointer-events: none;
  transition: opacity .25s ease;
}

.detail-hero {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(180deg, #f7faff 0%, #fff 100%);
}

.detail-hero-label {
  display: block;
  margin-bottom: 6px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.detail-hero-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-requirement-title {
  color: rgba(0, 0, 0, 0.88);
  font-size: 18px;
  font-weight: 700;
  word-break: break-word;
}

.detail-hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 14px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.detail-hero-meta strong {
  color: rgba(0, 0, 0, 0.65);
  font-weight: 500;
}

.detail-info {
  padding: 0 24px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid #f0f0f0;
  border-left: 1px solid #f0f0f0;
}

.detail-info-item {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  align-items: center;
  min-height: 48px;
  padding: 9px 14px;
  border-right: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
  color: rgba(0, 0, 0, 0.88);
  word-break: break-word;
}

.detail-section {
  padding: 24px;
  border-top: 1px solid #f0f0f0;
}

.detail-content-section {
  border-top: 0;
}

.detail-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.detail-field-label {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.detail-content-field {
  min-height: 150px;
  padding: 18px 20px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.detail-section h2 {
  margin: 0 0 10px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 15px;
  font-weight: 600;
}

.progress-timeline {
  margin-top: 16px;
}

.progress-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  color: rgba(0, 0, 0, 0.88);
}

.progress-meta span {
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.progress-content {
  margin: 6px 0 8px;
  color: rgba(0, 0, 0, 0.72);
  white-space: pre-wrap;
}

.progress-form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.detail-content,
.detail-empty {
  margin: 0;
  color: rgba(0, 0, 0, 0.72);
  white-space: pre-wrap;
}

@media (max-width: 640px) {
  .detail-info,
  .detail-hero,
  .detail-section {
    padding-right: 16px;
    padding-left: 16px;
  }

  .detail-info-grid {
    grid-template-columns: 1fr;
  }

  .detail-section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
