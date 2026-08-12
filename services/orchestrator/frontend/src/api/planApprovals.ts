import { apiClient } from './client'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'

export function getPlanApproval(id: string): Promise<PlanApprovalRequest> {
  return apiClient.get(`/api/plan-approvals/${encodeURIComponent(id)}`)
}

export function approveTask(taskId: string, approver: string): Promise<TaskRecord> {
  return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/approve`, { approver })
}

export function rejectTask(taskId: string, approver: string, reason: string): Promise<TaskRecord> {
  return apiClient.post(`/api/tasks/${encodeURIComponent(taskId)}/reject`, { approver, reason })
}
