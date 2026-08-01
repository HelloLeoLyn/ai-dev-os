import type { JobStatus } from './job'

export interface TaskStatistics {
  total: number
  byStatus: Record<string, number>
}

export interface JobStatistics {
  total: number
  queued: number
  running: number
  succeeded: number
  failed: number
  successRate: number
}

export interface ExecutionStatistics {
  total: number
  successful: number
  failed: number
  unknown: number
  successRate: number
}

export interface RecentJobSummary {
  id: string
  taskId: string
  status: JobStatus
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  executionRecordId: string | null
  resultSummary: string | null
  errorMessage: string | null
}

export interface DashboardSummary {
  generatedAt: string
  tasks: TaskStatistics
  jobs: JobStatistics
  executions: ExecutionStatistics
  recentJobs: RecentJobSummary[]
}
