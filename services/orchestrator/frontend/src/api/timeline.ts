import { apiClient } from './client'
import type { UnifiedTimeline } from '../types/timeline'

export function getTimeline(id: string): Promise<UnifiedTimeline> {
  return apiClient.get<UnifiedTimeline>(
    `/api/timeline/${encodeURIComponent(id)}`,
  )
}
