import { describe, expect, it } from 'vitest'
import { duplicateTaskDraft } from './taskDuplicate'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'

const source: TaskRecord = {
  taskId: 'old-id', name: 'Analyze JJX', description: 'Read only analysis', status: 'SUCCESS',
  createdAt: 'old-created', updatedAt: 'old-updated', approvalId: 'old-approval',
  planRunId: 'old-run', workspaceId: 'workspace-jjx', projectId: 'project-jjx',
  executionMode: 'READ_ONLY', errorMessage: 'old-error',
}

const approval = {
  plan: { goal: 'Inspect the repository safely' },
} as PlanApprovalRequest

describe('duplicateTaskDraft', () => {
  it('prefills only create fields including project and workspace', () => {
    const duplicate = duplicateTaskDraft(source, approval, { plannerName: 'fake' })
    expect(duplicate).toEqual({
      plannerWasRecovered: true,
      request: {
        name: 'Analyze JJX - 副本', description: 'Read only analysis',
        goal: 'Inspect the repository safely', plannerName: 'fake',
        projectId: 'project-jjx', workspaceId: 'workspace-jjx', executionMode: 'READ_ONLY',
      },
    })
    expect(duplicate.request).not.toHaveProperty('taskId')
    expect(duplicate.request).not.toHaveProperty('status')
    expect(duplicate.request).not.toHaveProperty('approvalId')
    expect(duplicate.request).not.toHaveProperty('planRunId')
    expect(duplicate.request).not.toHaveProperty('errorMessage')
  })

  it('falls back to hermes when historical planner metadata is unavailable', () => {
    const duplicate = duplicateTaskDraft(source, approval, null)
    expect(duplicate.request.plannerName).toBe('hermes')
    expect(duplicate.plannerWasRecovered).toBe(false)
  })
})
