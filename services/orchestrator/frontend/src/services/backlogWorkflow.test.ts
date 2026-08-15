import { describe, expect, it } from 'vitest'
import { backlogNextAction } from './backlogWorkflow'

describe('Backlog workflow projection', () => {
  it('derives navigation-only next actions from real Backlog status', () => {
    expect(backlogNextAction({ status: 'IDEA', convertedTaskId: null })).toBe('Move to PLANNED')
    expect(backlogNextAction({ status: 'PLANNED', convertedTaskId: null })).toBe('Mark READY')
    expect(backlogNextAction({ status: 'READY', convertedTaskId: null })).toBe('Convert to Task')
    expect(backlogNextAction({ status: 'CONVERTED', convertedTaskId: 'task-1' })).toBe('Open Task')
  })
})
