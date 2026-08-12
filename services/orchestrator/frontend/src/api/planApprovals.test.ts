import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from './client'
import { approveTask, getPlanApproval, rejectTask } from './planApprovals'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

describe('plan approval task API', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.post).mockReset()
  })

  it('loads the immutable approved-plan payload by approval id', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ id: 'approval-1' })
    await getPlanApproval('approval/1')
    expect(apiClient.get).toHaveBeenCalledWith('/api/plan-approvals/approval%2F1')
  })

  it('uses the atomic task approve endpoint', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})
    await approveTask('task-1', 'alice')
    expect(apiClient.post).toHaveBeenCalledWith('/api/tasks/task-1/approve', {
      approver: 'alice',
    })
  })

  it('uses the atomic task reject endpoint with a reason', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})
    await rejectTask('task-1', 'alice', 'unsafe')
    expect(apiClient.post).toHaveBeenCalledWith('/api/tasks/task-1/reject', {
      approver: 'alice', reason: 'unsafe',
    })
  })
})
