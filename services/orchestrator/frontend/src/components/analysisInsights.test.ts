import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const component = readFileSync(fileURLToPath(new URL('./AnalysisInsights.vue', import.meta.url)), 'utf8')
const composable = readFileSync(fileURLToPath(new URL('../composables/useAnalysisInsights.ts', import.meta.url)), 'utf8')

describe('Analysis Insights component contract', () => {
  it('renders findings, recommendations, evidence and next actions', () => {
    for (const value of ['Findings', 'Recommendations', 'Category', 'Confidence', 'Evidence', 'Priority', 'Risk', 'Benefit', 'Scope', 'Dependencies', 'Recommended Next Action', 'Goal:', 'Acceptance Criteria', 'Estimated Complexity']) expect(component).toContain(value)
  })
  it('renders every decision state through authoritative status and exposes controlled actions', () => {
    for (const value of ['item.status', 'View', 'Defer', 'Ignore', 'Create WorkItem', 'WORKITEM_CREATED', 'Open in Backlog']) expect(component).toContain(value)
    expect(composable).toContain('getRecommendation(id)')
    expect(component).toContain('result.created')
    expect(component).toContain('deferForm.deferUntil')
  })
  it('separates Task success from projection failure and supports retry', () => {
    expect(component).toContain('source Task itself completed successfully')
    expect(component).toContain('Analysis extraction / projection failed')
    expect(component).toContain('Retry Analysis Projection')
    expect(component).toContain('Analysis Projection is processing')
  })
  it('shows READ_WRITE approval warning without execution actions or agent hard-coding', () => {
    expect(component).toContain('需要审批 / Approval Required')
    expect(component).toContain('grants no execution authority')
    expect(component).not.toContain('Codex Recommendation')
    expect(component).not.toContain('Accept and Execute')
  })
  it('uses receiver-safe browser timers and clears them across refreshes', () => {
    expect(composable).toContain('globalThis.setTimeout')
    expect(composable).toContain('globalThis.clearTimeout')
    expect(composable).toContain('generation')
  })
})
