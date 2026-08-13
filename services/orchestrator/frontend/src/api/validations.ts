import { apiClient } from './client'
import type { ValidationRun } from '../types/validation'

export function getValidations(): Promise<ValidationRun[]> {
  return apiClient.get<ValidationRun[]>('/api/validations')
}

export function getValidation(validationRunId: string): Promise<ValidationRun> {
  return apiClient.get<ValidationRun>(`/api/validations/${encodeURIComponent(validationRunId)}`)
}

export function getTaskValidations(taskId: string): Promise<ValidationRun[]> {
  return apiClient.get<ValidationRun[]>(`/api/tasks/${encodeURIComponent(taskId)}/validations`)
}

export function startValidation(taskId: string): Promise<ValidationRun> {
  return apiClient.post<ValidationRun>(`/api/tasks/${encodeURIComponent(taskId)}/validations`)
}

export function validationArtifactUrl(artifactId: string): string {
  return `/api/validation-artifacts/${encodeURIComponent(artifactId)}`
}
