import { apiClient } from './client'
import type {
  AgentDefinition,
  AgentDetailDTO,
  AgentHistoryDTO,
  AgentStatusDTO,
} from '../types/agent'

export function getAgents(): Promise<AgentDefinition[]> {
  return apiClient.get<AgentDefinition[]>('/api/agents')
}

export function getAgentRegistry(): Promise<AgentStatusDTO[]> {
  return apiClient.get<AgentStatusDTO[]>('/api/dashboard/agents')
}

export function getAgentDetail(agentId: string): Promise<AgentDetailDTO> {
  return apiClient.get<AgentDetailDTO>(`/api/agents/${encodeURIComponent(agentId)}`)
}

export function getAgentHistory(agentId: string): Promise<AgentHistoryDTO> {
  return apiClient.get<AgentHistoryDTO>(
    `/api/agents/${encodeURIComponent(agentId)}/history`,
  )
}
