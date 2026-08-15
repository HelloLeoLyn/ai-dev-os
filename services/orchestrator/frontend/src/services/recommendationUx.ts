import type { AnalysisProjectionStatus, RecommendationStatus, RecommendationView } from '../types/analysis'

export const isProjectionProcessing = (status: AnalysisProjectionStatus) => ['NOT_GENERATED', 'PENDING', 'RUNNING'].includes(status)
export const canViewRecommendation = (status: RecommendationStatus) => status === 'NEW' || status === 'DEFERRED'
export const canDeferRecommendation = (status: RecommendationStatus) => status === 'NEW' || status === 'VIEWED'
export const canIgnoreRecommendation = (status: RecommendationStatus) => !['IGNORED', 'WORKITEM_CREATED'].includes(status)
export const canCreateRecommendationWorkItem = (status: RecommendationStatus) => status !== 'IGNORED' && status !== 'WORKITEM_CREATED'
export const requiresApprovalWarning = (recommendation: Pick<RecommendationView, 'suggestedExecutionMode' | 'approvalRequired'>) => recommendation.suggestedExecutionMode === 'READ_WRITE' || recommendation.approvalRequired
export const confidencePercent = (confidence: number) => `${Math.round(Math.max(0, Math.min(1, confidence)) * 100)}%`
