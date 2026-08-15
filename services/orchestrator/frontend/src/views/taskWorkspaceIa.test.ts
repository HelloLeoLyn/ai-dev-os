import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

const source = (path: string) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8')
const routes = source('../router/index.ts')
const center = source('./TasksView.vue')
const overview = source('./TaskWorkspaceView.vue')
const detail = source('../components/TaskDetail.vue')
const header = source('../components/TaskWorkspaceHeader.vue')

describe('V04-WORK-008A Task information architecture', () => {
  it('keeps Task Center list-only and opens a dedicated workspace', () => {
    expect(routes).toContain("{ path: '/tasks', component: TasksView")
    expect(routes).toContain("{ path: '/tasks/:taskId', component: TaskWorkspaceView")
    expect(center).toContain('<TaskTable')
    expect(center).not.toContain('<TaskDetail')
    expect(center).toContain('router.push(`/tasks/${encodeURIComponent(task.taskId)}`)')
  })

  it('provides refresh-safe deep links for every workspace section', () => {
    for (const path of ['plan', 'execution', 'analysis', 'timeline']) expect(routes).toContain(`/tasks/:taskId/${path}`)
    for (const label of ['Overview', 'Plan', 'Execution', 'Analysis', 'Timeline']) expect(header).toContain(`label: '${label}'`)
  })

  it('keeps Overview compact and moves full Analysis to its own route', () => {
    expect(overview).toContain('<TaskDetail')
    expect(detail).not.toContain('<AnalysisInsights')
    expect(detail).toContain('findingCount')
    expect(detail).toContain('recommendationCount')
  })

  it('does not introduce automatic approval or recommendation execution', () => {
    expect(center).not.toContain('approveTask')
    expect(overview).not.toContain('createRecommendationWorkItem')
    expect(header).not.toContain('/view')
  })
})
