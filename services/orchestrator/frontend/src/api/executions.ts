import { apiClient } from './client'
import type {
  ExecutionRecordDetail,
  ExecutionRecordFilters,
  ExecutionRecordSummary,
} from '../types/execution'
import type { ExecutionWorkspace } from '../types/executionWorkspace'

export interface ExecutionWorkspaceReview {
  taskId: string
  status: string
  sourceWorkspace: string
  executionWorkspace: string
  baseRevision: string
  sourceRevision?: string
  diff?: string
  diffCheck?: string
  changeStat?: string
  changedFiles: string[]
  untrackedFiles: string[]
  artifacts: string[]
  completeness: 'COMPLETE' | 'INCOMPLETE'
  incompleteReasons: string[]
  errorCode?: string
  reason?: string
}

export function getExecutionWorkspace(taskId: string): Promise<ExecutionWorkspace> {
  return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/execution-workspace`)
}

export function getExecutionWorkspaceReview(taskId: string): Promise<ExecutionWorkspaceReview> {
  return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/execution-workspace/review`)
}

export function promoteExecutionWorkspace(taskId: string): Promise<ExecutionWorkspace> {
  return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/execution-workspace/promote`)
}

export function rejectExecutionWorkspace(taskId: string): Promise<ExecutionWorkspace> {
  return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/execution-workspace/reject`)
}

export function getExecutionRecords(
  filters: ExecutionRecordFilters = {},
): Promise<ExecutionRecordSummary[]> {
  return apiClient.get<ExecutionRecordSummary[]>('/api/execution-records', {
    status: filters.status,
    taskId: filters.taskId,
  })
}

export function getExecutionRecord(recordId: string): Promise<ExecutionRecordDetail> {
  return apiClient.get<ExecutionRecordDetail>(
    `/api/execution-records/${encodeURIComponent(recordId)}`,
  )
}
