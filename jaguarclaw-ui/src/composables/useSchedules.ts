import { ref, readonly } from 'vue'
import { useWebSocket } from './useWebSocket'
import type { ScheduleInfo, ScheduleCreatePayload, ScheduleUpdatePayload, ScheduleRunLog } from '@/types'

const schedules = ref<ScheduleInfo[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const runHistory = ref<Map<string, ScheduleRunLog[]>>(new Map())
const runHistoryLoading = ref<Set<string>>(new Set())

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

  return {
    schedules: readonly(schedules),
    loading: readonly(loading),
    error: readonly(error),
    runHistory: readonly(runHistory),
    runHistoryLoading: readonly(runHistoryLoading),
    loadSchedules,
    createSchedule,
    updateSchedule,
    removeSchedule,
    toggleSchedule,
    runSchedule,
    loadRunHistory
  }
}
