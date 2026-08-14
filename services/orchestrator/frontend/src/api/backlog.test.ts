import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { changeBacklogStatus } from './backlog'

vi.mock('./client', () => ({
  apiClient: { post: vi.fn() },
}))

describe('backlog API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sends the IDEA to PLANNED status contract expected by the backend', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({})

    await changeBacklogStatus('backlog-1', 'PLANNED')

    expect(apiClient.post).toHaveBeenCalledWith('/api/backlog/backlog-1/status', {
      status: 'PLANNED',
    })
  })
})
