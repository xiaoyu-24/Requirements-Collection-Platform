<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Alert, Button, DatePicker, FormItem, Input, Modal, Result, Select, Space, message } from 'ant-design-vue'
import { InboxOutlined, InfoCircleOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { api } from '../api'
import { markChanged } from '../composables/refreshBus'
import { useApiError, apiErrorDetails } from '../composables/useApiError'
import { useFormErrors } from '../composables/useFormErrors'
import { useDictionaryOptions, type DictionaryItem } from '../composables/useDictionaryOptions'
import { useAuth } from '../composables/useAuth'
import { urgencyOptions } from '../constants/urgencyConfig'
import { usePageRefresh } from '../composables/usePageRefresh'
import { useClipboardAttachments } from '../composables/useClipboardAttachments'
import ContentSkeleton from './ContentSkeleton.vue'

type SystemItem = { id: number; name: string; status: string }
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
type EditableRequirement = {
  id: number
  title: string | null
  typeId: number | null
  type: string | null
  requesterName: string | null
  requesterUserId?: number | null
  departmentId: number | null
  department: string | null
  status: string | null
  systemId: number | null
  systemName?: string | null
  targetVersionId: number | null
  targetVersionName?: string | null
  periodStartDate: string | null
  periodEndDate: string | null
  content: string | null
  urgency?: string | null
  saveType?: string
  recordVersion?: number
}

const props = defineProps<{ requirementId: number }>()
const emit = defineEmits<{
  cancel: []
  returnList: []
  saved: []
}>()

const systems = ref<SystemItem[]>([])
const attachments = ref<Attachment[]>([])
const selectedFiles = ref<File[]>([])
const attachmentInput = ref<HTMLInputElement | null>(null)
const previewAttachment = ref<Attachment | null>(null)
const requirement = ref<EditableRequirement | null>(null)
const loading = ref(true)
const loadError = ref('')
const uploading = ref(false)
const isDragging = ref(false)
const saveType = ref('SUBMITTED')
const form = reactive({
  requesterName: '', departmentId: '', title: '', typeId: '', content: '', systemId: '',
  periodStartDate: '', periodEndDate: '', urgency: 'MEDIUM', status: '', recordVersion: 0,
})
const allowedAttachmentExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx'])
// tasks 4.7 顺带修复：编辑页原本限制 20 MB，与填写页 100 MB 及 spec "附件客户端校验规则 / 单文件大小限制" 不一致，本次统一为 100 MB
const maxAttachmentSizeBytes = 100 * 1024 * 1024
// 粘贴上传相关状态（tasks 4.2）
const formRootRef = ref<HTMLFormElement | null>(null)
const attachmentDropzoneRef = ref<HTMLElement | null>(null)
const pasteTargetActive = ref(false)
const remoteImageUrls = ref<string[]>([])
const remoteHintVisible = ref(false)
const { parseClipboard } = useClipboardAttachments({
  allowedExtensions: allowedAttachmentExtensions,
  maxFileSizeBytes: maxAttachmentSizeBytes,
})
const { departments, requirementTypes, loadDictionaryOptions } = useDictionaryOptions()
const { currentUser, isHandler } = useAuth()
const { handleError } = useApiError()
const { errors, clearErrors, setError, applyServerErrors } = useFormErrors()
let previewRefreshTimer: ReturnType<typeof setTimeout> | undefined

const isDraft = computed(() => saveType.value === 'DRAFT')
const canEdit = computed(() => isHandler.value || (
  requirement.value?.requesterUserId === currentUser.value?.id
  && (isDraft.value || ['PENDING_EVALUATION', 'CONFIRMED'].includes(requirement.value?.status ?? ''))
))
const requirementCode = computed(() => `#REQ-${String(props.requirementId).padStart(6, '0')}`)
const previewOpen = computed({
  get: () => previewAttachment.value !== null,
  set: (open: boolean) => { if (!open) previewAttachment.value = null },
})
const optionsWithCurrent = (items: DictionaryItem[], currentId: string, currentName: string | null | undefined) => {
  const options = items.map((item) => ({ label: item.name, value: String(item.id) }))
  if (currentId && !options.some((option) => option.value === currentId)) {
    options.push({ label: `${currentName || `#${currentId}`}（已停用）`, value: currentId })
  }
  return options
}
const departmentOptions = computed(() => optionsWithCurrent(departments.value, form.departmentId, requirement.value?.department))
const typeOptions = computed(() => optionsWithCurrent(requirementTypes.value, form.typeId, requirement.value?.type))
const statusOptions = [
  { label: '待评估', value: 'PENDING_EVALUATION' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '开发中', value: 'IN_DEVELOPMENT' },
  { label: '暂停', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已关闭', value: 'CLOSED' },
]
const systemOptions = computed(() => systems.value.map((system) => ({
    label: `${system.name}${system.status === 'ACTIVE' ? '' : '（已停用）'}`,
    value: String(system.id),
    disabled: system.status !== 'ACTIVE' && String(system.id) !== form.systemId,
  })))
const clearPreviewRefresh = () => {
  if (previewRefreshTimer !== undefined) clearTimeout(previewRefreshTimer)
  previewRefreshTimer = undefined
}
const schedulePreviewRefresh = () => {
  clearPreviewRefresh()
  if (!attachments.value.some((item) => item.previewStatus === 'PENDING' || item.previewStatus === 'CONVERTING')) return
  previewRefreshTimer = setTimeout(refreshAttachments, 2000)
}
const refreshAttachments = async () => {
  try {
    const { data } = await api.get(`/requirements/${props.requirementId}/attachments`)
    attachments.value = Array.isArray(data) ? data : []
  } finally {
    schedulePreviewRefresh()
  }
}
const loadEditor = async () => {
  if (!Number.isSafeInteger(props.requirementId) || props.requirementId <= 0) {
    loadError.value = '需求编号无效'
    loading.value = false
    return
  }

  loading.value = true
  loadError.value = ''
  clearPreviewRefresh()
  try {
    const systemsRequest = api.get('/systems')
    const [detailResponse, attachmentResponse, systemsResponse] = await Promise.all([
      api.get(`/requirements/${props.requirementId}`),
      api.get(`/requirements/${props.requirementId}/attachments`),
      systemsRequest,
      loadDictionaryOptions(),
    ])
    const detail = detailResponse.data as EditableRequirement
    requirement.value = detail
    systems.value = Array.isArray(systemsResponse.data) ? systemsResponse.data : []
    attachments.value = Array.isArray(attachmentResponse.data) ? attachmentResponse.data : []
    saveType.value = detail.saveType ?? 'SUBMITTED'
    if (!canEdit.value) {
      loadError.value = '当前状态不允许编辑该需求'
      return
    }
    Object.assign(form, {
      requesterName: detail.requesterName ?? '',
      departmentId: detail.departmentId == null ? '' : String(detail.departmentId),
      title: detail.title ?? '',
      typeId: detail.typeId == null ? '' : String(detail.typeId),
      content: detail.content ?? '',
      urgency: detail.urgency ?? 'MEDIUM',
      systemId: detail.systemId === null ? '' : String(detail.systemId),
      periodStartDate: detail.periodStartDate ?? '',
      periodEndDate: detail.periodEndDate ?? '',
      status: detail.status ?? '',
      recordVersion: detail.recordVersion ?? 0,
    })
    selectedFiles.value = []
    schedulePreviewRefresh()
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    loadError.value = status === 404 ? '该需求不存在或已被删除' : '加载编辑数据失败，请稍后重试'
    throw error
  } finally {
    loading.value = false
  }
}

const addFiles = (files: File[]) => {
  const accepted = files.filter((file) => {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    return allowedAttachmentExtensions.has(extension) && file.size <= maxAttachmentSizeBytes
  })
  selectedFiles.value = [
    ...selectedFiles.value,
    ...accepted.filter((file) => !selectedFiles.value.some((item) => item.name === file.name && item.size === file.size && item.lastModified === file.lastModified)),
  ]
}
const selectFiles = (event: Event) => {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files ?? []))
  input.value = ''
}
const dropFiles = (event: DragEvent) => {
  isDragging.value = false
  addFiles(Array.from(event.dataTransfer?.files ?? []))
}
const openAttachmentPicker = () => attachmentInput.value?.click()

// 粘贴上传（tasks 4.3）：编辑表单根节点监听 paste 事件，按 design.md D2 焦点分流
// 附件预览 Modal 为 <form> 的兄弟节点（非子节点），Modal.confirm 默认 teleport 到 body，
// 两者均不会冒泡到 form，天然满足 spec "粘贴事件在模态框、Popover 等浮层内触发" 场景
const onPaste = async (event: ClipboardEvent) => {
  if (!formRootRef.value || !formRootRef.value.contains(event.target as Node)) return
  const result = await parseClipboard(event)
  if (result.files.length === 0 && result.remoteImageUrls.length === 0 && result.rejectedReasons.length === 0) return
  event.preventDefault()
  if (result.files.length > 0) {
    addFiles(result.files)
    message.success(`已添加 ${result.files.length} 个附件`)
    schedulePreviewRefresh()
  }
  if (result.rejectedReasons.length > 0) {
    message.warning(result.rejectedReasons.join('；'))
  }
  if (result.remoteImageUrls.length > 0) {
    remoteImageUrls.value = result.remoteImageUrls
    remoteHintVisible.value = true
  }
  pasteTargetActive.value = true
  setTimeout(() => { pasteTargetActive.value = false }, 1200)
  attachmentDropzoneRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
const uploadAttachments = async () => {
  if (selectedFiles.value.length === 0) return
  uploading.value = true
  try {
    for (const file of selectedFiles.value) {
      const body = new FormData()
      body.append('file', file)
      const { data } = await api.post(`/requirements/${props.requirementId}/attachments`, body)
      attachments.value.push(data)
    }
    selectedFiles.value = []
    schedulePreviewRefresh()
    message.success('附件上传成功')
    markChanged(['requirements', 'dashboard', 'overview'])
  } catch (error) {
    const { message: serverMessage } = apiErrorDetails(error)
    message.error(serverMessage || '附件上传失败，未完成的文件可重新选择后上传')
  } finally {
    uploading.value = false
  }
}
const deleteAttachment = (attachment: Attachment) => {
  Modal.confirm({
    title: '删除附件',
    content: `确定删除附件“${attachment.originalName}”吗？`,
    okText: '删除',
    cancelText: '取消',
    onOk: async () => {
  try {
    await api.delete(`/attachments/${attachment.id}`)
    attachments.value = attachments.value.filter((item) => item.id !== attachment.id)
    message.success('附件已删除')
    markChanged(['requirements', 'dashboard', 'overview'])
  } catch (error: unknown) {
    handleError(error, '删除附件失败，请稍后重试')
  }
    },
  })
}
const changeSystem = (value: unknown) => {
  const nextSystemId = value == null ? '' : String(value)
  const originalSystemId = requirement.value?.systemId == null ? '' : String(requirement.value.systemId)
  if (!requirement.value?.targetVersionId || nextSystemId === originalSystemId) return
  Modal.confirm({
    title: '更换所属系统',
    content: `该需求当前已绑定版本“${requirement.value.targetVersionName ?? `#${requirement.value.targetVersionId}`}”。保存后将自动解除版本绑定，是否继续？`,
    okText: '继续更换',
    cancelText: '取消',
    onCancel: () => { form.systemId = originalSystemId },
  })
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
const save = async (submitDraft = false) => {
  clearErrors()
  if (!isDraft.value || submitDraft) {
    const requiredFields: Array<[string, string, string]> = [
      ['requesterName', form.requesterName, '请输入姓名'],
      ['departmentId', form.departmentId, '请选择部门'],
      ['title', form.title, '请输入需求标题'],
      ['typeId', form.typeId, '请选择需求类型'],
      ['urgency', form.urgency, '请选择紧急程度'],
      ['content', form.content, '请输入需求内容'],
    ]
    requiredFields.forEach(([field, value, error]) => { if (!String(value).trim()) setError(field, error) })
    if ((form.periodStartDate && !form.periodEndDate) || (!form.periodStartDate && form.periodEndDate)) {
      setError('period', '开始日期和结束日期必须同时填写')
    } else if (form.periodStartDate && form.periodEndDate && form.periodEndDate < form.periodStartDate) {
      setError('period', '结束日期不能早于开始日期')
    }
    if (Object.keys(errors).length > 0) {
      message.warning('请先补充标记的必填项')
      return
    }
  }
  const body = {
    requesterName: form.requesterName,
    departmentId: form.departmentId ? Number(form.departmentId) : null,
    title: form.title,
    typeId: form.typeId ? Number(form.typeId) : null,
    urgency: form.urgency,
    content: form.content,
    systemId: form.systemId ? Number(form.systemId) : null,
    periodStartDate: form.periodStartDate || null,
    periodEndDate: form.periodEndDate || null,
    recordVersion: form.recordVersion,
  }
  try {
    const isDraftSave = isDraft.value && !submitDraft
    const path = isDraftSave ? `/requirements/${props.requirementId}/draft` : `/requirements/${props.requirementId}`
    await api.put(path, body)
    message.success(isDraftSave ? '草稿已更新' : submitDraft ? '草稿已正式提交' : '需求已更新')
    markChanged(['requirements', 'dashboard', 'overview'])
    emit('saved')
  } catch (error: unknown) {
    const details = applyServerErrors(error)
    if (Object.keys(details.fieldErrors).length > 0) {
      message.error(details.message || '请检查标记的字段后重试')
    } else {
      handleError(error, '保存需求失败，请检查必填项和所属系统')
    }
  }
}

const { loaded, refresh, refreshing, lastUpdatedAt } = usePageRefresh('requirementEditor', loadEditor)
const lastUpdatedLabel = computed(() => lastUpdatedAt.value
  ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(lastUpdatedAt.value)
  : '')
watch(() => props.requirementId, () => {
  if (loaded.value) void refresh()
})
onBeforeUnmount(clearPreviewRefresh)
</script>

<template>
  <section class="requirement-editor" data-test="requirement-editor">
    <Result v-if="loadError" data-test="edit-error" status="error" :title="loadError">
      <template #extra>
        <Space>
          <Button type="primary" @click="refresh">重新加载</Button>
          <Button @click="emit('cancel')">取消</Button>
        </Space>
      </template>
    </Result>

    <ContentSkeleton v-else-if="!loaded || loading" preset="form" :rows="8" />
    <form v-else ref="formRootRef" class="requirement-editor-form" data-test="edit-form" @submit.prevent="save()" @paste="onPaste">
        <nav class="editor-breadcrumb" aria-label="编辑需求导航">
          <button type="button" @click="emit('returnList')">需求列表</button>
          <span>/</span>
          <button type="button" @click="emit('cancel')">需求详情</button>
          <span>/</span>
          <span>编辑需求</span>
        </nav>
        <header class="editor-page-header">
          <div>
            <h1>编辑需求 <span>{{ requirementCode }}</span></h1>
            <p>修改需求信息，保存后即时生效</p>
          </div>
          <div class="editor-page-actions">
            <span v-if="lastUpdatedLabel" class="editor-updated-at">更新于 {{ lastUpdatedLabel }}</span>
            <Button class="editor-refresh-button" html-type="button" :loading="refreshing" title="刷新数据" aria-label="刷新数据" @click="refresh"><template #icon><ReloadOutlined /></template></Button>
            <Button html-type="button" @click="emit('returnList')">返回列表</Button>
            <Button html-type="button" @click="emit('cancel')">取消编辑</Button>
          </div>
        </header>
        <div class="editor-surface">
          <div class="editor-notice" data-test="edit-field-legend">
            <InfoCircleOutlined class="editor-notice-icon" />
            <span>带 <span class="field-required">*</span> 的项目为必填项；草稿可暂存未完成内容，正式提交时必须填写完整。</span>
          </div>
          <div class="editor-grid">
            <FormItem label="姓名" :required="!isDraft" :validate-status="errors.requesterName ? 'error' : undefined" :help="errors.requesterName"><Input v-model:value="form.requesterName" placeholder="请输入姓名" /></FormItem>
            <FormItem label="部门" :required="!isDraft" :validate-status="errors.departmentId ? 'error' : undefined" :help="errors.departmentId"><Select v-model:value="form.departmentId" data-test="edit-department-select" :options="departmentOptions" placeholder="请选择部门" /></FormItem>
            <FormItem class="full-width" data-test="edit-title-field" label="需求标题" :required="!isDraft" :validate-status="errors.title ? 'error' : undefined" :help="errors.title"><Input v-model:value="form.title" data-test="edit-title" placeholder="一句话概括需求，建议 30 字以内" /></FormItem>
            <FormItem label="类型" :required="!isDraft" :validate-status="errors.typeId ? 'error' : undefined" :help="errors.typeId"><Select v-model:value="form.typeId" :options="typeOptions" placeholder="请选择需求类型" /></FormItem>
            <FormItem label="紧急程度" :required="!isDraft" :validate-status="errors.urgency ? 'error' : undefined" :help="errors.urgency"><Select v-model:value="form.urgency" :options="urgencyOptions" placeholder="请选择紧急程度" /></FormItem>
            <FormItem class="disabled-field" label="当前状态"><Select v-model:value="form.status" data-test="edit-status" :options="statusOptions" disabled placeholder="提交后默认为待评估" /><template #extra>状态由流程流转自动更新，编辑时不可修改</template></FormItem>
            <FormItem label="所属系统"><Select v-model:value="form.systemId" data-test="edit-system-select" :options="systemOptions" placeholder="请选择所属系统" @change="changeSystem" /></FormItem>
            <FormItem label="当前版本">
              <div class="readonly-version" data-test="current-version-display">{{ requirement?.targetVersionName || '未绑定版本' }}</div>
              <template v-if="requirement?.targetVersionId" #extra>版本由需求处理员或管理员统一维护；更换所属系统会自动解除当前版本。</template>
            </FormItem>
            <FormItem data-test="edit-period-field" label="需求时间周期" :validate-status="errors.period ? 'error' : undefined" :help="errors.period"><div class="date-range"><DatePicker v-model:value="form.periodStartDate" value-format="YYYY-MM-DD" placeholder="开始日期" /><span>至</span><DatePicker v-model:value="form.periodEndDate" value-format="YYYY-MM-DD" placeholder="结束日期" /></div></FormItem>
            <FormItem class="full-width" label="需求内容" :required="!isDraft" :validate-status="errors.content ? 'error' : undefined" :help="errors.content"><Input.TextArea v-model:value="form.content" :rows="9" placeholder="请详细描述需求背景、目标与验收标准…" /></FormItem>
            <FormItem class="full-width attachment-form-item" label="附件">
              <div class="edit-attachments">
                <Alert
                  v-if="remoteHintVisible && remoteImageUrls.length"
                  class="attachment-remote-hint"
                  type="info"
                  show-icon
                  closable
                  data-test="edit-attachment-remote-hint"
                  @close="remoteHintVisible = false"
                >
                  <template #message>检测到 {{ remoteImageUrls.length }} 张远程图片，出于安全与版权考虑不会自动下载。如需作为附件，请右键图片“另存为”后再上传。</template>
                  <template #description>
                    <ul class="attachment-remote-hint-list">
                      <li v-for="(url, index) in remoteImageUrls" :key="index"><span>{{ url }}</span></li>
                    </ul>
                  </template>
                </Alert>
                <div class="attachment-upload">
                  <div ref="attachmentDropzoneRef" class="attachment-dropzone" :class="{ 'is-dragging': isDragging, 'is-paste-target': pasteTargetActive }" data-test="edit-attachment-dropzone" role="button" tabindex="0" @click="openAttachmentPicker" @keydown.enter.prevent="openAttachmentPicker" @dragenter.prevent="isDragging = true" @dragover.prevent="isDragging = true" @dragleave.prevent="isDragging = false" @drop.prevent="dropFiles">
                    <p class="ant-upload-drag-icon"><InboxOutlined /></p>
                    <p class="ant-upload-text">拖拽附件到此处，或 <b>点击选择文件</b></p>
                    <p class="attachment-hint">支持 PDF / Word / 图片等常见格式，单个文件不超过 100MB。可点击选择、拖入，或直接 Ctrl+V 粘贴截图与文件。</p>
                    <input ref="attachmentInput" data-test="edit-attachment-input" type="file" multiple accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="selectFiles">
                  </div>
                  <Button v-if="selectedFiles.length" type="primary" html-type="button" data-test="edit-attachment-upload" :loading="uploading" @click="uploadAttachments">上传附件</Button>
                </div>
                <ul v-if="selectedFiles.length" class="attachment-list pending-attachments"><li v-for="file in selectedFiles" :key="`${file.name}-${file.lastModified}`">待上传：{{ file.name }}</li></ul>
                <ul v-if="attachments.length" class="attachment-list">
                  <li v-for="attachment in attachments" :key="attachment.id">
                    <div class="attachment-row">
                      <span><strong>{{ attachment.originalName }}</strong>（{{ attachment.sizeBytes }} 字节）</span>
                      <span v-if="previewStateLabel(attachment)" class="attachment-preview-state">{{ previewStateLabel(attachment) }}</span>
                      <Space wrap>
                        <Button v-if="canPreview(attachment)" type="link" html-type="button" :data-test="`edit-attachment-open-preview-${attachment.id}`" @click="openAttachmentPreview(attachment)">预览</Button>
                        <Button v-else-if="attachment.previewStatus === 'FAILED'" type="link" html-type="button" @click="retryAttachmentPreview(attachment)">重新生成</Button>
                        <a :href="`/api/attachments/${attachment.id}`" :download="attachment.originalName">下载原文件</a>
                        <Button danger type="link" html-type="button" :data-test="`edit-attachment-delete-${attachment.id}`" @click="deleteAttachment(attachment)">删除</Button>
                      </Space>
                    </div>
                    <img v-if="attachment.contentType.startsWith('image/')" :src="`/api/attachments/${attachment.id}`" :alt="`${attachment.originalName} 预览`">
                  </li>
                </ul>
                <p v-if="!attachments.length && !selectedFiles.length" class="edit-no-attachment">暂无附件</p>
              </div>
            </FormItem>
          </div>
        </div>
        <div class="editor-footer">
          <span class="editor-save-state">{{ isDraft ? '当前为草稿，可暂存未完成内容' : '修改内容保存后即时生效' }}</span>
          <span class="editor-footer-spacer"></span>
          <Button v-if="isDraft" html-type="button" :data-test="`save-draft-${requirementId}`" @click="save()">存为草稿</Button>
          <Button type="primary" :html-type="isDraft ? 'button' : 'submit'" :data-test="isDraft ? `submit-draft-${requirementId}` : undefined" @click="isDraft && save(true)">保存并提交</Button>
        </div>
    </form>

    <Modal v-model:open="previewOpen" :title="previewAttachment?.originalName" :footer="null" destroy-on-close :get-container="false" width="1100px" wrap-class-name="attachment-preview-modal">
      <section v-if="previewAttachment" class="attachment-preview-dialog" data-test="attachment-preview-dialog">
        <p v-if="!previewAttachment.contentType.startsWith('image/')" class="preview-disclaimer">在线预览由系统生成，版式可能与原文件略有差异，请以下载的原文件为准。</p>
        <img v-if="previewAttachment.previewContentType?.startsWith('image/') || previewAttachment.contentType.startsWith('image/')" :src="`/api/attachments/${previewAttachment.id}/preview`" :alt="`${previewAttachment.originalName} 在线预览`">
        <iframe v-else data-test="attachment-preview-frame" :src="`/api/attachments/${previewAttachment.id}/preview`" :title="`${previewAttachment.originalName} 在线预览`"></iframe>
        <div class="editor-actions"><a data-test="attachment-preview-download" :href="`/api/attachments/${previewAttachment.id}`" :download="previewAttachment.originalName">下载原文件</a></div>
      </section>
    </Modal>
  </section>
</template>

<style scoped>
.requirement-editor {
  min-width: 0;
}

.requirement-editor-form {
  padding-bottom: 88px;
}

.editor-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
  line-height: 20px;
}

.editor-breadcrumb button {
  padding: 0;
  border: 0;
  background: transparent;
  color: rgba(0, 0, 0, 0.65);
  cursor: pointer;
}

.editor-breadcrumb button:hover {
  color: #1677ff;
}

.editor-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

.editor-page-header h1 {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0;
  color: rgba(0, 0, 0, 0.88);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
}

.editor-page-header h1 span {
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
}

.editor-page-header p {
  margin: 6px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.editor-page-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
}

.editor-updated-at { color: rgba(0, 0, 0, 0.45); font-size: 12px; white-space: nowrap; }
.editor-refresh-button { display: grid; width: 34px; height: 34px; place-items: center; padding: 0; border-radius: 8px; }
.editor-refresh-button :deep(.ant-btn-icon) { margin: 0; }

.editor-surface {
  padding: 24px 32px 32px;
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%), 0 4px 16px rgb(0 0 0 / 4%);
}

.editor-notice {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 26px;
  padding: 10px 16px;
  border: 1px solid #91caff;
  border-radius: 8px;
  background: #e6f4ff;
  color: #0958d9;
  font-size: 13px;
}

.editor-notice-icon {
  flex: 0 0 auto;
  font-size: 14px;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 40px;
  max-width: 980px;
}

.editor-grid .full-width {
  grid-column: 1 / -1;
}

.editor-grid :deep(.ant-form-item) {
  min-width: 0;
  margin-bottom: 18px;
}

.editor-grid :deep(.ant-form-item-label) {
  padding-bottom: 6px;
}

.editor-grid :deep(.ant-form-item-label > label) {
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  font-weight: 500;
}

.editor-grid :deep(.ant-input),
.editor-grid :deep(.ant-select-selector),
.editor-grid :deep(.ant-picker) {
  border-radius: 8px;
}

.editor-grid :deep(.ant-input),
.editor-grid :deep(.ant-select-single .ant-select-selector),
.editor-grid :deep(.ant-picker) {
  min-height: 36px;
}

.editor-grid :deep(.ant-input-textarea .ant-input) {
  min-height: 180px;
  padding-top: 10px;
  line-height: 1.8;
}

.disabled-field :deep(.ant-form-item-extra) {
  margin-top: 6px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.date-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.date-range :deep(.ant-picker) {
  width: 100%;
}

.attachment-upload {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
}

.attachment-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  padding: 24px;
  border: 1.5px dashed #d9d9d9;
  border-radius: 10px;
  background: #fafbfc;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.attachment-dropzone:hover,
.attachment-dropzone.is-dragging {
  border-color: #1677ff;
  background: #e6f4ff;
}

.attachment-dropzone :deep(.ant-upload-drag-icon) {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  margin: 0 0 8px;
  border-radius: 12px;
  background: #e6f4ff;
  color: #1677ff;
  font-size: 22px;
}

.attachment-dropzone:hover :deep(.ant-upload-drag-icon),
.attachment-dropzone.is-dragging :deep(.ant-upload-drag-icon) {
  background: #fff;
}

.attachment-dropzone :deep(.ant-upload-text) {
  margin: 0;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.attachment-dropzone :deep(.ant-upload-text b) {
  color: #1677ff;
}

.attachment-dropzone .attachment-hint {
  margin: 6px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.attachment-dropzone input[type='file'] {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

.attachment-upload :deep(.ant-btn) {
  align-self: flex-start;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.attachment-list li {
  padding: 10px 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafbfc;
}

.attachment-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.attachment-row > span:first-child {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-row :deep(.ant-space) {
  flex-shrink: 0;
}

.pending-attachments li {
  color: rgba(0, 0, 0, 0.65);
}

.attachment-preview-state {
  flex-shrink: 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.attachment-list img {
  display: block;
  max-width: 180px;
  max-height: 120px;
  margin-top: 10px;
  border-radius: 6px;
  object-fit: contain;
}

.edit-no-attachment {
  margin: 12px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.attachment-preview-dialog img {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  margin: 0 auto;
  object-fit: contain;
}

.attachment-preview-dialog iframe {
  width: 100%;
  height: 70vh;
  border: 0;
}

.preview-disclaimer {
  margin: 0 0 12px;
  color: rgba(0, 0, 0, 0.45);
  font-size: 13px;
}

.attachment-preview-dialog .editor-actions a {
  color: #1677ff;
}

.editor-footer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 200px;
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  min-height: 64px;
  padding: 14px 32px;
  border-top: 1px solid #f0f0f0;
  background: rgb(255 255 255 / 92%);
  box-shadow: 0 -4px 16px rgb(0 0 0 / 4%);
  backdrop-filter: blur(8px);
  z-index: 8;
}

.editor-save-state {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
}

.editor-footer-spacer {
  flex: 1;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 768px) {
  .editor-page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .editor-page-actions {
    width: 100%;
  }

  .editor-page-actions :deep(.ant-btn) {
    flex: 1;
  }

  .editor-updated-at { display: none; }
  .editor-refresh-button { flex: 0 0 34px !important; }

  .editor-surface {
    padding: 20px 22px 26px;
  }

  .editor-grid {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .editor-grid .full-width {
    grid-column: auto;
  }

  .editor-footer { padding: 12px 16px; }
}

@media (max-width: 640px) {
  .requirement-editor-form {
    padding-bottom: 118px;
  }

  .editor-surface {
    padding: 16px;
  }

  .editor-page-header h1 {
    font-size: 20px;
  }

  .editor-notice {
    align-items: flex-start;
  }

  .attachment-upload {
    align-items: stretch;
  }

  .date-range {
    grid-template-columns: 1fr;
  }

  .date-range > span {
    display: none;
  }

  .editor-save-state {
    display: none;
  }

  .editor-footer {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
