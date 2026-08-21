import type { ChangeSet } from '../api/changes'
import type { CommitRecord } from '../api/commits'
import type { DeliveryPipeline } from '../api/delivery'
import type { ExecutionState, InterventionAction } from '../api/planRuns'
import type { RemotePushApproval } from '../api/remotePush'
import type { QualityGateResult, ValidationRun } from '../types/validation'

export type DeliveryStageKey = 'CHANGE' | 'VALIDATION' | 'QUALITY_GATE' | 'COMMIT' | 'REMOTE_PUSH' | 'PR' | 'CI'
export type DeliveryStageStatus = 'NOT_STARTED' | 'ACTIVE' | 'SUCCESS' | 'FAILED' | 'WAITING_APPROVAL'

export interface DeliveryStage {
  key: DeliveryStageKey
  label: string
  status: DeliveryStageStatus
}

export interface DeliveryProjection {
  stages: DeliveryStage[]
  current: string
}

export interface ExecutionViewInput {
  changes: ChangeSet[]
  validation: ValidationRun | null
  gate: QualityGateResult | null
  commits: CommitRecord[]
  remotePushApprovals: RemotePushApproval[]
  codingApprovalPending: boolean
  executionState: ExecutionState | null
  workspaceStatus: string
  workspaceReviewComplete: boolean
  taskStatus: string
  delivery?: DeliveryPipeline | null
}

export interface PrimaryAction {
  key: string
  label: string
}

export interface WorkflowSummary {
  stage: string
  status: string
  blockedReason: string
  nextAction: string
  failureClass: string | null
  errorMessage: string | null
  recommendedAction: string | null
  severity: string | null
}

const STAGE_LABELS: Record<string, string> = {
  CHANGE: 'Change',
  VALIDATION: 'Validation',
  QUALITY_GATE: 'Quality Gate',
  COMMIT: 'Commit',
  REMOTE_PUSH: 'Remote Push',
  PR: 'Pull Request',
  CI: 'CI',
  EXECUTION: 'Execution',
}

const PIPELINE_STAGE_ORDER: DeliveryStageKey[] = [
  'CHANGE',
  'VALIDATION',
  'QUALITY_GATE',
  'COMMIT',
  'REMOTE_PUSH',
  'PR',
  'CI',
]

const PIPELINE_STAGE_MAP: Record<string, DeliveryStageKey> = {
  CHANGE_READY: 'CHANGE',
  VALIDATING: 'VALIDATION',
  QUALITY_GATE: 'QUALITY_GATE',
  COMMITTING: 'COMMIT',
  WAITING_REMOTE_PUSH_APPROVAL: 'REMOTE_PUSH',
  PUSHING: 'REMOTE_PUSH',
  CREATING_PR: 'PR',
  CI_CHECKING: 'CI',
  DELIVERY_COMPLETE: 'CI',
}

const INTERVENTION_STATUSES = new Set([
  'HUMAN_INTERVENTION_REQUIRED',
  'LIMIT_REACHED',
  'HUMAN_REQUIRED',
  'STOPPED_SYSTEM_FAILURE',
])

function interventionRequired(state: ExecutionState | null): boolean {
  return Boolean(state && INTERVENTION_STATUSES.has(state.interventionStatus))
}

export function recommendedActionLabel(action: string | null): string {
  switch (action) {
    case 'FIX_CREDENTIAL': return 'Fix credential → Retry'
    case 'CHECK_NETWORK': return 'Check network → Retry'
    case 'REVIEW_CODE': return 'Review code → Retry'
    case 'RETRY_MANUALLY': return 'Retry'
    case 'REPLAN': return 'Replan'
    case 'ABORT': return 'Abort'
    default: return action || ''
  }
}

/**
 * Maps the stored RecommendedAction to the human intervention API action.
 * The recommendation is only a suggestion; the user must click the button.
 */
export function interventionAction(recommendedAction: string | null): InterventionAction | null {
  switch (recommendedAction) {
    case 'FIX_CREDENTIAL':
    case 'CHECK_NETWORK':
    case 'REVIEW_CODE':
    case 'RETRY_MANUALLY':
      return 'RETRY'
    case 'REPLAN':
      return 'REPLAN'
    case 'ABORT':
      return 'ABORT'
    default:
      return null
  }
}

/**
 * ABORT is a destructive termination and must be confirmed by the user before
 * the intervention API is called. RETRY and REPLAN need no confirmation.
 */
export function requiresConfirmation(action: InterventionAction | null): boolean {
  return action === 'ABORT'
}

export function deliveryStages(input: ExecutionViewInput): DeliveryProjection {
  const pipeline = input.delivery
  if (pipeline) {
    return pipelineStages(pipeline)
  }
  // DELIVERY-LEGACY-FLOW-REMOVAL-V1：Pipeline 缺失 → 空投影，不再投影旧 Workspace/Change stage 链。
  return { stages: [], current: 'EXECUTION' }
}

/**
 * Projects the persisted delivery pipeline onto the UI stage chain. Stages
 * before the current one are SUCCESS, the current stage is ACTIVE /
 * WAITING_APPROVAL / FAILED, the rest are NOT_STARTED.
 */
function pipelineStages(pipeline: DeliveryPipeline): DeliveryProjection {
  let currentIdx: number
  let currentStatus: DeliveryStageStatus = 'ACTIVE'
  if (pipeline.status === 'COMPLETE') {
    currentIdx = PIPELINE_STAGE_ORDER.length - 1
    currentStatus = 'SUCCESS'
  }
  else if (pipeline.status === 'FAILED') {
    currentIdx = PIPELINE_STAGE_ORDER.indexOf(failedStage(pipeline))
    currentStatus = 'FAILED'
  }
  else if (pipeline.status === 'WAITING_APPROVAL') {
    currentIdx = PIPELINE_STAGE_ORDER.indexOf(PIPELINE_STAGE_MAP[pipeline.currentStage] ?? 'CHANGE')
    currentStatus = 'WAITING_APPROVAL'
  }
  else {
    currentIdx = PIPELINE_STAGE_ORDER.indexOf(PIPELINE_STAGE_MAP[pipeline.currentStage] ?? 'CHANGE')
  }
  const stages: DeliveryStage[] = PIPELINE_STAGE_ORDER.map((key, index) => ({
    key,
    label: STAGE_LABELS[key],
    status: index < currentIdx ? 'SUCCESS' : index > currentIdx ? 'NOT_STARTED' : currentStatus,
  }))
  return { stages, current: stages[currentIdx]?.key ?? 'EXECUTION' }
}

function failedStage(pipeline: DeliveryPipeline): DeliveryStageKey {
  if (pipeline.ciRunId) return 'CI'
  if (pipeline.pullRequestId) return 'PR'
  if (pipeline.remoteBranchId) return 'REMOTE_PUSH'
  if (pipeline.commitId) return 'COMMIT'
  if (pipeline.qualityGateId) return 'QUALITY_GATE'
  if (pipeline.validationRunId) return 'VALIDATION'
  return 'CHANGE'
}

export function primaryAction(input: ExecutionViewInput): PrimaryAction | null {
  if (interventionRequired(input.executionState)) return null
  if (input.codingApprovalPending) return { key: 'APPROVE_WORKSPACE_WRITE', label: 'Approve Workspace Write' }

  // DELIVERY-LEGACY-FLOW-REMOVAL-V1：Primary Action 只允许根据 DeliveryPipeline 决定。
  // Pipeline 存在 → 只按 currentStage/status；Pipeline 缺失 → 不再 fallback 旧 Workspace/Change 推导。
  const pipeline = input.delivery
  if (!pipeline) return null

  if (pipeline.status === 'WAITING_APPROVAL') {
    if (pipeline.currentStage === 'QUALITY_GATE') {
      return { key: 'APPROVE_GATE', label: 'Approve Quality Gate' }
    }
    if (pipeline.currentStage === 'WAITING_REMOTE_PUSH_APPROVAL') {
      return { key: 'APPROVE_REMOTE_PUSH', label: 'Approve Remote Push' }
    }
  }
  if (pipeline.status === 'FAILED') {
    return { key: 'RETRY_DELIVERY', label: 'Retry Delivery' }
  }
  return null
}

export function workflowSummary(input: ExecutionViewInput): WorkflowSummary {
  const pipeline = input.delivery
  if (pipeline) {
    return pipelineSummary(input, pipeline)
  }
  const stage = 'EXECUTION'
  const state = input.executionState

  if (interventionRequired(state)) {
    const failureClass = state?.lastFailureClass ?? null
    const recommendedAction = state?.recommendedAction ?? null
    return {
      stage,
      status: 'NEEDS_INTERVENTION',
      blockedReason: state?.lastReason ?? 'Execution requires human intervention.',
      nextAction: recommendedAction ? recommendedActionLabel(recommendedAction)
        : 'Human intervention required',
      failureClass,
      errorMessage: state?.lastReason ?? null,
      recommendedAction,
      severity: state?.lastSeverity ?? null,
    }
  }
  if (input.codingApprovalPending) {
    return {
      stage,
      status: 'WAITING_APPROVAL',
      blockedReason: 'Workspace write approval required.',
      nextAction: 'Approve Workspace Write',
      failureClass: null,
      errorMessage: null,
      recommendedAction: null,
      severity: null,
    }
  }

  // DELIVERY-LEGACY-FLOW-REMOVAL-V1：Pipeline 缺失时不再推进旧 Delivery 流程，
  // 只显示 Delivery pipeline unavailable / not started。
  return {
    stage,
    status: 'NOT_STARTED',
    blockedReason: 'Delivery pipeline not started.',
    nextAction: 'Delivery pipeline unavailable / not started',
    failureClass: null,
    errorMessage: null,
    recommendedAction: null,
    severity: null,
  }
}

function pipelineSummary(input: ExecutionViewInput, pipeline: DeliveryPipeline): WorkflowSummary {
  const stage = STAGE_LABELS[PIPELINE_STAGE_MAP[pipeline.currentStage] ?? pipeline.currentStage]
    ?? pipeline.currentStage
  if (pipeline.status === 'FAILED') {
    return {
      stage,
      status: 'FAILED',
      blockedReason: pipeline.failureReason || 'Delivery stage failed.',
      nextAction: 'Retry Delivery',
      failureClass: pipeline.failureClass,
      errorMessage: pipeline.failureReason || null,
      recommendedAction: null,
      severity: null,
    }
  }
  if (pipeline.status === 'WAITING_APPROVAL') {
    const blockedReason = pipeline.currentStage === 'QUALITY_GATE'
      ? 'Quality gate approval required.'
      : 'Remote push approval required.'
    const primary = primaryAction(input)
    return {
      stage,
      status: 'WAITING_APPROVAL',
      blockedReason,
      nextAction: primary?.label ?? 'Approval required',
      failureClass: null,
      errorMessage: null,
      recommendedAction: null,
      severity: null,
    }
  }
  if (pipeline.status === 'COMPLETE') {
    return {
      stage: 'Delivery',
      status: 'COMPLETE',
      blockedReason: 'None',
      nextAction: 'Monitor execution',
      failureClass: null,
      errorMessage: null,
      recommendedAction: null,
      severity: null,
    }
  }
  return {
    stage,
    status: 'DELIVERING',
    blockedReason: 'None',
    nextAction: 'Auto-advancing',
    failureClass: null,
    errorMessage: null,
    recommendedAction: null,
    severity: null,
  }
}
