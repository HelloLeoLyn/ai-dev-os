import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./TaskExecutionView.vue', import.meta.url), 'utf8')

describe('execution transparency UX', () => {
  it('distinguishes plan and coding approval with explicit actions', () => {
    expect(source).toContain('Plan Approval:')
    expect(source).toContain('Coding Approval:')
    expect(source).toContain('Approve Workspace Write')
    expect(source).toContain("decideCodingApproval('reject')")
  })

  it('shows authoritative correlation and actual executor without hiding history', () => {
    expect(source).toContain('record.executorName || \'Unknown\'')
    expect(source).toContain('record.stepRunId')
    expect(source).toContain('v-for="(record, index) in executions.records.value"')
    expect(source).not.toContain('<dt>Executor</dt><dd>—</dd>')
  })

  it('uses task polling updates and does not approve during render', () => {
    expect(source).toContain('watch(monitoredTask')
    expect(source).toContain('@click="decideCodingApproval(\'approve\')"')
    expect(source).not.toContain('onMounted(() => approveCodingApproval')
  })
})
