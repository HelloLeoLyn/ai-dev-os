export type AgentPlanStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface AgentExecutionPlan {
  planId: string
  taskId: string
  agentId: string
  step: number
  status: AgentPlanStatus
  result: string | null
  createdAt: string
  updatedAt: string
  startedAt: string | null
  completedAt: string | null
}
