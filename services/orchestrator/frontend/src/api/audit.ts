import { apiClient } from './client'
import type { ExecutionTimeline } from '../types/audit'

const DEFAULT_LIMIT = 100

function getTimeline(
  scope: 'plan-runs' | 'executions' | 'jobs',
  id: string,
  offset = 0,
  limit = DEFAULT_LIMIT,
): Promise<ExecutionTimeline> {
  return apiClient.get<ExecutionTimeline>(
    `/api/timelines/${scope}/${encodeURIComponent(id)}`,
    { offset, limit },
  )
}

export function getPlanRunTimeline(
  id: string,
  offset?: number,
  limit?: number,
): Promise<ExecutionTimeline> {
  return getTimeline('plan-runs', id, offset, limit)
}

export function getExecutionTimeline(
  id: string,
  offset?: number,
  limit?: number,
): Promise<ExecutionTimeline> {
  return getTimeline('executions', id, offset, limit)
}

export function getJobTimeline(
  id: string,
  offset?: number,
  limit?: number,
): Promise<ExecutionTimeline> {
  return getTimeline('jobs', id, offset, limit)
}
