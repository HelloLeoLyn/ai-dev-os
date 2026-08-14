import { describe, expect, it } from 'vitest'
import { isNavigationActive, navigationGroups } from './navigation'

describe('console navigation', () => {
  it('groups the product routes by workspace, execution, AI and operations', () => {
    expect(navigationGroups.map((group) => group.label)).toEqual(['Workspace', 'Execution', 'AI', 'Operations'])
    expect(navigationGroups.find((group) => group.label === 'Workspace')?.items.map((item) => item.label))
      .toEqual(['Dashboard', 'Projects', 'Tasks', 'Backlog', 'Workspaces'])
    expect(navigationGroups.find((group) => group.label === 'AI')?.items).toEqual(expect.arrayContaining([
      { to: '/agents', label: 'Agents' }, { to: '/models', label: 'Models' },
    ]))
    expect(navigationGroups.flatMap((group) => group.items).some((item) => item.to === '/agent-market')).toBe(false)
  })

  it('keeps parent navigation active on detail routes without false prefixes', () => {
    expect(isNavigationActive('/tasks/task-1/plan', '/tasks')).toBe(true)
    expect(isNavigationActive('/projects/project-1', '/projects')).toBe(true)
    expect(isNavigationActive('/tasks-extra', '/tasks')).toBe(false)
  })
})
