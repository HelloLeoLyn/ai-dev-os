import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./TaskExecutionView.vue', import.meta.url), 'utf8')

describe('execution efficiency UX', () => {
  it('shows execution type and validation profile', () => {
    expect(source).toContain('<span>Execution Type</span>')
    expect(source).toContain('<span>Validation Profile</span>')
    expect(source).toContain('executionTypeLabel')
    expect(source).toContain('efficiency.profile')
  })

  it('shows AI and tool call counts and time buckets', () => {
    expect(source).toContain('<span>AI Calls</span>')
    expect(source).toContain('<span>Tool Calls</span>')
    expect(source).toContain('<span>AI Time</span>')
    expect(source).toContain('<span>Tool Time</span>')
    expect(source).toContain('<span>Waiting Time</span>')
    expect(source).toContain('formatMs(efficiency.aiMs)')
    expect(source).toContain('formatMs(efficiency.waitingMs)')
  })

  it('classifies records as AI or tool deterministically', () => {
    expect(source).toContain("name === 'deterministic'")
    expect(source).toContain("record.executionType === 'TOOL_STEP'")
    expect(source).toContain('Boolean(record.resolvedModelId || record.modelExecutor)')
  })
})
