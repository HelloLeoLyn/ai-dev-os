import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { approveCodingApproval, getCodingApproval, rejectCodingApproval } from './codingApprovals'

vi.mock('./client', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

describe('coding approval API', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('keeps reads side-effect free', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({})
    await getCodingApproval('approval/1')
    expect(apiClient.get).toHaveBeenCalledWith('/api/approvals/approval%2F1')
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('uses existing approve and reject resume endpoints', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})
    await approveCodingApproval('approval-1')
    await rejectCodingApproval('approval-2')
    expect(apiClient.post).toHaveBeenNthCalledWith(1, '/api/approvals/approval-1/approve')
    expect(apiClient.post).toHaveBeenNthCalledWith(2, '/api/approvals/approval-2/reject')
  })
})
