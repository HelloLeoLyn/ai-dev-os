export type TraceStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface TraceRecord {
  traceId: string
  taskId: string
  projectId: string | null
  graphId: string | null
  nodeId: string | null
  agentType: string | null
  toolId: string | null
  status: TraceStatus
  startTime: string
  endTime: string | null
  duration: number
  errorMessage: string | null
}

export interface UsageSummary {
  recordCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  estimatedCost: number
}

export interface TimelineEventDTO {
  eventId: string
  eventType: string
  sourceType: string
  sourceId: string
  status: string | null
  message: string
  timestamp: string
}

export interface UnifiedTimeline {
  scopeType: string
  scopeId: string
  events: TimelineEventDTO[]
}

export interface TaskObservability {
  taskId: string
  taskStatus: string
  timeline: UnifiedTimeline
  traces: TraceRecord[]
  agent: {
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
    executions: Array<{
      taskId: string
      agentId: string
      executionId: string
      durationMillis: number
      status: string
      createdAt: string | null
    }>
  }
  toolTraces: TraceRecord[]
  usage: UsageSummary
}

export interface ProjectObservability {
  projectId: string
  taskCount: number
  successCount: number
  failedCount: number
  successRate: number
  failureRate: number
  averageDurationMillis: number
  totalTokens: number
  estimatedCost: number
}

export interface AgentObservability {
  agentType: string
  executionCount: number
  successCount: number
  failedCount: number
  successRate: number
  averageDurationMillis: number
  totalTokens: number
  estimatedCost: number
}

export interface ToolMetrics {
  toolId: string
  executeCount: number
  successCount: number
  failedCount: number
  deniedCount: number
  averageDurationMillis: number
}
