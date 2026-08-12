import { describe, expect, it } from 'vitest'
import { taskExecutionPath, taskTimelinePath } from '../services/taskNotificationNavigation'

describe('task notification navigation', () => {
  it('builds encoded Execution and Timeline destinations', () => {
    expect(taskExecutionPath('task/one')).toBe('/tasks/task%2Fone/execution')
    expect(taskTimelinePath('task/one')).toBe('/timeline?id=task%2Fone')
  })
})
