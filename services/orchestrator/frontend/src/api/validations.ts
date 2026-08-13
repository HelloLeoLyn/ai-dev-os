import { apiClient } from './client'
import type { QualityGateResult, SecurityFinding, SecurityReport, ValidationRun } from '../types/validation'

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
export function getQualityGates(runId:string):Promise<QualityGateResult[]>{return apiClient.get(`/api/validations/${encodeURIComponent(runId)}/quality-gates`)}
export function evaluateQualityGate(runId:string):Promise<QualityGateResult>{return apiClient.post(`/api/validations/${encodeURIComponent(runId)}/quality-gate`)}
export function approveQualityGate(id:string):Promise<QualityGateResult>{return apiClient.post(`/api/quality-gates/${encodeURIComponent(id)}/approve`,{reviewer:'console-user'})}
export function rejectQualityGate(id:string):Promise<QualityGateResult>{return apiClient.post(`/api/quality-gates/${encodeURIComponent(id)}/reject`,{reviewer:'console-user'})}
