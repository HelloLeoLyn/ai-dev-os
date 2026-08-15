import type { AnalysisProjectionStatus } from '../types/analysis'
import type { PlanApprovalStatus } from '../types/planApproval'
import type { TaskRecord } from '../types/task'

export type WorkflowStage = 'TASK' | 'PLAN' | 'APPROVAL' | 'EXECUTION' | 'ANALYSIS' | 'RECOMMENDATION' | 'BACKLOG'

export interface WorkflowProjection {
  current: WorkflowStage
  label: string
  nextAction: string
}

export const workflowStages: Array<{ key: WorkflowStage; label: string }> = [
  { key: 'TASK', label: 'Task' },
  { key: 'PLAN', label: 'Plan' },
  { key: 'APPROVAL', label: 'Approval' },
  { key: 'EXECUTION', label: 'Execution' },
  { key: 'ANALYSIS', label: 'Analysis' },
  { key: 'RECOMMENDATION', label: 'Recommendation' },
  { key: 'BACKLOG', label: 'Backlog' },
]

export function projectTaskWorkflow(
  task: TaskRecord,
  approvalStatus?: PlanApprovalStatus | null,
  analysisStatus?: AnalysisProjectionStatus | null,
  recommendationCount = 0,
  workItemCount = 0,
	codingApprovalRequired = false,
): WorkflowProjection {
	if (codingApprovalRequired) return { current: 'EXECUTION', label: 'Workspace write approval required', nextAction: 'Approve Coding Workspace Write' }
  if (workItemCount > 0) return { current: 'BACKLOG', label: 'Backlog created', nextAction: 'Open the created Backlog item' }
  if (recommendationCount > 0) return { current: 'RECOMMENDATION', label: 'Recommendations available', nextAction: 'Review recommendations' }
  if (analysisStatus) {
    if (analysisStatus === 'FAILED') return { current: 'ANALYSIS', label: 'Analysis projection failed', nextAction: 'Review or retry Analysis projection' }
    if (analysisStatus === 'SUCCEEDED') return { current: 'ANALYSIS', label: 'Analysis complete', nextAction: 'Review Analysis' }
    return { current: 'ANALYSIS', label: `Analysis ${analysisStatus.toLowerCase()}`, nextAction: 'Wait for Analysis projection' }
  }
  if (['SUCCESS', 'COMPLETED', 'FAILED'].includes(task.status)) {
    return { current: 'EXECUTION', label: task.status === 'FAILED' ? 'Execution failed' : 'Execution complete', nextAction: 'Review execution result' }
  }
  if (task.planRunId || ['APPROVED', 'RUNNING', 'CODING', 'TESTING'].includes(task.status)) {
    return { current: 'EXECUTION', label: 'Execution in progress', nextAction: 'Monitor execution' }
  }
  if (approvalStatus === 'PENDING') return { current: 'APPROVAL', label: 'Awaiting approval', nextAction: 'Review Plan approval' }
  if (approvalStatus === 'REJECTED') return { current: 'APPROVAL', label: 'Plan rejected', nextAction: 'Review rejection details' }
  if (approvalStatus) return { current: 'APPROVAL', label: `Approval ${approvalStatus.toLowerCase()}`, nextAction: 'Review Plan' }
  if (task.approvalId || task.status === 'PLANNING') return { current: 'PLAN', label: task.status === 'PLANNING' ? 'Planning' : 'Plan available', nextAction: 'Review Plan' }
  return { current: 'TASK', label: 'Task created', nextAction: 'Wait for planning' }
}
