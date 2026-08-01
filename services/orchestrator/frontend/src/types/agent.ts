export interface AgentDefinition {
  name: string | null
  executor: string | null
  capabilities: string[] | null
  type: string | null
  description: string | null
  permissionLevel: string | null
  enabled: boolean
}
