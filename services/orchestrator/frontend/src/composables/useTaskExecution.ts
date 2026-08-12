import { ref } from 'vue'
import { getExecutionRecord, getExecutionRecords } from '../api/executions'
import type { ExecutionRecordDetail } from '../types/execution'

export function useTaskExecution() {
  const records = ref<ExecutionRecordDetail[]>([])
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  async function load(taskId: string): Promise<void> {
    loading.value = true
    errorMessage.value = null
    try {
      const summaries = await getExecutionRecords({ taskId })
      records.value = await Promise.all(summaries.map((item) => getExecutionRecord(item.id)))
    } catch (error) {
      records.value = []
      errorMessage.value = error instanceof Error ? error.message : 'Unable to load executions.'
    } finally {
      loading.value = false
    }
  }

  return { records, loading, errorMessage, load }
}
