import { apiClient } from './client'
import type { CodingApprovalRequest } from '../types/codingApproval'

export function getCodingApproval(id: string): Promise<CodingApprovalRequest> {
  return apiClient.get(`/api/approvals/${encodeURIComponent(id)}`)
}

export function approveCodingApproval(id: string): Promise<CodingApprovalRequest> {
  return apiClient.post(`/api/approvals/${encodeURIComponent(id)}/approve`)
}

export function rejectCodingApproval(id: string): Promise<CodingApprovalRequest> {
  return apiClient.post(`/api/approvals/${encodeURIComponent(id)}/reject`)
}
