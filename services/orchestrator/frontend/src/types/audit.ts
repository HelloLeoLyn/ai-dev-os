export type TimelineScopeType = 'PLAN_RUN' | 'EXECUTION' | 'JOB'

export interface AuditEvent {
  id: string
  type: string
  occurredAt: string
  sequence: number
  aggregateType: string | null
  aggregateId: string | null
  fromStatus: string | null
  toStatus: string | null
  taskId: string | null
  planId: string | null
  planVersion: number | null
  planRunId: string | null
  stepRunId: string | null
  attemptId: string | null
  jobId: string | null
  executionId: string | null
  executionRecordId: string | null
  invocationId: string | null
  approvalId: string | null
  actorType: string | null
  actorId: string | null
  summary: string | null
  metadata: Record<string, unknown>
  schemaVersion: number
}

export interface ExecutionTimeline {
  scopeType: TimelineScopeType
  scopeId: string
  offset: number
  limit: number
  count: number
  events: AuditEvent[]
}

export type TimelineLoader = (
  id: string,
  offset?: number,
  limit?: number,
) => Promise<ExecutionTimeline>
