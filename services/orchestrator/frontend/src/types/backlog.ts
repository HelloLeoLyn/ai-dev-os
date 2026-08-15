import type { ExecutionMode, TaskRecord } from './task'

export type BacklogStatus = 'IDEA' | 'PLANNED' | 'READY' | 'BLOCKED' | 'CONVERTED' | 'DONE' | 'CANCELLED'
export type BacklogPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type BacklogSourceType = 'MANUAL' | 'LESSON' | 'ROADMAP' | 'TASK' | 'SYSTEM'

export interface BacklogRecommendationContext {
  recommendationId: string
  analysisId: string
  sourceTaskId: string
  goal: string
  acceptanceCriteria: string[]
  risk: BacklogPriority
  scope: string[]
  suggestedExecutionMode: ExecutionMode
  approvalRequired: boolean
}

export interface BacklogItem {
  backlogItemId: string
  title: string
  description: string | null
  status: BacklogStatus
  priority: BacklogPriority
  projectId: string | null
  workspaceId: string | null
  sourceType: BacklogSourceType
  sourceReference: string | null
  blockedReason: string | null
  dependsOn: string[]
  tags: string[]
  createdAt: string
  updatedAt: string
  convertedTaskId: string | null
  completedAt: string | null
  recommendationContext?: BacklogRecommendationContext | null
}

export interface BacklogDraft {
  title: string
  description: string
  status: BacklogStatus
  priority: BacklogPriority
  projectId: string
  workspaceId: string
  sourceType: BacklogSourceType
  sourceReference: string
  blockedReason: string
  dependsOn: string[]
  tags: string[]
}

export interface ConvertBacklogRequest {
  goal: string
  plannerName: string
  projectId: string
  workspaceId: string
  executionMode: ExecutionMode
}

export interface BacklogConversionResult { backlogItem: BacklogItem; task: TaskRecord }
