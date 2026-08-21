import { describe, expect, it } from 'vitest'
import { deliveryStages, interventionAction, primaryAction, requiresConfirmation, workflowSummary, type ExecutionViewInput } from './executionDelivery'
import type { ChangeSet } from '../api/changes'
import type { CommitRecord } from '../api/commits'
import type { ExecutionState } from '../api/planRuns'
import type { RemotePushApproval } from '../api/remotePush'
import type { ValidationRun } from '../types/validation'

function base(): ExecutionViewInput {
  return {
    changes: [],
    validation: null,
    gate: null,
    commits: [],
    remotePushApprovals: [],
    codingApprovalPending: false,
    executionState: null,
    workspaceStatus: 'PENDING',
    workspaceReviewComplete: false,
    taskStatus: 'RUNNING',
  }
}

function change(status: string): ChangeSet {
  return { changeId: 'c1', taskId: 't1', workspaceId: 'w1', projectId: 'p1', executionId: 'e1', branch: 'feature/x', diff: '', diffStat: '', filesChanged: 1, status }
}

function commit(status: string): CommitRecord {
  return { commitId: 'cm1', taskId: 't1', changeId: 'c1', branch: 'feature/x', status }
}

function pushApproval(status: string): RemotePushApproval {
  return { approvalId: 'r1', taskId: 't1', executionWorkspaceId: 'w1', executionBranch: 'feature/x', commitId: 'cm1', commitHash: 'abc', remote: 'origin', targetRef: 'refs/heads/feature/x', authority: 'REMOTE', operation: 'PUSH_TASK_BRANCH', status }
}

function validation(status: string): ValidationRun {
  return { validationRunId: 'v1', taskId: 't1', projectId: 'p1', workspaceId: 'w1', status: status as ValidationRun['status'], startedAt: '2026-01-01T00:00:00Z', checks: [], delivery: true }
}

function executionState(interventionStatus: string, overrides: Partial<ExecutionState> = {}): ExecutionState {
  return { runId: 'r1', totalAttempts: 1, aiAttempts: 0, toolAttempts: 1, repairAttempts: 0, replanAttempts: 0, consecutiveFailures: 3, interventionStatus, lastFailureClass: 'CREDENTIAL_MISSING', lastSeverity: 'L3_HUMAN_REQUIRED', lastResponse: 'REQUEST_HUMAN', lastAttempt: 1, lastMaxAttempts: 2, lastReason: 'DeepSeek credential is not configured.', recommendedAction: 'FIX_CREDENTIAL', ...overrides }
}

function pipeline(overrides: Partial<import('../api/delivery').DeliveryPipeline> = {}): import('../api/delivery').DeliveryPipeline {
  return {
    taskId: 't1',
    changeSetId: 'c1',
    executionWorkspaceId: 'w1',
    currentStage: 'VALIDATING',
    status: 'RUNNING',
    validationRunId: '',
    qualityGateId: '',
    commitId: '',
    remotePushApprovalId: '',
    remoteBranchId: '',
    pullRequestId: '',
    ciRunId: '',
    failureClass: null,
    failureReason: '',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    ...overrides,
  }
}

describe('execution delivery primary action', () => {
  it('WAITING workspace approval derives Approve Workspace Write', () => {
    const input = { ...base(), codingApprovalPending: true }
    expect(primaryAction(input)).toEqual({ key: 'APPROVE_WORKSPACE_WRITE', label: 'Approve Workspace Write' })
  })

  it('NEEDS_INTERVENTION surfaces failure and recommended action', () => {
    const input = { ...base(), executionState: executionState('HUMAN_INTERVENTION_REQUIRED') }
    const summary = workflowSummary(input)
    expect(summary.status).toBe('NEEDS_INTERVENTION')
    expect(summary.failureClass).toBe('CREDENTIAL_MISSING')
    expect(summary.severity).toBe('L3_HUMAN_REQUIRED')
    expect(summary.errorMessage).toBe('DeepSeek credential is not configured.')
    expect(summary.recommendedAction).toBe('FIX_CREDENTIAL')
    expect(summary.nextAction).toBe('Fix credential → Retry')
    expect(primaryAction(input)).toBeNull()
  })

  it('maps RecommendedAction to the intervention API action', () => {
    expect(interventionAction('FIX_CREDENTIAL')).toBe('RETRY')
    expect(interventionAction('CHECK_NETWORK')).toBe('RETRY')
    expect(interventionAction('REVIEW_CODE')).toBe('RETRY')
    expect(interventionAction('RETRY_MANUALLY')).toBe('RETRY')
    expect(interventionAction('REPLAN')).toBe('REPLAN')
    expect(interventionAction('ABORT')).toBe('ABORT')
    expect(interventionAction(null)).toBeNull()
    expect(interventionAction('UNKNOWN')).toBeNull()
  })

  it('ABORT requires confirmation while RETRY and REPLAN do not', () => {
    expect(requiresConfirmation('ABORT')).toBe(true)
    expect(requiresConfirmation('RETRY')).toBe(false)
    expect(requiresConfirmation('REPLAN')).toBe(false)
    expect(requiresConfirmation(null)).toBe(false)
  })

  // ===== DELIVERY-LEGACY-FLOW-REMOVAL-V1 =====

  it('Pipeline present: primary action derives only from pipeline stage/status', () => {
    // 即使旧推导会给出 Run Validation，pipeline 存在时只按 pipeline 决定
    const running = { ...base(), changes: [change('APPROVED')], validation: null, delivery: pipeline({ currentStage: 'VALIDATING', status: 'RUNNING' }) }
    expect(primaryAction(running)).toBeNull()
    // pipeline WAITING_APPROVAL + QUALITY_GATE → Approve Quality Gate
    const gateWait = { ...base(), changes: [change('APPROVED')], delivery: pipeline({ currentStage: 'QUALITY_GATE', status: 'WAITING_APPROVAL' }) }
    expect(primaryAction(gateWait)).toEqual({ key: 'APPROVE_GATE', label: 'Approve Quality Gate' })
    // pipeline FAILED → Retry Delivery（不回落旧推导）
    const failed = { ...base(), changes: [change('APPROVED')], delivery: pipeline({ currentStage: 'FAILED', status: 'FAILED' }) }
    expect(primaryAction(failed)).toEqual({ key: 'RETRY_DELIVERY', label: 'Retry Delivery' })
  })

  it('Pipeline missing: no legacy promote/commit/validation actions', () => {
    // 旧推导会给建议的完整场景：change APPROVED + validation SUCCESS + workspace COMPLETED + push CONSUMED
    const input = {
      ...base(),
      changes: [change('APPROVED')],
      validation: validation('SUCCESS'),
      commits: [commit('SUCCESS')],
      remotePushApprovals: [pushApproval('CONSUMED')],
      workspaceStatus: 'COMPLETED',
      workspaceReviewComplete: true,
    }
    expect(primaryAction(input)).toBeNull()
    const summary = workflowSummary(input)
    expect(summary.status).toBe('NOT_STARTED')
    expect(summary.nextAction).toBe('Delivery pipeline unavailable / not started')
    expect(summary.blockedReason).toBe('Delivery pipeline not started.')
    expect(deliveryStages(input).stages).toEqual([])
  })

  it('Remote Push / CI stages never recommend Promote', () => {
    const pushWait = { ...base(), workspaceStatus: 'COMPLETED', workspaceReviewComplete: true, delivery: pipeline({ currentStage: 'WAITING_REMOTE_PUSH_APPROVAL', status: 'WAITING_APPROVAL' }) }
    expect(primaryAction(pushWait)).toEqual({ key: 'APPROVE_REMOTE_PUSH', label: 'Approve Remote Push' })
    const ciChecking = { ...base(), workspaceStatus: 'COMPLETED', workspaceReviewComplete: true, delivery: pipeline({ currentStage: 'CI_CHECKING', status: 'RUNNING' }) }
    expect(primaryAction(ciChecking)).toBeNull()
    const ciComplete = { ...base(), workspaceStatus: 'COMPLETED', workspaceReviewComplete: true, delivery: pipeline({ currentStage: 'DELIVERY_COMPLETE', status: 'COMPLETE', ciRunId: 'ci-1' }) }
    expect(primaryAction(ciComplete)).toBeNull()
    // 任何 pipeline 阶段都不应产生 PROMOTE_WORKSPACE
    for (const stage of ['CHANGE_READY', 'VALIDATING', 'QUALITY_GATE', 'COMMITTING', 'WAITING_REMOTE_PUSH_APPROVAL', 'PUSHING', 'CREATING_PR', 'CI_CHECKING']) {
      const at = { ...base(), workspaceStatus: 'COMPLETED', workspaceReviewComplete: true, delivery: pipeline({ currentStage: stage as never, status: 'RUNNING' }) }
      expect(primaryAction(at)).not.toEqual({ key: 'PROMOTE_WORKSPACE', label: 'Promote to Source Workspace' })
    }
  })
})

describe('execution delivery timeline', () => {
  it('no pipeline projects an empty stage chain (legacy derivation removed)', () => {
    const input = { ...base(), changes: [change('APPROVED')], validation: validation('SUCCESS'), commits: [commit('SUCCESS')], remotePushApprovals: [pushApproval('CONSUMED')] }
    const projection = deliveryStages(input)
    expect(projection.stages).toEqual([])
    expect(projection.current).toBe('EXECUTION')
  })

  it('empty delivery shows EXECUTION stage', () => {
    expect(deliveryStages(base()).current).toBe('EXECUTION')
  })
})

describe('delivery pipeline projection', () => {
  it('WAITING_APPROVAL at Quality Gate derives Approve Quality Gate', () => {
    const input = { ...base(), delivery: pipeline({ currentStage: 'QUALITY_GATE', status: 'WAITING_APPROVAL' }) }
    expect(primaryAction(input)).toEqual({ key: 'APPROVE_GATE', label: 'Approve Quality Gate' })
    const projection = deliveryStages(input)
    expect(projection.current).toBe('QUALITY_GATE')
    const gateStage = projection.stages.find(stage => stage.key === 'QUALITY_GATE')
    expect(gateStage?.status).toBe('WAITING_APPROVAL')
    const summary = workflowSummary(input)
    expect(summary.status).toBe('WAITING_APPROVAL')
  })

  it('WAITING_APPROVAL at Remote Push derives Approve Remote Push', () => {
    const input = { ...base(), delivery: pipeline({ currentStage: 'WAITING_REMOTE_PUSH_APPROVAL', status: 'WAITING_APPROVAL' }) }
    expect(primaryAction(input)).toEqual({ key: 'APPROVE_REMOTE_PUSH', label: 'Approve Remote Push' })
  })

  it('RUNNING at VALIDATING shows automatic advancement with no primary action', () => {
    const input = { ...base(), delivery: pipeline({ currentStage: 'VALIDATING', status: 'RUNNING' }) }
    expect(primaryAction(input)).toBeNull()
    const projection = deliveryStages(input)
    expect(projection.current).toBe('VALIDATION')
    const stages = Object.fromEntries(projection.stages.map(stage => [stage.key, stage.status]))
    expect(stages.CHANGE).toBe('SUCCESS')
    expect(stages.VALIDATION).toBe('ACTIVE')
    expect(stages.QUALITY_GATE).toBe('NOT_STARTED')
    expect(workflowSummary(input).nextAction).toBe('Auto-advancing')
  })

  it('COMPLETE marks the whole chain SUCCESS', () => {
    const input = { ...base(), delivery: pipeline({ currentStage: 'DELIVERY_COMPLETE', status: 'COMPLETE', ciRunId: 'ci-1' }) }
    expect(deliveryStages(input).stages.every(stage => stage.status === 'SUCCESS')).toBe(true)
    expect(workflowSummary(input).status).toBe('COMPLETE')
  })

  it('FAILED surfaces failure class, reason and Retry Delivery action', () => {
    const input = {
      ...base(),
      delivery: pipeline({ currentStage: 'FAILED', status: 'FAILED', ciRunId: 'ci-1', failureClass: 'HUMAN_REQUIRED', failureReason: 'CI FAILED: delivery cannot complete without CI success' }),
    }
    expect(primaryAction(input)).toEqual({ key: 'RETRY_DELIVERY', label: 'Retry Delivery' })
    const summary = workflowSummary(input)
    expect(summary.status).toBe('FAILED')
    expect(summary.failureClass).toBe('HUMAN_REQUIRED')
    expect(summary.blockedReason).toContain('CI FAILED')
    const projection = deliveryStages(input)
    expect(projection.stages.find(stage => stage.key === 'CI')?.status).toBe('FAILED')
  })
})
