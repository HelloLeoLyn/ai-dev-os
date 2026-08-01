export interface TaskDefinition {
  id: string
  name: string | null
  description: string | null
  agentName: string | null
  requiredCapabilities: string[] | null
  status: string | null
}

export type CreateTaskRequest = TaskDefinition
