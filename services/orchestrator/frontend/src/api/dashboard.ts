import { apiClient } from './client'
import type {
  DashboardSummary,
  DashboardSummaryDTO,
  DashboardTimeline,
  ExecutionSummaryDTO,
  JobSummaryDTO,
} from '../types/dashboard'

export function getDashboard(): Promise<DashboardSummary> {
  return apiClient.get<DashboardSummary>('/api/dashboard')
}

export function getDashboardSummary(): Promise<DashboardSummaryDTO> {
  return apiClient.get<DashboardSummaryDTO>('/api/dashboard/summary')
}

export function getDashboardJobs(): Promise<JobSummaryDTO[]> {
  return apiClient.get<JobSummaryDTO[]>('/api/dashboard/jobs')
}

export function getDashboardExecutions(): Promise<ExecutionSummaryDTO[]> {
  return apiClient.get<ExecutionSummaryDTO[]>('/api/dashboard/executions')
}

export function getDashboardTimeline(id: string): Promise<DashboardTimeline> {
  return apiClient.get<DashboardTimeline>(
    `/api/dashboard/timeline/${encodeURIComponent(id)}`,
  )
}
