import { describe, expect, it } from 'vitest'
import { baseDashboardModel, executionResultSummary } from './dashboardConsole'
import type { ExecutionRecordDetail } from '../types/execution'
import type { Project } from '../types/project'
import type { TaskRecord, TaskStatus } from '../types/task'

function task(id: string, status: TaskStatus, errorMessage: string | null = null): TaskRecord {
  return { taskId: id, name: id, description: null, status, errorMessage,
    createdAt: `2026-01-0${id.length}T00:00:00Z`, updatedAt: `2026-01-0${id.length}T01:00:00Z`,
    approvalId: status === 'PLANNING' ? `approval-${id}` : null, planRunId: null,
    workspaceId: 'workspace', projectId: 'project', executionMode: 'READ_ONLY' }
}

const project = { projectId: 'project', status: 'ACTIVE' } as Project

describe('Dashboard console model', () => {
  it('derives running, pending, failed and success modules', () => {
    const model = baseDashboardModel([
      task('run', 'RUNNING'), task('approval', 'PLANNING'), task('failed', 'FAILED', 'Executor codex failed'),
      task('success', 'SUCCESS'),
    ], [project])
    expect(model.running.map((item) => item.taskId)).toEqual(['run'])
    expect(model.pending.map((item) => item.task.taskId)).toEqual(['approval'])
    expect(model.failures[0].task.errorMessage).toBe('Executor codex failed')
    expect(model.successes.map((item) => item.task.taskId)).toEqual(['success'])
    expect(model.activeProjects).toHaveLength(1)
    expect(model.counts).toEqual({ running: 1, pending: 1, failed: 1, successful: 1, activeProjects: 1 })
  })

  it('returns normal empty states when no tasks exist', () => {
    const model = baseDashboardModel([], [])
    expect([model.running, model.pending, model.failures, model.successes, model.activity])
      .toEqual([[], [], [], [], []])
  })

  it('uses the real execution message instead of a generic error', () => {
    const record = { message: 'Executor codex failed: outside allowed roots', output: 'fallback' } as ExecutionRecordDetail
    expect(executionResultSummary(record)).toBe('Executor codex failed: outside allowed roots')
  })
})
