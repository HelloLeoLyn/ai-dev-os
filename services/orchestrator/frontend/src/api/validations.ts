import { apiClient } from './client'
import type { SecurityFinding, SecurityReport, ValidationRun } from '../types/validation'

export function getValidations(): Promise<ValidationRun[]> {
  return apiClient.get<ValidationRun[]>('/api/validations')
}

export function getSecurityReports(runId:string):Promise<SecurityReport[]>{return apiClient.get(`/api/validations/${encodeURIComponent(runId)}/security-reports`)}
export function getSecurityReport(reportId:string):Promise<SecurityReport>{return apiClient.get(`/api/security-reports/${encodeURIComponent(reportId)}`)}
export function getSecurityFindings(reportId:string):Promise<SecurityFinding[]>{return apiClient.get(`/api/security-reports/${encodeURIComponent(reportId)}/findings`)}

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
