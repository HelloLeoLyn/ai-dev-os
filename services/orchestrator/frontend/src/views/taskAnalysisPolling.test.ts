import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const tasks = readFileSync(fileURLToPath(new URL('./TasksView.vue', import.meta.url)), 'utf8')

describe('Task detail Analysis polling integration', () => {
  it('tracks active tasks and applies TaskPollingMonitor updates to the selected detail', () => {
    expect(tasks).toContain('taskNotifications.track(task)')
    expect(tasks).toContain('monitoredSelectedTask')
    expect(tasks).toContain('selectedTask.value = current')
  })
})
