import { apiClient } from './client'

export type DeliveryStageKey =
  | 'CHANGE_READY'
  | 'VALIDATING'
  | 'QUALITY_GATE'
  | 'COMMITTING'
  | 'WAITING_REMOTE_PUSH_APPROVAL'
  | 'PUSHING'
  | 'CREATING_PR'
  | 'CI_CHECKING'
  | 'DELIVERY_COMPLETE'
  | 'WAITING_APPROVAL'
  | 'FAILED'

export type DeliveryStatus = 'RUNNING' | 'WAITING_APPROVAL' | 'COMPLETE' | 'FAILED'

export interface DeliveryPipeline {
  taskId: string
  changeSetId: string
  executionWorkspaceId: string
  currentStage: DeliveryStageKey
  status: DeliveryStatus
  validationRunId: string
  qualityGateId: string
  commitId: string
  remotePushApprovalId: string
  remoteBranchId: string
  pullRequestId: string
  ciRunId: string
  failureClass: string | null
  failureReason: string
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export function getDeliveryPipeline(taskId: string): Promise<DeliveryPipeline> {
  return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/delivery`)
}

export function advanceDelivery(taskId: string): Promise<DeliveryPipeline> {
  return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/delivery/advance`)
}
