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

function firstChange(changes: ChangeSet[]): ChangeSet | null {
  return changes[0] ?? null
}

function firstCommit(commits: CommitRecord[]): CommitRecord | null {
  return commits[0] ?? null
}

function pendingRemotePush(approvals: RemotePushApproval[]): boolean {
  return approvals.some(item => item.status === 'PENDING')
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
  const change = firstChange(input.changes)
  const commit = firstCommit(input.commits)
  const stages: DeliveryStage[] = [
    { key: 'CHANGE', label: STAGE_LABELS.CHANGE, status: changeStage(change) },
    { key: 'VALIDATION', label: STAGE_LABELS.VALIDATION, status: validationStage(change, input.validation) },
    { key: 'QUALITY_GATE', label: STAGE_LABELS.QUALITY_GATE, status: gateStage(input.validation, input.gate) },
    { key: 'COMMIT', label: STAGE_LABELS.COMMIT, status: commitStage(change, input.gate, commit) },
    { key: 'REMOTE_PUSH', label: STAGE_LABELS.REMOTE_PUSH, status: pushStage(input.remotePushApprovals) },
  ]
  const current = currentStage(stages, Boolean(change || commit || input.remotePushApprovals.length))
  return { stages, current }
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

function changeStage(change: ChangeSet | null): DeliveryStageStatus {
  if (!change) return 'NOT_STARTED'
  if (change.status === 'REJECTED') return 'FAILED'
  if (change.status === 'APPROVED' || change.status === 'COMMITTED') return 'SUCCESS'
  return 'ACTIVE'
}

function validationStage(change: ChangeSet | null, validation: ValidationRun | null): DeliveryStageStatus {
  if (!change || change.status !== 'APPROVED') return 'NOT_STARTED'
  if (!validation) return 'ACTIVE'
  switch (validation.status) {
    case 'SUCCESS': return 'SUCCESS'
    case 'FAILED':
    case 'ERROR':
    case 'BLOCKED': return 'FAILED'
    case 'SKIPPED': return 'SUCCESS'
    default: return 'ACTIVE'
  }
}

function gateStage(validation: ValidationRun | null, gate: QualityGateResult | null): DeliveryStageStatus {
  if (!validation || validation.status !== 'SUCCESS') return 'NOT_STARTED'
  if (!gate) return 'ACTIVE'
  if (gate.decision === 'PASS') return 'SUCCESS'
  if (gate.decision === 'REQUIRE_APPROVAL') return 'WAITING_APPROVAL'
  return 'FAILED'
}

function commitStage(change: ChangeSet | null, gate: QualityGateResult | null, commit: CommitRecord | null): DeliveryStageStatus {
  if (!change || change.status !== 'APPROVED') return 'NOT_STARTED'
  if (gate?.decision !== 'PASS') return 'NOT_STARTED'
  if (!commit) return 'ACTIVE'
  if (commit.status === 'SUCCESS') return 'SUCCESS'
  if (commit.status === 'FAILED') return 'FAILED'
  return 'ACTIVE'
}

function pushStage(approvals: RemotePushApproval[]): DeliveryStageStatus {
  if (!approvals.length) return 'NOT_STARTED'
  if (pendingRemotePush(approvals)) return 'WAITING_APPROVAL'
  if (approvals.some(item => item.status === 'REJECTED')) return 'FAILED'
  if (approvals.some(item => ['SUCCESS', 'CONSUMED', 'PUSHED'].includes(item.status))) return 'SUCCESS'
  return 'ACTIVE'
}

function currentStage(stages: DeliveryStage[], hasDelivery: boolean): string {
  if (!hasDelivery) return 'EXECUTION'
  for (const stage of stages) {
    if (stage.status === 'ACTIVE' || stage.status === 'WAITING_APPROVAL' || stage.status === 'FAILED') {
      return stage.key
    }
  }
  return stages.at(-1)?.key ?? 'EXECUTION'
}

export function primaryAction(input: ExecutionViewInput): PrimaryAction | null {
  if (interventionRequired(input.executionState)) return null
  if (input.codingApprovalPending) return { key: 'APPROVE_WORKSPACE_WRITE', label: 'Approve Workspace Write' }

  const pipeline = input.delivery
  if (pipeline) {
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

  const change = firstChange(input.changes)
  if (change) {
    if (change.status === 'CREATED') return { key: 'START_REVIEW', label: 'Start Review' }
    if (change.status === 'REVIEWING') return { key: 'APPROVE_CHANGE', label: 'Approve Change' }
    if (change.status === 'APPROVED') {
      if (!input.validation || ['FAILED', 'ERROR', 'BLOCKED'].includes(input.validation.status)) {
        return { key: 'RUN_VALIDATION', label: 'Run Validation' }
      }
      if (input.validation.status === 'SUCCESS' && !input.gate) {
        return { key: 'EVALUATE_GATE', label: 'Evaluate Quality Gate' }
      }
      if (input.gate?.decision === 'REQUIRE_APPROVAL') return { key: 'APPROVE_GATE', label: 'Approve Quality Gate' }
      if (input.gate?.decision === 'PASS') return { key: 'COMMIT_CHANGE', label: 'Commit Change' }
      if (input.gate?.decision === 'BLOCK') return { key: 'RERUN_VALIDATION', label: 'Re-run Validation' }
    }
  }

  const commit = firstCommit(input.commits)
  if (commit && commit.status === 'PENDING') return { key: 'RECOVER_COMMIT', label: 'Recover Commit State' }
  if (pendingRemotePush(input.remotePushApprovals)) return { key: 'APPROVE_REMOTE_PUSH', label: 'Approve Remote Push' }
  if ((input.workspaceStatus === 'COMPLETED' || input.workspaceStatus === 'PROMOTION_FAILED')
      && input.workspaceReviewComplete) {
    return { key: 'PROMOTE_WORKSPACE', label: 'Promote to Source Workspace' }
  }
  return null
}

export function workflowSummary(input: ExecutionViewInput): WorkflowSummary {
  const pipeline = input.delivery
  if (pipeline) {
    return pipelineSummary(input, pipeline)
  }
  const projection = deliveryStages(input)
  const state = input.executionState
  const stage = STAGE_LABELS[projection.current] ?? projection.current

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

  const change = firstChange(input.changes)
  let status = input.taskStatus
  let blockedReason = 'None'
  if (input.codingApprovalPending) {
    status = 'WAITING_APPROVAL'
    blockedReason = 'Workspace write approval required.'
  }
  else if (pendingRemotePush(input.remotePushApprovals)) {
    status = 'WAITING_APPROVAL'
    blockedReason = 'Remote push approval required.'
  }
  else if (input.gate?.decision === 'REQUIRE_APPROVAL') {
    status = 'WAITING_APPROVAL'
    blockedReason = gateReason(input.gate)
  }
  else if (input.gate?.decision === 'BLOCK') {
    status = 'BLOCKED'
    blockedReason = gateReason(input.gate)
  }
  else if (input.validation && ['FAILED', 'ERROR', 'BLOCKED'].includes(input.validation.status)) {
    status = 'FAILED'
    blockedReason = input.validation.summary || 'Delivery validation failed.'
  }
  else if (change?.status === 'REVIEWING') {
    status = 'REVIEWING'
    blockedReason = 'Change review pending.'
  }
  else if (change?.status === 'APPROVED' && !input.validation) {
    status = 'READY'
    blockedReason = 'None'
  }
  else if (change?.status === 'APPROVED' && input.validation?.status === 'SUCCESS' && !input.gate) {
    status = 'READY'
    blockedReason = 'None'
  }
  else if (change?.status === 'COMMITTED' || (change?.status === 'APPROVED' && input.gate?.decision === 'PASS')) {
    status = 'DELIVERING'
    blockedReason = 'None'
  }

  const primary = primaryAction(input)
  return {
    stage,
    status,
    blockedReason,
    nextAction: primary?.label ?? 'Monitor execution',
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

function gateReason(gate: QualityGateResult): string {
  const first = gate.reasons[0]
  return first?.message || 'Quality gate requires attention.'
}
