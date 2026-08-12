import { describe, expect, it } from 'vitest'
import type { PlanApprovalRequest } from '../types/planApproval'
import { canDecide, planApprovalRisk, toolLabel, validRejectReason } from './planApprovalView'

const approval = {
  status: 'PENDING',
  plan: {
    steps: [{ id: 'step-1', name: 'Analyze', description: '',
      assignment: { agentName: 'planner', requiredCapabilities: ['analysis'], fallbackAgentNames: [] },
      toolProviderId: null, toolName: null, failurePolicy: 'STOP_PLAN', expectedArtifacts: [] }],
    snapshot: { plannerMetadata: { executionMode: 'READ_ONLY' },
      agents: [{ name: 'planner', executor: 'mock', capabilities: ['analysis'], permissionLevel: null, enabled: true }],
      tools: [], executors: ['mock'], policyVersion: 'v1' },
  },
} as unknown as PlanApprovalRequest

describe('PlanApprovalDetail view rules', () => {
  it('marks the approved plan as read-only without assigned write risks', () => {
    expect(planApprovalRisk(approval)).toEqual({
      readOnly: true,
      hasWriteAgent: false,
      hasWriteTool: false,
      hasDangerousTool: false,
      hasWorkspaceWritePermission: false,
    })
  })

  it('shows no tool for an agent-only step', () => {
    expect(toolLabel(approval.plan.steps[0])).toBe('无')
  })

  it('only enables decisions for a non-busy pending approval', () => {
    expect(canDecide(approval, false)).toBe(true)
    expect(canDecide(approval, true)).toBe(false)
    expect(canDecide({ ...approval, status: 'APPROVED' }, false)).toBe(false)
  })

  it('requires a non-blank reject reason', () => {
    expect(validRejectReason('   ')).toBe(false)
    expect(validRejectReason('unsafe write step')).toBe(true)
  })
})
