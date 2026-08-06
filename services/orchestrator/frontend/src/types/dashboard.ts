import type { AuditEvent } from './audit'
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


export interface HealthSummary {
  status: string
  ready: boolean
}

export interface AgentSummary {
  total: number
  enabled: number
}

export interface RecoverySummary {
  pending: number
}

export interface DashboardSummaryDTO {
  health: HealthSummary
  agents: AgentSummary
  jobs: JobStatistics
  executions: ExecutionStatistics
  recovery: RecoverySummary
}


export interface JobSummaryDTO {
  jobId: string
  status: string
  priority: number
  leaseOwner: string | null
  createdAt: string
  updatedAt: string
}

export interface ExecutionSummaryDTO {
  executionId: string
  jobId: string | null
  status: string | null
  attempt: number
  failureReason: string | null
  createdAt: string | null
}

export interface DashboardTimeline {
  scopeType: string
  scopeId: string
  events: AuditEvent[]
}
