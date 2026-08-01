import { apiClient } from './client'
import type { CreateScheduleRequest, ScheduledTask } from '../types/schedule'

export function getSchedules(): Promise<ScheduledTask[]> {
  return apiClient.get<ScheduledTask[]>('/api/schedules')
}

export function createSchedule(
  schedule: CreateScheduleRequest,
): Promise<ScheduledTask> {
  return apiClient.post<ScheduledTask>('/api/schedules', schedule)
}

export function deleteSchedule(scheduleId: string): Promise<void> {
  return apiClient.delete<void>(
    `/api/schedules/${encodeURIComponent(scheduleId)}`,
  )
}
