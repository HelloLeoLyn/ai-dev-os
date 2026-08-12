import type { PlanApprovalRequest } from '../types/planApproval'
import type { CreateTaskRequest, TaskRecord } from '../types/task'

const METADATA_KEY = 'ai-dev-os.task-create-metadata.v1'

export interface TaskCreateMetadata {
  plannerName: string
}

export interface DuplicateTaskDraft {
  request: CreateTaskRequest
  plannerWasRecovered: boolean
}

function readMetadata(): Record<string, TaskCreateMetadata> {
  if (typeof window === 'undefined') return {}
  try {
    const value = JSON.parse(window.localStorage.getItem(METADATA_KEY) || '{}')
    return value && typeof value === 'object' && !Array.isArray(value)
      ? value as Record<string, TaskCreateMetadata> : {}
  } catch { return {} }
}

export function rememberTaskCreateMetadata(taskId: string, request: CreateTaskRequest): void {
  if (typeof window === 'undefined') return
  const metadata = readMetadata()
  metadata[taskId] = { plannerName: request.plannerName }
  window.localStorage.setItem(METADATA_KEY, JSON.stringify(metadata))
}

export function taskCreateMetadata(taskId: string): TaskCreateMetadata | null {
  return readMetadata()[taskId] ?? null
}

export function duplicateTaskDraft(
  task: TaskRecord,
  approval: PlanApprovalRequest | null,
  metadata: TaskCreateMetadata | null,
): DuplicateTaskDraft {
  return {
    request: {
      name: `${task.name || 'Untitled Task'} - 副本`,
      description: task.description || '',
      goal: approval?.plan.goal || '',
      plannerName: metadata?.plannerName || 'hermes',
      projectId: task.projectId,
      workspaceId: task.workspaceId || '',
      executionMode: task.executionMode,
    },
    plannerWasRecovered: Boolean(metadata?.plannerName),
  }
}
