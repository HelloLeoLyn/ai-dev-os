import type { PlanApprovalRequest, PlanStep } from '../types/planApproval'

export function planApprovalRisk(approval: PlanApprovalRequest) {
  const assigned = new Set(approval.plan.steps.map((step) => step.assignment.agentName)
    .filter((name): name is string => Boolean(name)))
  const assignedAgents = approval.plan.snapshot.agents.filter((agent) => assigned.has(agent.name))
  const assignedTools = approval.plan.steps.flatMap((step) => approval.plan.snapshot.tools.filter((tool) =>
    tool.providerId === step.toolProviderId && tool.name === step.toolName))
  return {
    readOnly: approval.plan.snapshot.plannerMetadata.executionMode === 'READ_ONLY',
    hasWriteAgent: assignedAgents.some((agent) =>
      (agent.permissionLevel === 'workspace-write' || agent.capabilities.some((item) =>
        ['coding', 'git', 'write'].includes(item.toLowerCase())))),
    hasWriteTool: assignedTools.some((tool) => tool.access === 'WORKSPACE_WRITE'),
    hasDangerousTool: assignedTools.some((tool) => String(tool.access).toUpperCase().includes('DANGEROUS')),
    hasWorkspaceWritePermission: assignedAgents.some((agent) => agent.permissionLevel === 'workspace-write'),
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

export function isLongPlanGoal(goal: string): boolean {
  return goal.length > 320
}
