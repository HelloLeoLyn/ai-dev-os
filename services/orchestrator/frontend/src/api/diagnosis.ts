import { apiClient } from './client'

// V1 Failure Diagnosis（对应后端 FailureDiagnosis record）
export interface FailureDiagnosis {
  taskId: string | null
  source: string
  stage: string
  failedStepId: string | null
  errorCode: string
  code: string
  category: string
  summary: string
  rootCause: string
  evidence: string[]
  recommendedAction: string
  retryable: boolean
  fingerprint: string
  diagnosedAt: string
  // KNOWN-FAILURE-AND-DIAGNOSIS-HISTORY-V1
  knownFailure: boolean
  occurrenceCount: number
  firstSeenAt: string | null
  lastSeenAt: string | null
}

/**
 * 获取 Task 失败诊断；无失败（或正常人工 Gate）时返回 undefined。
 */
export function getDiagnosis(taskId: string): Promise<FailureDiagnosis | undefined> {
  return apiClient.get<FailureDiagnosis>(`/api/tasks/${encodeURIComponent(taskId)}/diagnosis`)
}
