import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const component = readFileSync(fileURLToPath(new URL('./AnalysisInsights.vue', import.meta.url)), 'utf8')
const summary = readFileSync(fileURLToPath(new URL('./AnalysisSummary.vue', import.meta.url)), 'utf8')
const finding = readFileSync(fileURLToPath(new URL('./FindingCard.vue', import.meta.url)), 'utf8')
const recommendation = readFileSync(fileURLToPath(new URL('./RecommendationCard.vue', import.meta.url)), 'utf8')
const evidence = readFileSync(fileURLToPath(new URL('./EvidenceList.vue', import.meta.url)), 'utf8')
const composable = readFileSync(fileURLToPath(new URL('../composables/useAnalysisInsights.ts', import.meta.url)), 'utf8')

describe('Analysis Insights component contract', () => {
  it('renders findings, recommendations, evidence and next actions', () => {
    const ux = [component, summary, finding, recommendation, evidence].join('\n')
    for (const value of ['Findings', 'Recommendations', 'Confidence', 'Evidence', 'Priority', 'Risk', 'Benefit', 'Scope', 'Dependencies', 'Recommended Next Action', 'Goal:', 'Acceptance Criteria']) expect(ux).toContain(value)
  })
  it('renders every decision state through authoritative status and exposes controlled actions', () => {
    for (const value of ['item.status', 'View', 'Defer', 'Ignore', 'Create WorkItem', 'WORKITEM_CREATED', 'Open in Backlog']) expect(recommendation).toContain(value)
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
    expect(recommendation).toContain('READ_WRITE · Approval Required')
    expect(recommendation).toContain('grants no execution authority')
    expect(recommendation).not.toContain('Codex Recommendation')
    expect([component, recommendation].join('\n')).not.toContain('Accept and Execute')
  })
  it('uses receiver-safe browser timers and clears them across refreshes', () => {
    expect(composable).toContain('globalThis.setTimeout')
    expect(composable).toContain('globalThis.clearTimeout')
    expect(composable).toContain('generation')
  })

  it('uses progressive disclosure for Finding, Recommendation and Evidence details', () => {
    expect(finding).toContain('<details class="finding-details">')
    expect(recommendation).toContain('<details class="recommendation-details">')
    expect(evidence).toContain('<details v-if="evidence.length"')
    expect(recommendation.indexOf('Recommended Next Action')).toBeLessThan(recommendation.indexOf('recommendation-details'))
    expect(recommendation.indexOf('Acceptance Criteria')).toBeGreaterThan(recommendation.indexOf('recommendation-details'))
  })

  it('states that WorkItem creation stops at Backlog IDEA', () => {
    expect(component).toContain('Recommendation → Backlog IDEA')
    expect(component).toContain('does not create or execute a Task')
    expect(component).not.toContain('Convert Now')
  })
})
