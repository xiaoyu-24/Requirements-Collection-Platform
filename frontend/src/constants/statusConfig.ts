/**
 * 状态/类型/枚举的集中配置：标签文案 + Tag 颜色 + Select options 派生。
 *
 * 用途：集中维护需求列表、系统和版本页面共用的状态文案。
 * 统一 Tag 颜色规范，便于后续调整。
 *
 * 使用方式：
 *   import { REQUIREMENT_STATUS } from '@/constants/statusConfig'
 *   <Tag :color="REQUIREMENT_STATUS[record.status].color">{{ REQUIREMENT_STATUS[record.status].label }}</Tag>
 */

type StatusMeta = Readonly<{ label: string; color: string }>

/** 需求状态（与后端 RequirementStatus 枚举一致） */
export const REQUIREMENT_STATUS = {
  PENDING_EVALUATION: { label: '待评估', color: 'gold' },
  CONFIRMED: { label: '已确认', color: 'blue' },
  IN_DEVELOPMENT: { label: '开发中', color: 'processing' },
  PAUSED: { label: '暂停', color: 'orange' },
  COMPLETED: { label: '已完成', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
  CLOSED: { label: '已关闭', color: 'default' },
} as const satisfies Record<string, StatusMeta>

/** 保存类型（与后端 RequirementSaveType 枚举一致） */
export const SAVE_TYPE = {
  SUBMITTED: { label: '正式需求', color: 'blue' },
  DRAFT: { label: '草稿', color: 'default' },
} as const satisfies Record<string, StatusMeta>

/** 附件预览状态（与后端 AttachmentPreviewStatus 枚举一致） */
export const ATTACHMENT_PREVIEW_STATUS = {
  DIRECT: { label: '', color: 'green' },
  PENDING: { label: '等待生成预览', color: 'gold' },
  CONVERTING: { label: '正在生成预览', color: 'processing' },
  READY: { label: '', color: 'green' },
  FAILED: { label: '预览生成失败', color: 'error' },
  UNAVAILABLE: { label: '预览不可用', color: 'default' },
} as const satisfies Record<string, StatusMeta>

/** 系统/版本状态（与后端 SystemStatus / SystemVersionStatus 一致） */
export const ENTITY_STATUS = {
  ACTIVE: { label: '启用', color: 'success' },
  INACTIVE: { label: '停用', color: 'default' },
} as const satisfies Record<string, StatusMeta>

export type RequirementStatusKey = keyof typeof REQUIREMENT_STATUS
export type SaveTypeKey = keyof typeof SAVE_TYPE
export type AttachmentPreviewStatusKey = keyof typeof ATTACHMENT_PREVIEW_STATUS
export type EntityStatusKey = keyof typeof ENTITY_STATUS

/** 派生 Select options（供筛选区/表单 Select 使用） */
const toOptions = <T extends Record<string, StatusMeta>>(meta: T) =>
  Object.entries(meta).map(([value, { label }]) => ({ value, label }))

export const requirementStatusOptions = toOptions(REQUIREMENT_STATUS)
export const saveTypeOptions = toOptions(SAVE_TYPE)
export const entityStatusOptions = toOptions(ENTITY_STATUS)

/** 辅助函数：根据枚举 key 安全获取 label + color，未知值回退到 default */
const fallbackMeta = (key: string): StatusMeta => ({ label: key, color: 'default' })

export const requirementStatusMeta = (status: string): StatusMeta =>
  REQUIREMENT_STATUS[status as RequirementStatusKey] ?? fallbackMeta(status)

export const saveTypeMeta = (saveType: string): StatusMeta =>
  SAVE_TYPE[saveType as SaveTypeKey] ?? fallbackMeta(saveType)

export const entityStatusMeta = (status: string): StatusMeta =>
  ENTITY_STATUS[status as EntityStatusKey] ?? fallbackMeta(status)

export const attachmentPreviewMeta = (status: string): StatusMeta =>
  ATTACHMENT_PREVIEW_STATUS[status as AttachmentPreviewStatusKey] ?? fallbackMeta(status)
