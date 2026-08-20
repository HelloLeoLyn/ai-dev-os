import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./TaskExecutionView.vue', import.meta.url), 'utf8')

describe('commit action refreshes unified timeline', () => {
  it('A: commit action reloads execution state and then the timeline', () => {
    expect(source).toContain("else await commitChange(id)")
    expect(source).toContain("await loadExecutionState()\n    await taskTimeline.load(taskId)")
  })

  it('B: LAST FLOW EVENT still derives from the last timeline event', () => {
    expect(source).toContain("lastFlowEvent = computed(() => taskTimeline.timeline.value?.events.at(-1) ?? null)")
  })

  it('C: remote push action list still drives the Approve Remote Push UI', () => {
    expect(source).toContain("pendingRemotePush = computed(() => remotePushApprovals.value.find(item => item.status === 'PENDING') ?? null)")
    expect(source).toContain("remotePushApprovals.value = await getRemotePushApprovals(taskId).catch(() => [])")
  })

  it('D: remote push approve refreshes execution state and timeline', () => {
    expect(source).toContain("await pushRemote(approved.commitId, approved.remote, approved.approvalId)")
    expect(source).toContain("await loadExecutionState()\n    await taskTimeline.load(taskId)")
  })
})
