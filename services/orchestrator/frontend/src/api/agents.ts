import { apiClient } from './client'
import type { AgentDefinition } from '../types/agent'

export function getAgents(): Promise<AgentDefinition[]> {
  return apiClient.get<AgentDefinition[]>('/api/agents')
}
