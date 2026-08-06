export interface AgentDefinition {
  name: string | null
  executor: string | null
  capabilities: string[] | null
  type: string | null
  description: string | null
  permissionLevel: string | null
  enabled: boolean
}

export type AgentRuntimeStatus = 'ONLINE' | 'IDLE' | 'RUNNING' | 'ERROR' | 'DISABLED'

export interface AgentStatusDTO {
  agentId: string
  name: string | null
  type: string | null
  status: AgentRuntimeStatus
  enabled: boolean
  capabilities: string[]
  lastHeartbeat: string | null
}

export interface AgentExecutionSummary {
  executionId: string
  jobId: string | null
  status: string | null
  startedAt: string | null
  completedAt: string | null
  message: string | null
}

export interface AgentDetailDTO {
  agentId: string
  name: string | null
  type: string | null
  status: AgentRuntimeStatus
  capabilities: string[]
  configuration: Record<string, unknown>
  lastActivity: string | null
  executions: AgentExecutionSummary[]
}

export interface AgentHistoryDTO {
  recentExecutions: AgentExecutionSummary[]
  successCount: number
  failedCount: number
  lastError: string | null
}
