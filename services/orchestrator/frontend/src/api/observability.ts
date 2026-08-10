import { apiClient } from './client'
import type {
  AgentObservability,
  ProjectObservability,
  TaskObservability,
  ToolMetrics,
} from '../types/observability'

export function getTaskObservability(taskId: string): Promise<TaskObservability> {
  return apiClient.get<TaskObservability>(
    `/api/observability/tasks/${encodeURIComponent(taskId)}`,
  )
}

export function getProjectObservability(projectId: string): Promise<ProjectObservability> {
  return apiClient.get<ProjectObservability>(
    `/api/observability/projects/${encodeURIComponent(projectId)}`,
  )
}

export function getAgentObservability(agentType: string): Promise<AgentObservability> {
  return apiClient.get<AgentObservability>(
    `/api/observability/agents/${encodeURIComponent(agentType)}`,
  )
}

export function getToolMetrics(): Promise<ToolMetrics[]> {
  return apiClient.get<ToolMetrics[]>('/api/observability/tools')
}
