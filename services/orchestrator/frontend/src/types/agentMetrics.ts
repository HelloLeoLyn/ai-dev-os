export interface AgentMetrics {
  agentId: string
  agentName: string
  taskCount: number
  successCount: number
  failedCount: number
  retryCount: number
  averageDuration: number
  lastExecutedAt: string | null
  repairCount: number
  changeCount: number
}

export interface AgentExecutionMetric {
  taskId: string
  agentId: string
  executionId: string
  durationMillis: number
  status: string
  createdAt: string | null
}

export interface AgentMetricsDetail {
  metrics: AgentMetrics
  executions: AgentExecutionMetric[]
}

export interface TaskExecutionMetrics {
  taskId: string
  taskStatus: string
  executionCount: number
  successCount: number
  failedCount: number
  totalDurationMillis: number
  averageDurationMillis: number
  repairCount: number
  retryCount: number
  changeCount: number
  approvedChanges: number
  rejectedChanges: number
  reviewPassRate: number
  executions: AgentExecutionMetric[]
}
