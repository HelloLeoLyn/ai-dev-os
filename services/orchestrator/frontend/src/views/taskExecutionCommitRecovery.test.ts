import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./TaskExecutionView.vue', import.meta.url), 'utf8')

describe('commit state recovery UX', () => {
  it('A: PENDING commits show Recover Commit State only', () => {
    expect(source).toContain("v-if=\"commit.status === 'PENDING' || (commit.status === 'SUCCESS' && !hasUsableApproval(commit.commitId))\"")
    expect(source).toContain("@click=\"recoverCommitState(commit)\"")
    expect(source).toContain("Recover Commit State")
    expect(source).toContain("function hasUsableApproval(commitId: string): boolean")
    expect(source).toContain("item.status === 'PENDING' || item.status === 'APPROVED'")
  })

  it('B: recover calls the backend endpoint and refreshes state + timeline', () => {
    expect(source).toContain("await recoverCommit(taskId, commit.commitId)")
    expect(source).toContain("await loadExecutionState()\n    await taskTimeline.load(taskId)")
  })

  it('C: recover is manual, never automatic on mount', () => {
    const calls = (source.match(/recoverCommitState\(/g) || []).length
    expect(calls).toBe(2)
  })
})
