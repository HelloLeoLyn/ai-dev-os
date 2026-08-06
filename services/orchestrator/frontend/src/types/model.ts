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
