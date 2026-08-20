import { apiClient } from './client'

export interface ExecutionState {
  runId: string
  totalAttempts: number
  aiAttempts: number
  toolAttempts: number
  repairAttempts: number
  replanAttempts: number
  consecutiveFailures: number
  interventionStatus: string
  lastFailureClass: string | null
  lastSeverity: string | null
  lastResponse: string | null
  lastAttempt: number
  lastMaxAttempts: number
  lastReason: string | null
  recommendedAction: string | null
}

export type InterventionAction = 'RETRY' | 'REPLAN' | 'ABORT'

export function getExecutionState(runId: string): Promise<ExecutionState> {
  return apiClient.get(`/api/plan-runs/${encodeURIComponent(runId)}/execution-state`)
}

export function intervene(runId: string, action: InterventionAction, comment?: string): Promise<unknown> {
  return apiClient.post(`/api/plan-runs/${encodeURIComponent(runId)}/intervention`, { action, comment })
}
