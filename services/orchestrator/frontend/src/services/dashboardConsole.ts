import type { ExecutionRecordDetail } from '../types/execution'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { Project } from '../types/project'
import type { TaskRecord } from '../types/task'
import type { TimelineEventDTO } from '../types/timeline'

const RUNNING = new Set(['APPROVED', 'RUNNING', 'CODING', 'TESTING'])
const SUCCESS = new Set(['SUCCESS', 'COMPLETED'])

export interface DashboardTaskDetail {
  task: TaskRecord
  approval?: PlanApprovalRequest | null
  execution?: ExecutionRecordDetail | null
}

export interface DashboardConsoleModel {
  counts: { running: number; pending: number; failed: number; successful: number; activeProjects: number }
  running: TaskRecord[]
  pending: DashboardTaskDetail[]
  failures: DashboardTaskDetail[]
  successes: DashboardTaskDetail[]
  activeProjects: Project[]
  activity: Array<TimelineEventDTO & { taskId: string; taskName: string }>
}

export function baseDashboardModel(tasks: TaskRecord[], projects: Project[]): DashboardConsoleModel {
  const newest = (items: TaskRecord[]) => [...items].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  const running = tasks.filter((task) => RUNNING.has(task.status))
  const pending = tasks.filter((task) => task.status === 'PLANNING' && task.approvalId)
  const failures = tasks.filter((task) => task.status === 'FAILED')
  const successes = tasks.filter((task) => SUCCESS.has(task.status))
  const activeProjects = projects.filter((project) => project.status === 'ACTIVE')
  return {
    counts: { running: running.length, pending: pending.length, failed: failures.length,
      successful: successes.length, activeProjects: activeProjects.length },
    running: newest(running),
    pending: newest(pending).slice(0, 5).map((task) => ({ task })),
    failures: newest(failures).slice(0, 5).map((task) => ({ task })),
    successes: newest(successes).slice(0, 5).map((task) => ({ task })),
    activeProjects,
    activity: [],
  }
}

export function executionResultSummary(record: ExecutionRecordDetail | null | undefined): string | null {
  return record?.message || record?.output || null
}

export function latestExecution(records: ExecutionRecordDetail[]): ExecutionRecordDetail | null {
  return [...records].sort((a, b) => (b.completedAt || b.startedAt || '')
    .localeCompare(a.completedAt || a.startedAt || ''))[0] ?? null
}
