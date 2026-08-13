import { describe, expect, it } from 'vitest'
import { isNavigationActive, navigationGroups } from './navigation'

describe('console navigation', () => {
  it('groups the product routes by workspace, execution, AI and operations', () => {
    expect(navigationGroups.map((group) => group.label)).toEqual(['Workspace', 'Execution', 'AI', 'Operations'])
    expect(navigationGroups.find((group) => group.label === 'Workspace')?.items.map((item) => item.label))
      .toEqual(['Dashboard', 'Projects', 'Tasks', 'Workspaces'])
    expect(navigationGroups.find((group) => group.label === 'AI')?.items).toEqual(expect.arrayContaining([
      { to: '/agent-market', label: 'Agent Market' }, { to: '/agent-metrics', label: 'Agent Metrics' },
      { to: '/agent-flow', label: 'Agent Flow' },
    ]))
  })

  it('keeps parent navigation active on detail routes without false prefixes', () => {
    expect(isNavigationActive('/tasks/task-1/plan', '/tasks')).toBe(true)
    expect(isNavigationActive('/projects/project-1', '/projects')).toBe(true)
    expect(isNavigationActive('/tasks-extra', '/tasks')).toBe(false)
  })
})
