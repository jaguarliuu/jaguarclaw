import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type {
  Message,
  ScheduleCreatePayload,
  ScheduleInfo,
  ScheduleRunDetail,
  ScheduleRunLog,
  ScheduleRunTraceItem,
  ScheduleUpdatePayload,
  Session,
  SessionFile,
  StreamBlock
} from '@/types'

const schedules = ref<ScheduleInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const runHistory = ref<Map<string, ScheduleRunLog[]>>(new Map())
const runHistoryLoading = ref<Set<string>>(new Set())
const runDetails = ref<Map<string, ScheduleRunDetail>>(new Map())
const runDetailLoading = ref<Set<string>>(new Set())

export function useSchedules() {
  const { request } = useWebSocket()

  async function loadSchedules() {
    loading.value = true
    error.value = null
    try {
      const result = await request<ScheduleInfo[]>('schedule.list')
      schedules.value = result
    } catch (e) {
      console.error('[Schedules] Failed to load schedules:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load schedules'
      schedules.value = []
    } finally {
      loading.value = false
    }
  }

  async function createSchedule(payload: ScheduleCreatePayload): Promise<ScheduleInfo> {
    error.value = null
    try {
      const result = await request<ScheduleInfo>('schedule.create', payload)
      await loadSchedules()
      return result
    } catch (e) {
      console.error('[Schedules] Failed to create schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to create schedule'
      throw e
    }
  }

  async function updateSchedule(payload: ScheduleUpdatePayload): Promise<ScheduleInfo> {
    error.value = null
    try {
      const result = await request<ScheduleInfo>('schedule.update', payload)
      await loadSchedules()
      return result
    } catch (e) {
      console.error('[Schedules] Failed to update schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to update schedule'
      throw e
    }
  }

  async function removeSchedule(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('schedule.delete', { id })
      await loadSchedules()
    } catch (e) {
      console.error('[Schedules] Failed to remove schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to remove schedule'
      throw e
    }
  }

  async function toggleSchedule(id: string, enabled: boolean): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('schedule.toggle', { id, enabled })
      await loadSchedules()
    } catch (e) {
      console.error('[Schedules] Failed to toggle schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to toggle schedule'
      throw e
    }
  }

  async function runSchedule(id: string): Promise<void> {
    error.value = null
    try {
      await request<{ success: boolean }>('schedule.run', { id })
      await loadRunHistory(id)
      await loadSchedules()
    } catch (e) {
      console.error('[Schedules] Failed to run schedule:', e)
      error.value = e instanceof Error ? e.message : 'Failed to run schedule'
      throw e
    }
  }

  async function loadRunHistory(taskId: string): Promise<void> {
    runHistoryLoading.value = new Set(runHistoryLoading.value).add(taskId)
    try {
      const result = await request<ScheduleRunLog[]>('schedule.runs.list', { taskId, limit: 10 })
      runHistory.value = new Map(runHistory.value).set(taskId, result)
    } catch (e) {
      console.error('[Schedules] Failed to load run history:', e)
    } finally {
      const next = new Set(runHistoryLoading.value)
      next.delete(taskId)
      runHistoryLoading.value = next
    }
  }

  async function loadRunDetail(id: string): Promise<ScheduleRunDetail> {
    runDetailLoading.value = new Set(runDetailLoading.value).add(id)
    try {
      const result = await request<{
        run: ScheduleRunLog
        session: Session | null
        messages: Message[]
        files: SessionFile[]
        trace: ScheduleRunTraceItem[]
      }>('schedule.runs.get', { id })

      const detail: ScheduleRunDetail = {
        run: result.run,
        session: result.session,
        files: result.files || [],
        trace: result.trace || [],
        messages: attachFilesToMessages(result.messages || [], result.files || [])
      }

      runDetails.value = new Map(runDetails.value).set(id, detail)
      return detail
    } catch (e) {
      console.error('[Schedules] Failed to load run detail:', e)
      error.value = e instanceof Error ? e.message : 'Failed to load schedule run detail'
      throw e
    } finally {
      const next = new Set(runDetailLoading.value)
      next.delete(id)
      runDetailLoading.value = next
    }
  }

  function attachFilesToMessages(messages: Message[], files: SessionFile[]): Message[] {
    const filesByRun: Record<string, SessionFile[]> = {}
    for (const file of files) {
      if (!file.runId) continue
      if (!filesByRun[file.runId]) {
        filesByRun[file.runId] = []
      }
      filesByRun[file.runId]!.push(file)
    }

    return messages.map((message) => {
      if (message.role !== 'assistant' || !message.runId || !filesByRun[message.runId]) {
        return message
      }

      const existingBlocks = message.blocks || []
      const baseBlocks: StreamBlock[] =
        existingBlocks.length === 0 && message.content
          ? [{ id: `text-${message.id}`, type: 'text', content: message.content }]
          : existingBlocks

      const fileBlocks: StreamBlock[] = filesByRun[message.runId]!.map((file) => ({
        id: `file-${file.id}`,
        type: 'file',
        file
      }))

      return {
        ...message,
        blocks: [...baseBlocks, ...fileBlocks]
      }
    })
  }

  return {
    schedules: readonly(schedules),
    loading: readonly(loading),
    error: readonly(error),
    runHistory: readonly(runHistory),
    runHistoryLoading: readonly(runHistoryLoading),
    runDetails: readonly(runDetails),
    runDetailLoading: readonly(runDetailLoading),
    loadSchedules,
    createSchedule,
    updateSchedule,
    removeSchedule,
    toggleSchedule,
    runSchedule,
    loadRunHistory,
    loadRunDetail
  }
}
