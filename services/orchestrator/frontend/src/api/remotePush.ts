import { apiClient } from './client'

export interface RemotePushApproval {
  approvalId: string; taskId: string; executionWorkspaceId: string; executionBranch: string
  commitId: string; commitHash: string; remote: string; targetRef: string
  authority: 'REMOTE'; operation: 'PUSH_TASK_BRANCH'; status: string
}
export function getRemotePushApprovals(taskId: string): Promise<RemotePushApproval[]> { return apiClient.get(`/api/tasks/${encodeURIComponent(taskId)}/remote-push-approvals`) }
export function approveRemotePush(id: string): Promise<RemotePushApproval> { return apiClient.post(`/api/remote-push-approvals/${encodeURIComponent(id)}/approve`) }
export function rejectRemotePush(id: string): Promise<RemotePushApproval> { return apiClient.post(`/api/remote-push-approvals/${encodeURIComponent(id)}/reject`) }
export function pushRemote(commitId: string, remote: string, approvalId: string): Promise<unknown> {
  return apiClient.post(`/api/commits/${encodeURIComponent(commitId)}/push`, { remote, approvalId })
}
