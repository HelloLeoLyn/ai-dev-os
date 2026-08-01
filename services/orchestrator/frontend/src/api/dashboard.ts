import { apiClient } from './client'
import type { DashboardSummary } from '../types/dashboard'

export function getDashboard(): Promise<DashboardSummary> {
  return apiClient.get<DashboardSummary>('/api/dashboard')
}
