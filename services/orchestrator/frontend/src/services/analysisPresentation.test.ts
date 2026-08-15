import { describe, expect, it } from 'vitest'
import { levelSummary, sortFindings, sortRecommendations } from './analysisPresentation'
import type { Finding, Recommendation } from '../types/analysis'

const evidence = { type: 'SOURCE_FILE' as const, ref: 'src/App.vue', label: null, artifactType: null, uri: null, line: null, contentHash: null }
const finding = (id: string, severity: Finding['severity']): Finding => ({ findingId: id, title: id, summary: id, category: 'CODE', severity, confidence: .8, scope: [], evidenceRefs: [evidence] })
const recommendation = (id: string, priority: Recommendation['priority']): Recommendation => ({ recommendationId: id, findingIds: [], title: id, rationale: id, priority, risk: 'MEDIUM', benefit: 'HIGH', scope: [], dependencies: [], suggestedExecutionMode: 'READ_ONLY', approvalRequired: false, evidenceRefs: [], confidence: .9, recommendedNextAction: { actionId: `action-${id}`, title: id, description: id, goal: id, acceptanceCriteria: [], scope: [], dependencies: [], suggestedExecutionMode: 'READ_ONLY', approvalRequired: false, estimatedComplexity: 'SMALL' } })

describe('Analysis progressive presentation', () => {
  it('summarizes Finding severity and Recommendation priority', () => {
    expect(levelSummary([finding('critical', 'CRITICAL'), finding('high', 'HIGH'), finding('high-2', 'HIGH')], 'severity')).toEqual({ CRITICAL: 1, HIGH: 2, MEDIUM: 0, LOW: 0 })
    expect(levelSummary([recommendation('low', 'LOW'), recommendation('critical', 'CRITICAL')], 'priority')).toEqual({ CRITICAL: 1, HIGH: 0, MEDIUM: 0, LOW: 1 })
  })

  it('derives severity and priority sorting without mutating backend arrays', () => {
    const findings = [finding('low', 'LOW'), finding('critical', 'CRITICAL')]
    const recommendations = [recommendation('medium', 'MEDIUM'), recommendation('high', 'HIGH')]
    expect(sortFindings(findings).map(item => item.findingId)).toEqual(['critical', 'low'])
    expect(sortRecommendations(recommendations).map(item => item.recommendationId)).toEqual(['high', 'medium'])
    expect(findings[0].findingId).toBe('low')
    expect(recommendations[0].recommendationId).toBe('medium')
  })
})
