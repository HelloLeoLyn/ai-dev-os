import { apiClient } from './client'
import type { AgentPackage } from '../types/agentPackage'

export function getAgentPackages(): Promise<AgentPackage[]> {
  return apiClient.get<AgentPackage[]>('/api/agent-market')
}

export function getAgentPackage(agentId: string): Promise<AgentPackage> {
  return apiClient.get<AgentPackage>(
    `/api/agent-market/${encodeURIComponent(agentId)}`,
  )
}

export function installAgentPackage(agentId: string): Promise<AgentPackage> {
  return apiClient.post<AgentPackage>(
    `/api/agent-market/${encodeURIComponent(agentId)}/install`,
  )
}

export function uninstallAgentPackage(agentId: string): Promise<AgentPackage> {
  return apiClient.post<AgentPackage>(
    `/api/agent-market/${encodeURIComponent(agentId)}/uninstall`,
  )
}
