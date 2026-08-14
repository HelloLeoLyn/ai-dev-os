import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest } from './client'

describe('API client errors', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('preserves a structured backend error message', async () => {
    vi.stubGlobal('window', { location: { origin: 'http://localhost' } })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 'ILLEGAL_ARGUMENT', message: 'Invalid backlog transition' }),
      { status: 400, headers: { 'Content-Type': 'application/json' } },
    )))

    await expect(apiRequest('/api/backlog/backlog-1/status', {
      method: 'POST', body: { status: 'PLANNED' },
    })).rejects.toMatchObject({
      status: 400,
      message: 'Invalid backlog transition',
    } satisfies Partial<ApiError>)
  })
})
