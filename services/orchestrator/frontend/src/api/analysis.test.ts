import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRecommendationWorkItem, deferRecommendation, getRecommendation, getTaskAnalysisInsights, ignoreRecommendation, retryTaskAnalysisInsights, viewRecommendation } from './analysis'

describe('Analysis and Recommendation API compatibility', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('uses the existing 005A and 005B endpoints and GET has no view side effect', async () => {
    vi.stubGlobal('window', { location: { origin: 'http://localhost' } })
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'NOT_GENERATED', insight: null }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetch)
    await getTaskAnalysisInsights('task /1')
    expect(fetch.mock.calls[0][0]).toBe('/api/tasks/task%20%2F1/analysis-insights')
    expect(fetch.mock.calls[0][1].method).toBe('GET')
    expect(fetch.mock.calls[0][0]).not.toContain('/view')
  })
  it('maps retry and every explicit recommendation operation', async () => {
    vi.stubGlobal('window', { location: { origin: 'http://localhost' } })
    const fetch = vi.fn().mockImplementation(async () => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetch)
    await retryTaskAnalysisInsights('task-1'); await getRecommendation('rec-1'); await viewRecommendation('rec-1'); await deferRecommendation('rec-1', undefined, 'later'); await ignoreRecommendation('rec-1', 'not relevant'); await createRecommendationWorkItem('rec-1')
    expect(fetch.mock.calls.map(call => call[0])).toEqual(['/api/tasks/task-1/analysis-insights/retry', '/api/recommendations/rec-1', '/api/recommendations/rec-1/view', '/api/recommendations/rec-1/defer', '/api/recommendations/rec-1/ignore', '/api/recommendations/rec-1/work-item'])
  })
})
