import { apiClient } from './client'
import type { CreateMemoryRequest, MemoryRecord, MemoryType } from '../types/memory'

export function getMemories(projectId?: string, type?: MemoryType): Promise<MemoryRecord[]> {
  return apiClient.get<MemoryRecord[]>('/api/memory', { projectId, type })
}

export function createMemory(request: CreateMemoryRequest): Promise<MemoryRecord> {
  return apiClient.post<MemoryRecord>('/api/memory', request)
}

export function deleteMemory(id: string): Promise<void> {
  return apiClient.delete<void>(`/api/memory/${encodeURIComponent(id)}`)
}
