import { describe, expect, it } from 'vitest'
import type { BacklogStatus } from '../types/backlog'
import { canBlockBacklog, canUnblockBacklog } from './backlogActions'

describe('Backlog action availability', () => {
  const expected: Record<BacklogStatus, { block: boolean; unblock: boolean }> = {
    IDEA: { block: false, unblock: false },
    PLANNED: { block: true, unblock: false },
    READY: { block: true, unblock: false },
    BLOCKED: { block: false, unblock: true },
    CONVERTED: { block: false, unblock: false },
    DONE: { block: false, unblock: false },
    CANCELLED: { block: false, unblock: false },
  }

  it.each(Object.entries(expected))('%s exposes only legal block actions', (status, actions) => {
    expect(canBlockBacklog(status as BacklogStatus)).toBe(actions.block)
    expect(canUnblockBacklog(status as BacklogStatus)).toBe(actions.unblock)
  })
})
