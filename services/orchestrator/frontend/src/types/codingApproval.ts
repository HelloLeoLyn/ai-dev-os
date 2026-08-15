export type CodingApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CONSUMED'

export interface CodingApprovalRequest {
  id: string
  taskId: string
  jobId: string | null
  workspace: string
  sandbox: string
  reason: string
  status: CodingApprovalStatus
  createdAt: string
  decidedAt: string | null
}
