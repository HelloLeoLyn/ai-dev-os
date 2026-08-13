import { describe, expect, it } from 'vitest'
import { statusTone } from './status'

describe('StatusBadge semantics', () => {
  it.each([
    ['SUCCESS', 'success'], ['FAILED', 'danger'], ['RUNNING', 'info'], ['PENDING', 'warning'],
    ['PLANNING', 'warning'], ['REJECTED', 'danger'], ['CONSUMED', 'success'],
    ['READ_ONLY', 'safe'], ['READ_WRITE', 'write'],
  ])('maps %s to %s', (status, tone) => expect(statusTone(status)).toBe(tone))
})
