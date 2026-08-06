import { apiClient } from './client'
import type { CreateTestRequest, TestPlan, TestReport } from '../types/test'

export function getTests(): Promise<TestPlan[]> {
  return apiClient.get<TestPlan[]>('/api/tests')
}

export function getTest(testId: string): Promise<TestPlan> {
  return apiClient.get<TestPlan>(`/api/tests/${encodeURIComponent(testId)}`)
}

export function createTest(request: CreateTestRequest): Promise<TestPlan> {
  return apiClient.post<TestPlan>('/api/tests', request)
}

export function getTestReport(testId: string): Promise<TestReport> {
  return apiClient.get<TestReport>(`/api/tests/${encodeURIComponent(testId)}/report`)
}

export function getTestScreenshotUrl(testId: string): string {
  return `/api/tests/${encodeURIComponent(testId)}/screenshot`
}
