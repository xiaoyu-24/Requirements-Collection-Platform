import dayjs from 'dayjs'

/**
 * 剪贴板解析结果（对应 design.md D1 契约）
 *
 * - files：可直接送入组件 addFiles(...) 的 File 列表（位图 + 剪贴板文件 + HTML data URL）
 * - remoteImageUrls：富文本中检测到的远程图片 URL，仅用于 UI 提示条，不发起任何请求
 * - rejectedReasons：聚合的拒绝原因，组件用 message.warning 一次性展示
 */
export interface ClipboardParseResult {
  files: File[]
  remoteImageUrls: string[]
  rejectedReasons: string[]
}

export interface ClipboardAttachmentOptions {
  /** 允许的扩展名集合（小写），与组件内 addFiles 使用同一份规则 */
  allowedExtensions: Set<string>
  /** 单文件字节数上限 */
  maxFileSizeBytes: number
  /** 单次粘贴文件数上限，默认 20（design.md D7） */
  maxFilesPerPaste?: number
  /** 单次粘贴 HTML 字符串长度上限（字符），默认 10 MB（design.md D7） */
  maxHtmlChars?: number
}

/** MIME → 白名单扩展名映射；未列出的 MIME 一律拒绝（tasks 2.10） */
const MIME_TO_EXTENSION: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
}

/** 明确拒绝的 MIME：SVG 可携带 <script> 与外部实体，是存储型 XSS 常见载体（tasks 2.6） */
const BLOCKED_MIMES = new Set(['image/svg+xml'])

/** 浏览器安全策略不允许访问的协议，纳入远程图片提示条并附加标注（tasks 2.7） */
const INACCESSIBLE_PROTOCOLS = new Set(['file:', 'cid:', 'ftp:'])

const DEFAULT_MAX_FILES_PER_PASTE = 20
const DEFAULT_MAX_HTML_CHARS = 10 * 1024 * 1024

/** 时间戳：Asia/Shanghai 本地时区，与后端 hibernate.jdbc.time_zone 一致（design.md D5） */
const nowStamp = () => dayjs().format('YYYYMMDD-HHmmss')

const extensionOf = (name: string): string => {
  const idx = name.lastIndexOf('.')
  return idx < 0 ? '' : name.slice(idx + 1).toLowerCase()
}

/**
 * Blob → File 转换，含 Safari 早期版本降级（tasks 2.13）。
 * Safari < 14 不支持 File 构造器时，把 name/lastModified 挂到 Blob 上，
 * 下游 addFiles 仍能读到 name 做去重与提示。
 */
const toFile = (blob: Blob, name: string): File => {
  try {
    return new File([blob], name, { type: blob.type, lastModified: Date.now() })
  } catch {
    const patched = blob as Blob & { name?: string; lastModified?: number }
    patched.name = name
    patched.lastModified = Date.now()
    return patched as unknown as File
  }
}

/** base64 → Blob 解码；解码失败返回 null，静默降级（不弹技术错误） */
const base64ToBlob = (base64: string, mime: string): Blob | null => {
  try {
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
    return new Blob([bytes], { type: mime })
  } catch {
    return null
  }
}

/**
 * 客户端校验（tasks 2.11）：扩展名白名单 + 单文件大小
 * 与组件内 addFiles 使用同一份 options，规则完全一致，不会分歧
 */
const validateFile = (
  file: File,
  options: ClipboardAttachmentOptions,
): { ok: true } | { ok: false; reason: string } => {
  const ext = extensionOf(file.name)
  const maxMB = Math.round(options.maxFileSizeBytes / 1024 / 1024)
  if (!options.allowedExtensions.has(ext) || file.size > options.maxFileSizeBytes) {
    return { ok: false, reason: `以下附件格式不支持或超过 ${maxMB}MB：${file.name}` }
  }
  return { ok: true }
}

/**
 * 剪贴板 → 附件解析器（design.md D1）
 *
 * 关键工程约束（design.md D6）：`items.getAsFile()` 与 `getData('text/html')`
 * 必须在 paste 事件回调的同步阶段完成，Firefox/Safari 部分版本在事件返回后
 * 会清空 DataTransferItemList，异步回来拿到的都是 null。
 */
export function useClipboardAttachments(options: ClipboardAttachmentOptions) {
  const maxFilesPerPaste = options.maxFilesPerPaste ?? DEFAULT_MAX_FILES_PER_PASTE
  const maxHtmlChars = options.maxHtmlChars ?? DEFAULT_MAX_HTML_CHARS

  /** 浏览器兼容性检测（tasks 2.2，spec "浏览器兼容性 / ClipboardEvent.clipboardData 不可用"） */
  const isPasteSupported = (): boolean =>
    typeof ClipboardEvent !== 'undefined' && 'clipboardData' in ClipboardEvent.prototype

  const parseClipboard = async (event: ClipboardEvent): Promise<ClipboardParseResult> => {
    const empty: ClipboardParseResult = { files: [], remoteImageUrls: [], rejectedReasons: [] }
    const data = event.clipboardData
    if (!data) return empty

    // ===== 阶段 1：同步收集原始素材（不可 await，见 design.md D6） =====
    const clipboardFiles: File[] = data.files && data.files.length > 0 ? Array.from(data.files) : []
    const bitmapBlobs: Blob[] = []
    const svgDetected = { value: false }

    // tasks 2.4：clipboardData.files 优先级高于 items，避免重复入队
    if (clipboardFiles.length === 0 && data.items && data.items.length > 0) {
      for (let i = 0; i < data.items.length; i += 1) {
        const item = data.items[i]
        if (item.kind !== 'file') continue
        if (BLOCKED_MIMES.has(item.type)) {
          svgDetected.value = true
          continue
        }
        if (!item.type.startsWith('image/')) continue
        const f = item.getAsFile() // 必须同步调用
        if (f) bitmapBlobs.push(f)
      }
    }
    const htmlString = data.getData('text/html') || '' // 必须同步调用

    const rejectedReasons: string[] = []
    if (svgDetected.value) rejectedReasons.push('SVG 格式不支持（安全策略）')

    // ===== 阶段 2：数量与 HTML 大小上限（tasks 2.12，design.md D7） =====
    const estimatedHtmlImageCount = htmlString ? (htmlString.match(/<img\s/gi) || []).length : 0
    const totalCandidates = clipboardFiles.length + bitmapBlobs.length + estimatedHtmlImageCount
    if (totalCandidates > maxFilesPerPaste || htmlString.length > maxHtmlChars) {
      return {
        files: [],
        remoteImageUrls: [],
        rejectedReasons: [...rejectedReasons, '单次粘贴内容过多，请分批操作'],
      }
    }

    const files: File[] = []
    const remoteImageUrls: string[] = []
    const stamp = nowStamp()

    // ===== 阶段 3：clipboardData.files 分支（tasks 2.4） =====
    if (clipboardFiles.length > 0) {
      for (const f of clipboardFiles) {
        // 保留原文件名，与拖拽路径完全等价
        const verdict = validateFile(f, options)
        if (verdict.ok) files.push(f)
        else rejectedReasons.push(verdict.reason)
      }
    } else if (bitmapBlobs.length > 0) {
      // ===== 阶段 4：位图 blob 分支（tasks 2.3, 2.9, 2.10, 2.13） =====
      bitmapBlobs.forEach((blob, index) => {
        const ext = MIME_TO_EXTENSION[blob.type.toLowerCase()]
        if (!ext) {
          rejectedReasons.push(`以下附件格式不支持：${blob.type || '未知图片'}`)
          return
        }
        const name = bitmapBlobs.length === 1
          ? `截图-${stamp}.${ext}`
          : `截图-${stamp}-${index + 1}.${ext}`
        const file = toFile(blob, name)
        const verdict = validateFile(file, options)
        if (verdict.ok) files.push(file)
        else rejectedReasons.push(verdict.reason)
      })
    }

    // ===== 阶段 5：富文本 HTML 分支（tasks 2.5, 2.7, 2.8） =====
    if (htmlString) {
      let doc: Document | null = null
      try {
        // design.md D4：DOMParser 独立文档，<script> 不执行、<img> 不加载
        doc = new DOMParser().parseFromString(htmlString, 'text/html')
        if (doc.querySelector('parsererror')) doc = null
      } catch {
        doc = null
      }
      // tasks 2.8：解析异常静默降级，不影响 items/files 分支
      if (doc) {
        const imgs = doc.querySelectorAll('img[src]')
        let dataUrlIndex = 0
        imgs.forEach((img) => {
          const src = img.getAttribute('src') || ''
          if (!src) return

          if (src.toLowerCase().startsWith('data:')) {
            const match = /^data:([^;,]+)(;base64)?,(.*)$/i.exec(src)
            if (!match) return
            const mime = match[1].toLowerCase()
            const isBase64 = Boolean(match[2])
            const payload = match[3]
            if (BLOCKED_MIMES.has(mime)) {
              rejectedReasons.push('SVG 格式不支持（安全策略）')
              return
            }
            const ext = MIME_TO_EXTENSION[mime]
            if (!ext || !isBase64) return // 非白名单 MIME 或非 base64 编码，忽略
            const blob = base64ToBlob(payload, mime)
            if (!blob) return // 解码失败，静默跳过
            dataUrlIndex += 1
            const name = `粘贴图片-${stamp}-${dataUrlIndex}.${ext}`
            const file = toFile(blob, name)
            const verdict = validateFile(file, options)
            if (verdict.ok) files.push(file)
            else rejectedReasons.push(verdict.reason)
            return
          }

          // tasks 2.7：远程 URL 收集，不发起任何网络请求
          const protocolMatch = /^([a-z][a-z0-9+.-]*):/i.exec(src)
          if (!protocolMatch) return // 相对 URL / 空 src，忽略
          const protocol = protocolMatch[1].toLowerCase() + ':'
          if (protocol === 'http:' || protocol === 'https:') {
            remoteImageUrls.push(src)
          } else if (INACCESSIBLE_PROTOCOLS.has(protocol)) {
            remoteImageUrls.push(`${src}（浏览器安全策略不允许访问，请手动附加原文件）`)
          }
          // 其他协议（gopher:、javascript: 等）忽略
        })
      }
    }

    // 拒绝原因去重（同一原因可能出现多次，例如多张 SVG）
    const dedupedReasons = Array.from(new Set(rejectedReasons))
    return { files, remoteImageUrls, rejectedReasons: dedupedReasons }
  }

  return { parseClipboard, isPasteSupported }
}
