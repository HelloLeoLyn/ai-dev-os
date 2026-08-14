export interface NavigationItem { to: string; label: string }
export interface NavigationGroup { label: string; items: NavigationItem[] }

export const navigationGroups: NavigationGroup[] = [
  { label: 'Workspace', items: [
    { to: '/dashboard', label: 'Dashboard' }, { to: '/projects', label: 'Projects' },
    { to: '/tasks', label: 'Tasks' }, { to: '/workspaces', label: 'Workspaces' },
  ] },
  { label: 'Execution', items: [
    { to: '/executions', label: 'Executions' }, { to: '/jobs', label: 'Jobs' },
    { to: '/timeline', label: 'Timeline' }, { to: '/audit', label: 'Audit' },
  ] },
  { label: 'AI', items: [
    { to: '/agents', label: 'Agents' },
    { to: '/models', label: 'Models' }, { to: '/memory', label: 'Memory' },
    { to: '/skills', label: 'Skills' }, { to: '/mcp/plugins', label: 'MCP Plugins' },
  ] },
  { label: 'Operations', items: [
    { to: '/schedules', label: 'Schedules' }, { to: '/tests', label: 'Validation' },
    { to: '/settings/network', label: 'Network / Proxy' },
  ] },
]

export function isNavigationActive(currentPath: string, target: string): boolean {
  return currentPath === target || currentPath.startsWith(`${target}/`)
}
