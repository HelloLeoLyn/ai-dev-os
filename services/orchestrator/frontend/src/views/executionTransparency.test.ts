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
    expect(source).toContain("!['COMPLETED', 'PROMOTION_FAILED'].includes(executionWorkspace.value?.status || '')")
    expect(source).toContain("v-if=\"['COMPLETED','PROMOTION_FAILED'].includes(executionWorkspace?.status || '')\"")
    expect(source).toContain("workspaceReview.value?.completeness !== 'COMPLETE'")
    expect(source).toContain(':disabled="workspaceReview.completeness !== \'COMPLETE\'"')
    expect(source).toContain('Review is incomplete:')
    expect(source).toContain("'Unable to render untracked file diff.'")
  })

  it('renders compact model/provider/executor evidence and structured errors', () => {
    expect(source).toContain("record.resolvedModelId")
    expect(source).toContain("record.modelProvider")
    expect(source).toContain("record.modelExecutor")
    expect(source).toContain("record.requestedModelId")
    expect(source).toContain('record.errorCode')
    expect(source).toContain('record.errorMessage')
    expect(source).toContain('<dt>Error Code</dt>')
  })

  it('does not fall back to Actual Executor = Unknown when executor is known', () => {
    expect(source).toContain("record.executorName || 'Unknown'")
    expect(source).not.toContain('Actual Executor = Unknown')
  })

  it('refreshes authoritative workspace state after promotion failure and exposes reject only for review states', () => {
    expect(source).toContain("['COMPLETED','PROMOTION_FAILED'].includes(executionWorkspace?.status || '')")
    expect(source).toContain('getExecutionWorkspace(taskId).then(value => { executionWorkspace.value = value })')
    expect(source).toContain('getExecutionWorkspaceReview(taskId).then(value => { workspaceReview.value = value })')
    expect(source).not.toContain("executionWorkspace?.status === 'PROMOTION_FAILED' && promoteWorkspace")
    expect(source).toContain("executionWorkspace?.status === 'PROMOTION_FAILED' ? 'Retry Promote to Source Workspace'")
  })

  it('does not offer promote or reject after terminal promotion outcomes', () => {
    expect(source).toContain("v-if=\"['COMPLETED','PROMOTION_FAILED'].includes(executionWorkspace?.status || '')\"")
  })
})
