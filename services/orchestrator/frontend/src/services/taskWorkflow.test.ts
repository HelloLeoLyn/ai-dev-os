import { describe, expect, it } from 'vitest'
import { projectTaskWorkflow } from './taskWorkflow'
import type { TaskRecord } from '../types/task'

const task = (status: TaskRecord['status']): TaskRecord => ({ taskId: 'task-1', name: 'Analysis task', description: null, status, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', approvalId: 'approval-1', planRunId: 'run-1', workspaceId: 'ws-1', projectId: 'project-1', executionMode: 'READ_WRITE', errorMessage: null })

describe('task workflow UI projection', () => {
  it('keeps source Task success separate from Analysis projection failure', () => {
    const projection = projectTaskWorkflow(task('SUCCESS'), 'CONSUMED', 'FAILED')
    expect(projection.current).toBe('ANALYSIS')
    expect(projection.label).toBe('Analysis projection failed')
    expect(task('SUCCESS').status).toBe('SUCCESS')
  })

  it('projects pending approval without changing READ_WRITE authorization', () => {
    const pending = { ...task('PLANNING'), planRunId: null }
    expect(projectTaskWorkflow(pending, 'PENDING')).toEqual(expect.objectContaining({ current: 'APPROVAL', nextAction: 'Review Plan approval' }))
    expect(pending.executionMode).toBe('READ_WRITE')
  })
})
