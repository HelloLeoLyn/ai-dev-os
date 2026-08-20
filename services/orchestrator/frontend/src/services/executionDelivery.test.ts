import { describe, expect, it } from 'vitest'
import { deliveryStages, primaryAction, workflowSummary, type ExecutionViewInput } from './executionDelivery'
import type { ChangeSet } from '../api/changes'
import type { CommitRecord } from '../api/commits'
import type { ExecutionState } from '../api/planRuns'
import type { RemotePushApproval } from '../api/remotePush'
import type { QualityGateResult, ValidationRun } from '../types/validation'

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

function gate(decision: QualityGateResult['decision']): QualityGateResult {
  return { gateResultId: 'g1', validationRunId: 'v1', taskId: 't1', projectId: 'p1', workspaceId: 'w1', decision, status: 'DECIDED', policyVersion: 'v1', evidenceFingerprint: 'f', reasons: [{ code: 'GATE', severity: 'HIGH', message: 'Gate message', sourceType: 'validation', sourceId: 'v1', blocking: true }], securitySummary: {}, validationSummary: {}, createdAt: '2026-01-01T00:00:00Z', decidedAt: '2026-01-01T00:00:00Z' }
}

function executionState(interventionStatus: string, overrides: Partial<ExecutionState> = {}): ExecutionState {
  return { runId: 'r1', totalAttempts: 1, aiAttempts: 0, toolAttempts: 1, repairAttempts: 0, replanAttempts: 0, consecutiveFailures: 3, interventionStatus, lastFailureClass: 'CREDENTIAL_MISSING', lastSeverity: 'L3_HUMAN_REQUIRED', lastResponse: 'REQUEST_HUMAN', lastAttempt: 1, lastMaxAttempts: 2, lastReason: 'DeepSeek credential is not configured.', recommendedAction: 'FIX_CREDENTIAL', ...overrides }
}

describe('execution delivery primary action', () => {
  it('WAITING workspace approval derives Approve Workspace Write', () => {
    const input = { ...base(), codingApprovalPending: true }
    expect(primaryAction(input)).toEqual({ key: 'APPROVE_WORKSPACE_WRITE', label: 'Approve Workspace Write' })
  })

  it('Change APPROVED with validation NOT_RUN derives Run Validation', () => {
    const input = { ...base(), changes: [change('APPROVED')] }
    expect(primaryAction(input)).toEqual({ key: 'RUN_VALIDATION', label: 'Run Validation' })
  })

  it('Quality Gate REQUIRE_APPROVAL derives Approve Quality Gate', () => {
    const input = { ...base(), changes: [change('APPROVED')], validation: validation('SUCCESS'), gate: gate('REQUIRE_APPROVAL') }
    expect(primaryAction(input)).toEqual({ key: 'APPROVE_GATE', label: 'Approve Quality Gate' })
  })

  it('Commit SUCCESS with Remote Approval PENDING derives Approve Remote Push', () => {
    const input = { ...base(), changes: [change('COMMITTED')], commits: [commit('SUCCESS')], remotePushApprovals: [pushApproval('PENDING')] }
    expect(primaryAction(input)).toEqual({ key: 'APPROVE_REMOTE_PUSH', label: 'Approve Remote Push' })
  })

  it('NEEDS_INTERVENTION surfaces failure and recommended action', () => {
    const input = { ...base(), executionState: executionState('HUMAN_INTERVENTION_REQUIRED') }
    const summary = workflowSummary(input)
    expect(summary.status).toBe('NEEDS_INTERVENTION')
    expect(summary.failureClass).toBe('CREDENTIAL_MISSING')
    expect(summary.errorMessage).toBe('DeepSeek credential is not configured.')
    expect(summary.recommendedAction).toBe('FIX_CREDENTIAL')
    expect(summary.nextAction).toBe('Fix credential → Retry')
    expect(primaryAction(input)).toBeNull()
  })
})

describe('execution delivery timeline', () => {
  it('walks Change → Validation → Quality Gate → Commit → Remote Push', () => {
    const input = { ...base(), changes: [change('APPROVED')], validation: validation('SUCCESS'), gate: gate('REQUIRE_APPROVAL') }
    const projection = deliveryStages(input)
    expect(projection.stages.map(stage => [stage.key, stage.status])).toEqual([
      ['CHANGE', 'SUCCESS'],
      ['VALIDATION', 'SUCCESS'],
      ['QUALITY_GATE', 'WAITING_APPROVAL'],
      ['COMMIT', 'NOT_STARTED'],
      ['REMOTE_PUSH', 'NOT_STARTED'],
    ])
    expect(projection.current).toBe('QUALITY_GATE')
  })

  it('empty delivery shows EXECUTION stage', () => {
    expect(deliveryStages(base()).current).toBe('EXECUTION')
  })
})
