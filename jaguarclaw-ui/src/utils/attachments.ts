import type { AttachedContext, SessionFile } from '@/types'

export type AttachmentKind = 'image' | 'text' | 'pdf' | 'folder' | 'generic'

export interface AttachmentDescriptor {
  id: string
  name: string
  filePath: string
  sessionId?: string | null
  size?: number
  mimeType?: string
  extension: string
  kind: AttachmentKind
  typeLabel: string
  displayPath?: string
  previewUrl?: string
  downloadUrl?: string
}

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'])
const TEXT_EXTENSIONS = new Set([
  'txt',
  'md',
  'json',
  'js',
  'ts',
  'css',
  'html',
  'htm',
  'xml',
  'yaml',
  'yml',
  'csv',
  'py',
  'java',
  'sql',
])

export function getFileExtension(nameOrPath: string): string {
  const normalized = nameOrPath.replace(/\\/g, '/')
  const baseName = normalized.split('/').pop() || normalized
  const parts = baseName.split('.')
  return parts.length > 1 ? (parts[parts.length - 1] || '').toLowerCase() : ''
}

export function encodeWorkspacePath(path: string): string {
  return path.replace(/\\/g, '/').split('/').map(encodeURIComponent).join('/')
}

export function buildWorkspaceFileUrl(
  filePath: string,
  sessionId?: string | null,
  options?: { download?: boolean },
): string {
  const prefix = sessionId ? `${sessionId}/` : ''
  const query = options?.download ? '?download' : ''
  return `/api/workspace/${prefix}${encodeWorkspacePath(filePath)}${query}`
}

export function formatAttachmentSize(size?: number): string {
  if (!size || size < 0) return 'Unknown size'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export function inferAttachmentKind(name: string, mimeType?: string): AttachmentKind {
  const extension = getFileExtension(name)
  const normalizedMime = mimeType?.toLowerCase()

  if (extension === 'pdf' || normalizedMime === 'application/pdf') return 'pdf'

  if (normalizedMime?.startsWith('image/')) return 'image'
  if (IMAGE_EXTENSIONS.has(extension)) return 'image'

  if (normalizedMime?.startsWith('text/')) return 'text'
  if (
    normalizedMime &&
    [
      'application/json',
      'application/xml',
      'application/yaml',
      'text/markdown',
      'application/javascript',
    ].includes(normalizedMime)
  ) {
    return 'text'
  }
  if (TEXT_EXTENSIONS.has(extension)) return 'text'

  return 'generic'
}

export function getAttachmentTypeLabel(
  name: string,
  mimeType?: string,
  kind?: AttachmentKind,
): string {
  const extension = getFileExtension(name)
  const resolvedKind = kind ?? inferAttachmentKind(name, mimeType)

  if (resolvedKind === 'folder') return 'FOLDER'
  if (resolvedKind === 'pdf') return 'PDF'
  if (resolvedKind === 'image') return 'IMAGE'
  if (resolvedKind === 'text') {
    if (extension) return extension.toUpperCase()
    return 'TEXT'
  }

  if (extension === 'doc' || extension === 'docx') return 'DOC'
  if (extension === 'xls' || extension === 'xlsx') return 'XLS'
  if (extension === 'ppt' || extension === 'pptx') return 'PPT'
  if (extension) return extension.toUpperCase()
  return 'FILE'
}

export function toAttachmentDescriptorFromSessionFile(
  file: SessionFile,
  sessionId?: string | null,
): AttachmentDescriptor {
  const effectiveSessionId = sessionId ?? file.sessionId
  const kind = inferAttachmentKind(file.fileName || file.filePath, file.mimeType)

  return {
    id: file.id,
    name: file.fileName,
    filePath: file.filePath,
    sessionId: effectiveSessionId,
    size: file.fileSize,
    mimeType: file.mimeType,
    extension: getFileExtension(file.fileName || file.filePath),
    kind,
    typeLabel: getAttachmentTypeLabel(file.fileName || file.filePath, file.mimeType, kind),
    displayPath: file.filePath,
    previewUrl: buildWorkspaceFileUrl(file.filePath, effectiveSessionId),
    downloadUrl: buildWorkspaceFileUrl(file.filePath, effectiveSessionId, { download: true }),
  }
}

export function isFileAttachedContext(context: AttachedContext): boolean {
  return (
    context.type === 'file' && Boolean(context.filePath || context.filename || context.displayName)
  )
}

export function isFolderAttachedContext(context: AttachedContext): boolean {
  return context.type === 'folder' && Boolean(context.folderPath || context.displayName)
}

export function toAttachmentDescriptorFromContext(
  context: AttachedContext,
  sessionId?: string | null,
): AttachmentDescriptor | null {
  if (!isFileAttachedContext(context)) return null

  const filePath = context.filePath || context.filename || context.displayName
  const name = context.filename || context.displayName || filePath
  const kind = inferAttachmentKind(name || filePath, context.mimeType)

  return {
    id: context.id,
    name,
    filePath,
    sessionId,
    size: context.size,
    mimeType: context.mimeType,
    extension: getFileExtension(name || filePath),
    kind,
    typeLabel: getAttachmentTypeLabel(name || filePath, context.mimeType, kind),
    displayPath: filePath,
    previewUrl: buildWorkspaceFileUrl(filePath, sessionId),
    downloadUrl: buildWorkspaceFileUrl(filePath, sessionId, { download: true }),
  }
}

export function toFolderAttachmentDescriptor(
  context: AttachedContext,
): AttachmentDescriptor | null {
  if (!isFolderAttachedContext(context)) return null

  const folderPath = context.folderPath || context.displayName
  const name = context.displayName || folderPath

  return {
    id: context.id,
    name,
    filePath: folderPath,
    extension: '',
    kind: 'folder',
    typeLabel: 'FOLDER',
    displayPath: folderPath,
  }
}
