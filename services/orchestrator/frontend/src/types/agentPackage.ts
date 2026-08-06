export interface AgentPackage {
  agentId: string
  name: string
  version: string | null
  description: string | null
  author: string | null
  capabilities: string[]
  skills: string[]
  plugins: string[]
  executor: string | null
  executorConfig: Record<string, unknown>
  enabled: boolean
  installed: boolean
}
