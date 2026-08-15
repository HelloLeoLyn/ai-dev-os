import { apiClient } from './client'
import type {
  ExecutionRecordDetail,
  ExecutionRecordFilters,
  ExecutionRecordSummary,
} from '../types/execution'
import type { ExecutionWorkspace } from '../types/executionWorkspace'

export function getExecutionWorkspace(taskId: string): Promise<ExecutionWorkspace> {
  return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/execution-workspace`)
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
