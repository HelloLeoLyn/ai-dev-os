import { apiClient } from './client'
import type { ModelProvider, ModelRoute } from '../types/model'

export function getModels(): Promise<ModelProvider[]> {
  return apiClient.get<ModelProvider[]>('/api/models')
}

export function getModelRoutes(): Promise<ModelRoute[]> {
  return apiClient.get<ModelRoute[]>('/api/models/routes')
}
