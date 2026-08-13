export type ValidationStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'BLOCKED' | 'SKIPPED'
export type ValidationDecision = 'PASS' | 'FAIL' | 'BLOCK' | 'REQUIRE_APPROVAL'

export interface ValidationCheck {
  checkId: string
  type: string
  name: string
  status: ValidationStatus
  required: boolean
  blocking: boolean
  startedAt?: string
  completedAt?: string
  durationMs: number
  summary?: string
  errorMessage?: string
  artifactIds: string[]
  metadata: Record<string, unknown>
}

export interface ValidationRun {
  validationRunId: string
  taskId: string
  projectId: string
  workspaceId: string
  planRunId?: string
  executionId?: string
  status: ValidationStatus
  startedAt: string
  completedAt?: string
  checks: ValidationCheck[]
  decision?: ValidationDecision
  summary?: string
}
