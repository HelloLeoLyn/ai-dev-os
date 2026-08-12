import { ref } from 'vue'
import { getPlanApproval } from '../api/planApprovals'
import { getTask } from '../api/tasks'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'

export async function getTaskApproval(task: TaskRecord): Promise<PlanApprovalRequest | null> {
  return task.approvalId ? getPlanApproval(task.approvalId) : null
}

export function useTaskContext() {
  const task = ref<TaskRecord | null>(null)
  const approval = ref<PlanApprovalRequest | null>(null)
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  async function load(taskId: string): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      task.value = await getTask(taskId)
      approval.value = await getTaskApproval(task.value)
    } catch (error) {
      task.value = null
      approval.value = null
      errorMessage.value = error instanceof Error ? error.message : 'Unable to load task.'
    } finally {
      loading.value = false
    }
  }

  return { task, approval, loading, errorMessage, load }
}
