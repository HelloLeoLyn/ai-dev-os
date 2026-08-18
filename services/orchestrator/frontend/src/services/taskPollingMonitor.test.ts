import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../api/client'
import {
  TaskPollingMonitor,
  type MonitorStorage,
  type StoredTaskMonitor,
} from './taskPollingMonitor'
import type { TaskRecord, TaskStatus } from '../types/task'

function task(taskId: string, status: TaskStatus, errorMessage: string | null = null): TaskRecord {
  return {
    taskId, status, errorMessage, name: `Task ${taskId}`, description: null,
    createdAt: '2026-08-12T00:00:00Z', updatedAt: '2026-08-12T00:01:00Z',
    approvalId: 'approval-1', planRunId: 'run-1', workspaceId: 'workspace-1',
    projectId: 'project-1', executionMode: 'READ_ONLY',
  }
}

function harness(initialTasks: StoredTaskMonitor[] = [], terminalKeys: string[] = []) {
  let storedTasks = [...initialTasks]
  const terminals = new Set(terminalKeys)
  const storage: MonitorStorage = {
    loadTasks: () => [...storedTasks],
    saveTasks: (items) => { storedTasks = [...items] },
    hasTerminal: (key) => terminals.has(key),
    saveTerminal: (key) => { terminals.add(key) },
  }
  const fetchTask = vi.fn<(taskId: string) => Promise<TaskRecord>>()
  const onTerminal = vi.fn()
  const monitor = new TaskPollingMonitor({ fetchTask, storage, onTerminal, intervalMs: 4000 })
  return { monitor, fetchTask, onTerminal, terminals, storedTasks: () => storedTasks }
}

describe('TaskPollingMonitor', () => {
  it('calls browser timers with the global object as their receiver', () => {
    const originalSetTimeout = globalThis.setTimeout
    const originalClearTimeout = globalThis.clearTimeout
    const timer = 123 as unknown as ReturnType<typeof setTimeout>
    const setTimeoutSpy = vi.fn(function (this: typeof globalThis) {
      if (this !== globalThis) throw new TypeError('Illegal invocation')
      return timer
    })
    const clearTimeoutSpy = vi.fn(function (this: typeof globalThis) {
      if (this !== globalThis) throw new TypeError('Illegal invocation')
    })
    globalThis.setTimeout = setTimeoutSpy as unknown as typeof setTimeout
    globalThis.clearTimeout = clearTimeoutSpy as typeof clearTimeout

    try {
      const h = harness()
      expect(() => h.monitor.start()).not.toThrow()
      expect(() => h.monitor.track(task('running', 'RUNNING'))).not.toThrow()
      expect(setTimeoutSpy).toHaveBeenCalledOnce()
      expect(() => h.monitor.stop()).not.toThrow()
      expect(clearTimeoutSpy).toHaveBeenCalledWith(timer)
    } finally {
      globalThis.setTimeout = originalSetTimeout
      globalThis.clearTimeout = originalClearTimeout
    }
  })

  it('notifies once and stops polling for RUNNING -> SUCCESS', async () => {
    const h = harness()
    h.monitor.track(task('one', 'RUNNING'))
    h.fetchTask.mockResolvedValue(task('one', 'SUCCESS'))
    await h.monitor.pollNow('one')
    await h.monitor.pollNow('one')
    expect(h.onTerminal).toHaveBeenCalledTimes(1)
    expect(h.onTerminal).toHaveBeenCalledWith(expect.objectContaining({ status: 'SUCCESS' }), 'RUNNING')
    expect(h.monitor.monitoredTaskIds()).toEqual([])
  })

  it('notifies once for RUNNING -> FAILED and preserves the real error', async () => {
    const h = harness()
    h.monitor.track(task('failed', 'RUNNING'))
    h.fetchTask.mockResolvedValue(task('failed', 'FAILED', 'Codex workspace validation failed'))
    await h.monitor.pollNow('failed')
    expect(h.onTerminal).toHaveBeenCalledWith(
      expect.objectContaining({ errorMessage: 'Codex workspace validation failed' }), 'RUNNING',
    )
  })

  it('does not notify for RUNNING -> RUNNING', async () => {
    const h = harness()
    h.monitor.track(task('running', 'RUNNING'))
    h.fetchTask.mockResolvedValue(task('running', 'RUNNING'))
    await h.monitor.pollNow('running')
    expect(h.onTerminal).not.toHaveBeenCalled()
    expect(h.monitor.monitoredTaskIds()).toEqual(['running'])
  })

  it('does not repeat a persisted SUCCESS notification after refresh', async () => {
    const h = harness([], ['done:SUCCESS'])
    h.monitor.track(task('done', 'RUNNING'))
    h.fetchTask.mockResolvedValue(task('done', 'SUCCESS'))
    await h.monitor.pollNow('done')
    expect(h.onTerminal).not.toHaveBeenCalled()
    expect(h.monitor.monitoredTaskIds()).toEqual([])
  })

  it('monitors multiple tasks independently and leaves active tasks running', async () => {
    const h = harness()
    h.monitor.track(task('a', 'RUNNING'))
    h.monitor.track(task('b', 'RUNNING'))
    h.fetchTask.mockImplementation(async (id) => task(id, id === 'a' ? 'SUCCESS' : 'RUNNING'))
    await h.monitor.pollNow('a')
    await h.monitor.pollNow('b')
    expect(h.monitor.monitoredTaskIds()).toEqual(['b'])
    expect(h.onTerminal).toHaveBeenCalledTimes(1)
  })

  it('keeps polling independently of route state and survives transient errors', async () => {
    const h = harness()
    h.monitor.track(task('route-free', 'RUNNING'))
    h.fetchTask.mockRejectedValue(new Error('temporary network error'))
    await expect(h.monitor.pollNow('route-free')).resolves.toBeUndefined()
    expect(h.monitor.monitoredTaskIds()).toEqual(['route-free'])
    expect(h.onTerminal).not.toHaveBeenCalled()
  })

  it('stops polling and persists removal when a task no longer exists', async () => {
    const h = harness()
    h.monitor.track(task('missing', 'RUNNING'))
    h.fetchTask.mockRejectedValue(new ApiError(404, 'Task not found'))

    await h.monitor.pollNow('missing')

    expect(h.monitor.monitoredTaskIds()).toEqual([])
    expect(h.storedTasks()).toEqual([])
    await h.monitor.pollNow('missing')
    expect(h.fetchTask).toHaveBeenCalledOnce()
  })

  it('stops only the missing task while another task keeps polling', async () => {
    const h = harness()
    h.monitor.track(task('missing', 'RUNNING'))
    h.monitor.track(task('active', 'RUNNING'))
    h.fetchTask.mockImplementation(async (id) => {
      if (id === 'missing') throw new ApiError(404, 'Task not found')
      return task(id, 'RUNNING')
    })

    await h.monitor.pollNow('missing')
    await h.monitor.pollNow('active')

    expect(h.monitor.monitoredTaskIds()).toEqual(['active'])
    expect(h.fetchTask).toHaveBeenNthCalledWith(1, 'missing')
    expect(h.fetchTask).toHaveBeenNthCalledWith(2, 'active')
  })

  it('handles REJECTED as a distinct terminal state', async () => {
    const h = harness()
    h.monitor.track(task('rejected', 'REJECTED'))
    await vi.waitFor(() => expect(h.onTerminal).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'REJECTED' }), 'REJECTED',
    ))
  })
})
