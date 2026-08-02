import type { ExecutionResult } from './execution'
import type { TaskDefinition } from './task'

export type JobStatus = 'QUEUED' | 'RUNNING' | 'WAITING_APPROVAL' | 'SUCCESS' | 'FAILED'

export interface ExecutionJob {
  id: string
  taskId: string
  taskSnapshot: TaskDefinition
  createdAt: string
  status: JobStatus
  startedAt: string | null
  completedAt: string | null
  result: ExecutionResult | null
  executionRecordId: string | null
  resultSummary: string | null
  errorMessage: string | null
  error: string | null
  approvalId: string | null
}

export interface JobSubmissionResponse {
  jobId: string
  taskId: string
  status: JobStatus
}

export interface JobFilters {
  status?: JobStatus
}
