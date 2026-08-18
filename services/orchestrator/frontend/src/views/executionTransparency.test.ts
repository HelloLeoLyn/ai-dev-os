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
    expect(source).toContain('Historical Attempt')
    expect(source).toContain('Latest Attempt')
    expect(source).toContain('Resolved Executor')
    expect(source).toContain('Last Flow Event')
  })

  it('uses task polling updates and does not approve during render', () => {
    expect(source).toContain('watch(monitoredTask')
    expect(source).toContain('@click="decideCodingApproval(\'approve\')"')
    expect(source).not.toContain('onMounted(() => approveCodingApproval')
  })

  it('requires complete review before allowing promotion and renders new-file diffs', () => {
    expect(source).toContain("workspaceReview.value?.completeness !== 'COMPLETE'")
    expect(source).toContain(':disabled="workspaceReview.completeness !== \'COMPLETE\'"')
    expect(source).toContain('Review is incomplete:')
    expect(source).toContain("'Unable to render untracked file diff.'")
  })
})
