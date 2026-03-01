import { ref, readonly } from 'vue'
import type { Skill, SkillDetail } from '@/types'
import { useWebSocket } from './useWebSocket'

export type SkillViewScope = 'effective' | 'global'

export interface SkillQueryOptions {
  agentId?: string
  scope?: SkillViewScope
}

const skills = ref<Skill[]>([])
const loading = ref(false)
const selectedSkill = ref<string | null>(null)
const selectedSkillDetail = ref<SkillDetail | null>(null)
const detailLoading = ref(false)
const uploading = ref(false)
const uploadError = ref<string | null>(null)
const lastLoadedAgentId = ref<string | null>(null)
const lastLoadedScope = ref<SkillViewScope>('effective')

export function useSkills() {
  const { request } = useWebSocket()

  function normalizeOptions(options?: string | SkillQueryOptions): SkillQueryOptions {
    if (!options) return {}
    if (typeof options === 'string') {
      return { agentId: options }
    }
    return options
  }

  async function loadSkills(options?: string | SkillQueryOptions) {
    const { agentId, scope = 'effective' } = normalizeOptions(options)
    lastLoadedAgentId.value = agentId ?? null
    lastLoadedScope.value = scope
    const payload: Record<string, string> = { scope }
    if (scope === 'effective' && agentId) {
      payload.agentId = agentId
    }
    loading.value = true
    try {
      const result = await request<{ skills: Skill[] }>('skills.list', payload)
      skills.value = result.skills || []
    } catch (e) {
      console.error('[Skills] Failed to load:', e)
      skills.value = []
    } finally {
      loading.value = false
    }
  }

  async function selectSkill(name: string, options?: string | SkillQueryOptions) {
    const { agentId, scope = lastLoadedScope.value } = normalizeOptions(options)
    selectedSkill.value = name
    selectedSkillDetail.value = null
    detailLoading.value = true

    try {
      const payload: Record<string, string> = { name, scope }
      if (scope === 'effective' && agentId) {
        payload.agentId = agentId
      }
      const result = await request<SkillDetail>('skills.get', payload)
      selectedSkillDetail.value = result
    } catch (e) {
      console.error('[Skills] Failed to get detail:', e)
      selectedSkillDetail.value = null
    } finally {
      detailLoading.value = false
    }
  }

  function clearSelection() {
    selectedSkill.value = null
    selectedSkillDetail.value = null
  }

  async function uploadSkill(file: File) {
    uploading.value = true
    uploadError.value = null

    try {
      const base64 = await readFileAsBase64(file)
      await request<{ name: string }>('skills.upload', {
        fileName: file.name,
        content: base64
      })
      await loadSkills({
        agentId: lastLoadedAgentId.value ?? undefined,
        scope: lastLoadedScope.value,
      })
    } catch (e: any) {
      console.error('[Skills] Failed to upload:', e)
      uploadError.value = e?.message || 'Upload failed'
      throw e
    } finally {
      uploading.value = false
    }
  }

  function clearUploadError() {
    uploadError.value = null
  }

  return {
    skills: readonly(skills),
    loading: readonly(loading),
    selectedSkill: readonly(selectedSkill),
    selectedSkillDetail: readonly(selectedSkillDetail),
    detailLoading: readonly(detailLoading),
    uploading: readonly(uploading),
    uploadError: readonly(uploadError),
    loadSkills,
    selectSkill,
    clearSelection,
    uploadSkill,
    clearUploadError
  }
}

function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      // Strip the data:...;base64, prefix
      const base64 = result.split(',')[1] ?? ''
      resolve(base64)
    }
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}
