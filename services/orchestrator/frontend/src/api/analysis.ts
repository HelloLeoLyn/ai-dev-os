import { apiClient } from './client'
import type { AnalysisInsightResponse, CreateRecommendationWorkItemRequest, RecommendationView, RecommendationWorkItemResult } from '../types/analysis'

const recommendationPath = (id: string) => `/api/recommendations/${encodeURIComponent(id)}`
export const getTaskAnalysisInsights = (taskId: string) => apiClient.get<AnalysisInsightResponse>(`/api/tasks/${encodeURIComponent(taskId)}/analysis-insights`)
export const retryTaskAnalysisInsights = (taskId: string) => apiClient.post<AnalysisInsightResponse>(`/api/tasks/${encodeURIComponent(taskId)}/analysis-insights/retry`)
export const getRecommendation = (id: string) => apiClient.get<RecommendationView>(recommendationPath(id))
export const viewRecommendation = (id: string, actor = 'USER') => apiClient.post<RecommendationView>(`${recommendationPath(id)}/view`, { actor })
export const deferRecommendation = (id: string, deferUntil?: string, reason?: string, actor = 'USER') => apiClient.post<RecommendationView>(`${recommendationPath(id)}/defer`, { deferUntil: deferUntil || undefined, reason: reason || undefined, actor })
export const ignoreRecommendation = (id: string, reason?: string, actor = 'USER') => apiClient.post<RecommendationView>(`${recommendationPath(id)}/ignore`, { reason: reason || undefined, actor })
export const createRecommendationWorkItem = (id: string, request: CreateRecommendationWorkItemRequest = {}) => apiClient.post<RecommendationWorkItemResult>(`${recommendationPath(id)}/work-item`, { ...request, actor: request.actor ?? 'USER' })
