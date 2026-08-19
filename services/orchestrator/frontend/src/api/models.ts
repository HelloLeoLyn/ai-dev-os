import { apiClient } from './client'
import type {
  DefaultModelResponse,
  ModelDefinition,
  ModelProvider,
  ModelRoute,
  ProviderDefinition,
  ProviderStatus,
} from '../types/model'

export function getModels(): Promise<ModelProvider[]> {
  return apiClient.get<ModelProvider[]>('/api/models')
}

export function getModelRoutes(): Promise<ModelRoute[]> {
  return apiClient.get<ModelRoute[]>('/api/models/routes')
}

export const modelRegistryApi = {
  listProviders(): Promise<ProviderDefinition[]> {
    return apiClient.get<ProviderDefinition[]>('/api/model-registry/providers')
  },
  createProvider(definition: ProviderDefinition): Promise<ProviderDefinition> {
    return apiClient.post<ProviderDefinition>('/api/model-registry/providers', definition)
  },
  updateProvider(id: string, definition: ProviderDefinition): Promise<ProviderDefinition> {
    return apiClient.put<ProviderDefinition>(
      `/api/model-registry/providers/${encodeURIComponent(id)}`,
      definition,
    )
  },
  setProviderEnabled(id: string, enabled: boolean): Promise<ProviderDefinition> {
    return apiClient.post<ProviderDefinition>(
      `/api/model-registry/providers/${encodeURIComponent(id)}/enabled`,
      { enabled },
    )
  },
  listModels(): Promise<ModelDefinition[]> {
    return apiClient.get<ModelDefinition[]>('/api/model-registry/models')
  },
  createModel(definition: ModelDefinition): Promise<ModelDefinition> {
    return apiClient.post<ModelDefinition>('/api/model-registry/models', definition)
  },
  updateModel(id: string, definition: ModelDefinition): Promise<ModelDefinition> {
    return apiClient.put<ModelDefinition>(
      `/api/model-registry/models/${encodeURIComponent(id)}`,
      definition,
    )
  },
  setModelEnabled(id: string, enabled: boolean): Promise<ModelDefinition> {
    return apiClient.post<ModelDefinition>(
      `/api/model-registry/models/${encodeURIComponent(id)}/enabled`,
      { enabled },
    )
  },
  providerStatus(id: string): Promise<ProviderStatus> {
    return apiClient.get<ProviderStatus>(
      `/api/model-registry/providers/${encodeURIComponent(id)}/status`,
    )
  },
}

export function getDefaultModel(): Promise<DefaultModelResponse> {
  return apiClient.get<DefaultModelResponse>('/api/model-registry/default-model')
}
