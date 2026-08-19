export interface ModelProvider {
  providerId: string
  name: string | null
  type: string | null
  model: string | null
  enabled: boolean
}

export interface ModelRoute {
  taskType: string
  providerId: string
  model: string | null
  enabled: boolean
}

export interface ProviderDefinition {
  providerId: string
  displayName: string
  baseUrl: string | null
  credentialRef: string | null
  enabled: boolean
}

export interface ModelDefinition {
  modelId: string
  displayName: string
  providerId: string
  executorType: string
  enabled: boolean
  capabilities: string[]
}

export interface ProviderStatus {
  providerId: string
  credentialRef: string | null
  credentialConfigured: boolean
}

export interface DefaultModelResponse {
  modelId: string | null
}
