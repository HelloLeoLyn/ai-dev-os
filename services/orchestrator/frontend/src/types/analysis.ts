import type { BacklogItem, BacklogPriority } from './backlog'
import type { ExecutionMode } from './task'

export type AnalysisLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type AnalysisProjectionStatus = 'NOT_GENERATED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type RecommendationStatus = 'NEW' | 'VIEWED' | 'DEFERRED' | 'IGNORED' | 'WORKITEM_CREATED'
export type EvidenceType = 'EXECUTION_RECORD' | 'ARTIFACT' | 'SOURCE_FILE' | 'TIMELINE_EVENT' | 'MEMORY' | 'URL'

export interface EvidenceRef { type: EvidenceType; ref: string; label: string | null; artifactType: string | null; uri: string | null; line: number | null; contentHash: string | null }
export interface Finding { findingId: string; title: string; summary: string; category: string; severity: AnalysisLevel; confidence: number; scope: string[]; evidenceRefs: EvidenceRef[] }
export interface RecommendedNextAction { actionId: string; title: string; description: string; goal: string; acceptanceCriteria: string[]; scope: string[]; dependencies: string[]; suggestedExecutionMode: ExecutionMode; approvalRequired: boolean; estimatedComplexity: 'SMALL' | 'MEDIUM' | 'LARGE' }
export interface Recommendation { recommendationId: string; localRecommendationId?: string; findingIds: string[]; title: string; rationale: string; priority: AnalysisLevel; risk: AnalysisLevel; benefit: AnalysisLevel; scope: string[]; dependencies: string[]; suggestedExecutionMode: ExecutionMode; approvalRequired: boolean; evidenceRefs: EvidenceRef[]; confidence: number; recommendedNextAction: RecommendedNextAction }
export interface AnalysisInsightSet { analysisId: string; sourceTaskId: string; sourceExecutionRecordId: string; projectId: string; workspaceId: string | null; sourceArtifactRefs: EvidenceRef[]; extractorType: string; extractorVersion: string; schemaVersion: string; status: Exclude<AnalysisProjectionStatus, 'NOT_GENERATED'>; errorCode: string | null; errorMessage: string | null; contentFingerprint: string | null; findings: Finding[]; recommendations: Recommendation[]; createdAt: string; updatedAt: string }
export interface AnalysisInsightResponse { status: AnalysisProjectionStatus; insight: AnalysisInsightSet | null }
export interface RecommendationView extends Omit<Recommendation, 'findingIds'> { sourceTaskId: string; sourceExecutionRecordId: string; findings: Finding[]; status: RecommendationStatus; deferUntil: string | null; deferReason: string | null; ignoreReason: string | null; convertedBacklogItemId: string | null; updatedAt: string }
export interface CreateRecommendationWorkItemRequest { title?: string; description?: string; priority?: BacklogPriority; actor?: string }
export interface RecommendationWorkItemResult { created: boolean; backlogItem: BacklogItem }
