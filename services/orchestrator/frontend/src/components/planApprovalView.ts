import type { PlanApprovalRequest, PlanStep } from '../types/planApproval'

export function planApprovalRisk(approval: PlanApprovalRequest) {
  const assigned = new Set(approval.plan.steps.map((step) => step.assignment.agentName)
    .filter((name): name is string => Boolean(name)))
  return {
    readOnly: approval.plan.snapshot.plannerMetadata.executionMode === 'READ_ONLY',
    hasWriteAgent: approval.plan.snapshot.agents.some((agent) => assigned.has(agent.name) &&
      (agent.permissionLevel === 'workspace-write' || agent.capabilities.some((item) =>
        ['coding', 'git', 'write'].includes(item.toLowerCase())))),
    hasWriteTool: approval.plan.steps.some((step) => Boolean(step.toolName) &&
      approval.plan.snapshot.tools.some((tool) => tool.providerId === step.toolProviderId &&
        tool.name === step.toolName && tool.access === 'WORKSPACE_WRITE')),
  }
}

export function canDecide(approval: PlanApprovalRequest, busy: boolean): boolean {
  return approval.status === 'PENDING' && !busy
}

export function toolLabel(step: PlanStep): string {
  return step.toolName ? `${step.toolProviderId}/${step.toolName}` : '无'
}

export function validRejectReason(reason: string): boolean {
  return reason.trim().length > 0
}
