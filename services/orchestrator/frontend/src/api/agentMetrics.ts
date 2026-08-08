import { apiClient } from './client'
import type {
  AgentMetrics,
  AgentMetricsDetail,
  TaskExecutionMetrics,
} from '../types/agentMetrics'

export function getAgentMetrics(): Promise<AgentMetrics[]> {
  return apiClient.get<AgentMetrics[]>('/api/metrics/agents')
}

export function getAgentMetricsDetail(agentId: string): Promise<AgentMetricsDetail> {
  return apiClient.get<AgentMetricsDetail>(`/api/metrics/agents/${encodeURIComponent(agentId)}`)
}

export function getTaskExecutionMetrics(taskId: string): Promise<TaskExecutionMetrics> {
  return apiClient.get<TaskExecutionMetrics>(`/api/metrics/tasks/${encodeURIComponent(taskId)}`)
}
