export interface TaskDefinition {
  id: string
  name: string | null
  description: string | null
  agentName: string | null
  requiredCapabilities: string[] | null
  parameters: Record<string, unknown> | null
  status: string | null
}

export type TaskStatus =
  | 'CREATED'
  | 'PLANNING'
  | 'APPROVED'
  | 'CODING'
  | 'TESTING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'COMPLETED'
  | 'REJECTED'
  | 'FAILED'

export interface TaskRecord {
  taskId: string
  name: string | null
  description: string | null
  status: TaskStatus
  createdAt: string
  updatedAt: string
  approvalId: string | null
  planRunId: string | null
  workspaceId: string | null
  projectId: string
  executionMode: ExecutionMode
  errorMessage: string | null
}

export type ExecutionMode = 'READ_ONLY' | 'READ_WRITE'

export interface CreateTaskRequest {
  name: string
  description: string
  goal: string
  plannerName: string
  projectId?: string
  workspaceId?: string
  executionMode: ExecutionMode
}
