export type MemoryType = 'PROJECT_RULE' | 'HISTORY_TASK' | 'BUG_RECORD' | 'AGENT_EXPERIENCE'

export interface MemoryRecord {
  id: string
  projectId: string
  type: MemoryType
  key: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface CreateMemoryRequest {
  projectId: string
  type: MemoryType
  key: string
  content: string
}
