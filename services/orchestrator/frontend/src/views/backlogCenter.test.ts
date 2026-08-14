import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const view = readFileSync(fileURLToPath(new URL('./BacklogCenterView.vue', import.meta.url)), 'utf8')
const api = readFileSync(fileURLToPath(new URL('../api/backlog.ts', import.meta.url)), 'utf8')

describe('Backlog Center', () => {
  it('renders summary, loading/error/empty states and required actions', () => {
    for (const value of ['IDEA', 'PLANNED', 'READY', 'BLOCKED', 'CONVERTED', 'DONE', 'Create', 'Edit', 'Block', 'Unblock', 'Convert', 'Cancel']) expect(view).toContain(value)
    expect(view).toContain('AsyncState')
    expect(view).toContain('Open Task Detail')
  })
  it('requires formal task context and uses the controlled conversion API', () => {
    for (const value of ['Goal', 'Project', 'Workspace', 'Execution Mode']) expect(view).toContain(value)
    expect(api).toContain('/convert-to-task')
    expect(view).toContain('never approves or executes')
  })
  it('supports LESSON and ROADMAP source references', () => {
    expect(view).toContain('LESSON')
    expect(view).toContain('ROADMAP')
    expect(view).toContain('Source Reference')
  })
})
