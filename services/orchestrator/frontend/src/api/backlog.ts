import { apiClient } from './client'
import type { BacklogConversionResult, BacklogDraft, BacklogItem, BacklogPriority, BacklogSourceType, BacklogStatus, ConvertBacklogRequest } from '../types/backlog'

export function getBacklog(filters: { status?: BacklogStatus; priority?: BacklogPriority; projectId?: string; sourceType?: BacklogSourceType } = {}): Promise<BacklogItem[]> {
  return apiClient.get<BacklogItem[]>('/api/backlog', filters as Record<string, string | undefined>)
}
export function createBacklog(request: BacklogDraft): Promise<BacklogItem> { return apiClient.post('/api/backlog', request) }
export function updateBacklog(id: string, request: Omit<BacklogDraft, 'status' | 'blockedReason'>): Promise<BacklogItem> {
  return apiClient.put(`/api/backlog/${encodeURIComponent(id)}`, request)
}
export function changeBacklogStatus(id: string, status: BacklogStatus, blockedReason?: string): Promise<BacklogItem> {
  const request: { status: BacklogStatus; blockedReason?: string } = { status }
  if (blockedReason !== undefined) request.blockedReason = blockedReason
  return apiClient.post(`/api/backlog/${encodeURIComponent(id)}/status`, request)
}
export function convertBacklog(id: string, request: ConvertBacklogRequest): Promise<BacklogConversionResult> {
  return apiClient.post(`/api/backlog/${encodeURIComponent(id)}/convert-to-task`, request)
}
