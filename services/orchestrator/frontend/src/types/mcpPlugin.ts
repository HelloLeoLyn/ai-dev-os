export type PluginToolAccess = 'READ_ONLY' | 'WORKSPACE_WRITE'

export interface McpPluginTool {
  name: string
  description: string | null
  access: PluginToolAccess
  dangerous: boolean
}

export interface McpPlugin {
  pluginId: string
  name: string
  type: string
  description: string | null
  permissionLevel: string
  enabled: boolean
  tools: McpPluginTool[]
}
