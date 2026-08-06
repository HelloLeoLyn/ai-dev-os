import { apiClient } from './client'
import type { CreateTaskRequest, TaskRecord } from '../types/task'

export function getTasks(): Promise<TaskRecord[]> {
  return apiClient.get<TaskRecord[]>('/api/tasks')
}

export function createTask(task: CreateTaskRequest): Promise<TaskRecord> {
  return apiClient.post<TaskRecord>('/api/tasks', task)
}

export function getTask(taskId: string): Promise<TaskRecord> {
  return apiClient.get<TaskRecord>(`/api/tasks/${encodeURIComponent(taskId)}`)
}
