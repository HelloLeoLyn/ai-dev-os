import { apiClient } from './client'
import type { McpPlugin } from '../types/mcpPlugin'

export function getMcpPlugins(): Promise<McpPlugin[]> {
  return apiClient.get<McpPlugin[]>('/api/mcp/plugins')
}

export function getMcpPlugin(pluginId: string): Promise<McpPlugin> {
  return apiClient.get<McpPlugin>(`/api/mcp/plugins/${encodeURIComponent(pluginId)}`)
}

export function enableMcpPlugin(pluginId: string): Promise<McpPlugin> {
  return apiClient.post<McpPlugin>(`/api/mcp/plugins/${encodeURIComponent(pluginId)}/enable`)
}

export function disableMcpPlugin(pluginId: string): Promise<McpPlugin> {
  return apiClient.post<McpPlugin>(`/api/mcp/plugins/${encodeURIComponent(pluginId)}/disable`)
}
