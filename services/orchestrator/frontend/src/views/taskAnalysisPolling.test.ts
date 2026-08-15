import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const workspace = readFileSync(fileURLToPath(new URL('./TaskWorkspaceView.vue', import.meta.url)), 'utf8')
const analysis = readFileSync(fileURLToPath(new URL('./TaskAnalysisView.vue', import.meta.url)), 'utf8')

describe('Task detail Analysis polling integration', () => {
  it('tracks the active workspace task without duplicating Analysis polling', () => {
    expect(workspace).toContain('notifications.track(context.task.value)')
    expect(workspace).toContain('const monitored = notifications.taskState(taskId)')
    expect(analysis).toContain('<AnalysisInsights')
  })
})
