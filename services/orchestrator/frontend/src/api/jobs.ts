import { apiClient } from './client'
import type {
  ExecutionJob,
  JobFilters,
  JobSubmissionResponse,
} from '../types/job'

export function submitJob(taskId: string): Promise<JobSubmissionResponse> {
  return apiClient.post<JobSubmissionResponse>(`/api/tasks/${encodeURIComponent(taskId)}/jobs`)
}

export function getJob(jobId: string): Promise<ExecutionJob> {
  return apiClient.get<ExecutionJob>(`/api/jobs/${encodeURIComponent(jobId)}`)
}

export function getJobs(filters: JobFilters = {}): Promise<ExecutionJob[]> {
  return apiClient.get<ExecutionJob[]>('/api/jobs', {
    status: filters.status,
  })
}
