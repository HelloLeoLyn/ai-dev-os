import { apiClient } from './client'

export type ProxyMode = 'DIRECT' | 'SYSTEM' | 'CUSTOM'
export type ProxyHostStrategy = 'MANUAL' | 'AUTO_WINDOWS_HOST'
export interface ProxySettings {
  mode: ProxyMode
  hostStrategy: ProxyHostStrategy
  httpProxy?: string
  httpsProxy?: string
  socks5Proxy?: string
  noProxy?: string
  version?: number
  updatedAt?: string
  resolvedWindowsHost?: string
  errorCode?: string
}
export interface NetworkProbe {
  target: string
  url: string
  route: 'DIRECT' | 'SYSTEM' | 'PROXY' | 'FAILED'
  success: boolean
  durationMs: number
  errorCode?: string
}
export const networkApi = {
  get: () => apiClient.get<ProxySettings>('/api/settings/network'),
  save: (settings: ProxySettings) => apiClient.put<ProxySettings>('/api/settings/network', settings),
  probe: () => apiClient.post<NetworkProbe[]>('/api/settings/network/probes'),
}
