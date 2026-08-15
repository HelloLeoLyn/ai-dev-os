import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const backlog = readFileSync(fileURLToPath(new URL('./BacklogCenterView.vue', import.meta.url)), 'utf8')
const header = readFileSync(fileURLToPath(new URL('../components/TaskWorkspaceHeader.vue', import.meta.url)), 'utf8')

describe('V04-WORK-008C1 workflow continuity', () => {
  it('prefills structured Recommendation context without parsing description', () => {
    expect(backlog).toContain('item.recommendationContext?.goal')
    expect(backlog).toContain('item.recommendationContext?.suggestedExecutionMode')
    expect(backlog).toContain('item.projectId ??')
    expect(backlog).toContain('item.workspaceId ??')
    expect(backlog).not.toContain('parseRecommendation')
  })

  it('requires explicit confirmation of selected execution mode', () => {
    expect(backlog).toContain('Suggested Execution Mode')
    expect(backlog).toContain('Selected Execution Mode')
    expect(backlog).toContain('I confirm the selected execution mode for the new Task.')
    expect(backlog).toContain(':disabled="!executionModeConfirmed"')
    expect(backlog).toContain('READ_WRITE still requires explicit Plan Approval')
  })

  it('renders source and converted backlinks using server data', () => {
    expect(backlog).toContain('Source Recommendation')
    expect(backlog).toContain('Open Source Analysis')
    expect(backlog).toContain('selected.convertedTaskId')
    expect(header).toContain('task.sourceBacklogItemId')
    expect(header).toContain('getBacklogItem(id)')
    expect(header).toContain('Original Task')
  })

  it('does not introduce automatic workflow mutations', () => {
    expect(backlog).not.toContain('approveTask')
    expect(backlog).not.toContain('executeTask')
    expect(header).not.toContain('changeBacklogStatus')
  })
})
