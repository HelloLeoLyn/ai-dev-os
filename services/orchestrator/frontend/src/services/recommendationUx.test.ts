import { describe, expect, it } from 'vitest'
import { canCreateRecommendationWorkItem, canDeferRecommendation, canIgnoreRecommendation, canViewRecommendation, confidencePercent, isProjectionProcessing, requiresApprovalWarning } from './recommendationUx'
import type { AnalysisProjectionStatus } from '../types/analysis'

describe('Recommendation UX state', () => {
  it('distinguishes projection processing from terminal results', () => {
    expect((['NOT_GENERATED', 'PENDING', 'RUNNING'] as AnalysisProjectionStatus[]).every(isProjectionProcessing)).toBe(true)
    expect(isProjectionProcessing('SUCCEEDED')).toBe(false)
    expect(isProjectionProcessing('FAILED')).toBe(false)
  })
  it('supports explicit view/reactivation without changing terminal states', () => {
    expect(canViewRecommendation('NEW')).toBe(true)
    expect(canViewRecommendation('DEFERRED')).toBe(true)
    expect(canViewRecommendation('VIEWED')).toBe(false)
    expect(canViewRecommendation('WORKITEM_CREATED')).toBe(false)
  })
  it('enforces defer, ignore and work-item action guards', () => {
    expect(canDeferRecommendation('VIEWED')).toBe(true)
    expect(canDeferRecommendation('DEFERRED')).toBe(false)
    expect(canIgnoreRecommendation('DEFERRED')).toBe(true)
    expect(canIgnoreRecommendation('IGNORED')).toBe(false)
    expect(canCreateRecommendationWorkItem('IGNORED')).toBe(false)
    expect(canCreateRecommendationWorkItem('WORKITEM_CREATED')).toBe(false)
  })
  it('always warns for READ_WRITE or approval-required recommendations', () => {
    expect(requiresApprovalWarning({ suggestedExecutionMode: 'READ_WRITE', approvalRequired: false })).toBe(true)
    expect(requiresApprovalWarning({ suggestedExecutionMode: 'READ_ONLY', approvalRequired: true })).toBe(true)
    expect(confidencePercent(0.876)).toBe('88%')
  })
})
