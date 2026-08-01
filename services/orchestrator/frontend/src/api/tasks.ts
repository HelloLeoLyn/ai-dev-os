import { apiClient } from './client'
import type { ExecutionResult } from '../types/execution'
import type { CreateTaskRequest, TaskDefinition } from '../types/task'

export function getTasks(): Promise<TaskDefinition[]> {
  return apiClient.get<TaskDefinition[]>('/api/tasks')
}

export function createTask(task: CreateTaskRequest): Promise<TaskDefinition> {
  return apiClient.post<TaskDefinition>('/api/tasks', task)
}

export function executeTask(taskId: string): Promise<ExecutionResult> {
  return apiClient.post<ExecutionResult>(
    `/api/tasks/${encodeURIComponent(taskId)}/execute`,
  )
}
