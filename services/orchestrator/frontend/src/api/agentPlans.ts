import { apiClient, apiRequest } from './client'
import type { AgentExecutionPlan } from '../types/agentPlan'

export function getAgentPlan(taskId: string): Promise<AgentExecutionPlan[]> {
  return apiClient.get<AgentExecutionPlan[]>(`/api/agent-plans/${encodeURIComponent(taskId)}`)
}

export function createAgentPlan(
  taskId: string,
  taskType?: string,
): Promise<AgentExecutionPlan[]> {
  return apiRequest<AgentExecutionPlan[]>(`/api/agent-plans/${encodeURIComponent(taskId)}`, {
    method: 'POST',
    query: taskType ? { taskType } : undefined,
  })
}
