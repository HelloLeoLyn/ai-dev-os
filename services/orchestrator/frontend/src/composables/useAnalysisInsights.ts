import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { createRecommendationWorkItem, deferRecommendation, getRecommendation, getTaskAnalysisInsights, ignoreRecommendation, retryTaskAnalysisInsights, viewRecommendation } from '../api/analysis'
import { getBacklogItem } from '../api/backlog'
import { isProjectionProcessing } from '../services/recommendationUx'
import type { AnalysisInsightResponse, CreateRecommendationWorkItemRequest, RecommendationView, RecommendationWorkItemResult } from '../types/analysis'
import type { TaskRecord } from '../types/task'
import type { BacklogItem } from '../types/backlog'

const SUCCESS = new Set(['SUCCESS', 'COMPLETED'])
export function useAnalysisInsights(task: Ref<TaskRecord | null>, analysisTask: Ref<boolean>) {
  const response = ref<AnalysisInsightResponse | null>(null)
  const recommendations = ref<Record<string, RecommendationView>>({})
  const workItems = ref<Record<string, BacklogItem>>({})
  const loading = ref(false), retrying = ref(false), error = ref<string | null>(null)
  const busy = ref<Record<string, boolean>>({})
  let timer: ReturnType<typeof setTimeout> | null = null, generation = 0
  const clearTimer = () => { if (timer !== null) globalThis.clearTimeout(timer); timer = null }
  const schedule = (token: number) => { clearTimer(); timer = globalThis.setTimeout(() => void load(token), 2500) }
  async function load(token = ++generation): Promise<void> {
    clearTimer()
    const current = task.value
    if (!current || !SUCCESS.has(current.status)) { response.value = null; recommendations.value = {}; return }
    loading.value = response.value === null; error.value = null
    try {
      const next = await getTaskAnalysisInsights(current.taskId)
      if (token !== generation || task.value?.taskId !== current.taskId) return
      response.value = next
      if (next.status === 'SUCCEEDED' && next.insight) {
        const views = await Promise.all(next.insight.recommendations.map(item => getRecommendation(item.recommendationId)))
        if (token === generation) {
          recommendations.value = Object.fromEntries(views.map(item => [item.recommendationId, item]))
          const converted = views.filter(item => item.convertedBacklogItemId)
          const items = await Promise.all(converted.map(item => getBacklogItem(item.convertedBacklogItemId!)))
          if (token === generation) workItems.value = Object.fromEntries(items.map(item => [item.backlogItemId, item]))
        }
      } else recommendations.value = {}
      if (isProjectionProcessing(next.status) && (next.status !== 'NOT_GENERATED' || analysisTask.value)) schedule(token)
    } catch (cause) { if (token === generation) error.value = cause instanceof Error ? cause.message : 'Unable to load Analysis.' }
    finally { if (token === generation) loading.value = false }
  }
  async function retry(): Promise<void> {
    if (!task.value || retrying.value) return
    retrying.value = true; error.value = null; const token = ++generation
    try { response.value = await retryTaskAnalysisInsights(task.value.taskId); if (isProjectionProcessing(response.value.status)) schedule(token) }
    catch (cause) { error.value = cause instanceof Error ? cause.message : 'Unable to retry Analysis Projection.' }
    finally { retrying.value = false }
  }
  async function mutate(id: string, operation: () => Promise<RecommendationView>): Promise<RecommendationView | null> {
    if (busy.value[id]) return null
    busy.value = { ...busy.value, [id]: true }; error.value = null
    try { await operation(); const authoritative = await getRecommendation(id); recommendations.value = { ...recommendations.value, [id]: authoritative }; return authoritative }
    catch (cause) { error.value = cause instanceof Error ? cause.message : 'Recommendation operation failed.'; return null }
    finally { busy.value = { ...busy.value, [id]: false } }
  }
  const view = (id: string) => mutate(id, () => viewRecommendation(id))
  const defer = (id: string, until?: string, reason?: string) => mutate(id, () => deferRecommendation(id, until, reason))
  const ignore = (id: string, reason?: string) => mutate(id, () => ignoreRecommendation(id, reason))
  async function createWorkItem(id: string, request: CreateRecommendationWorkItemRequest): Promise<RecommendationWorkItemResult | null> {
    if (busy.value[id]) return null
    busy.value = { ...busy.value, [id]: true }; error.value = null
    try { const result = await createRecommendationWorkItem(id, request); const authoritative = await getRecommendation(id); recommendations.value = { ...recommendations.value, [id]: authoritative }; workItems.value = { ...workItems.value, [result.backlogItem.backlogItemId]: result.backlogItem }; return result }
    catch (cause) { error.value = cause instanceof Error ? cause.message : 'Unable to create WorkItem.'; return null }
    finally { busy.value = { ...busy.value, [id]: false } }
  }
  watch(() => [task.value?.taskId, task.value?.status, analysisTask.value], () => { generation++; void load(generation) }, { immediate: true })
  onBeforeUnmount(() => { generation++; clearTimer() })
  return { response, recommendations, workItems, loading, retrying, error, busy, load: () => load(++generation), retry, view, defer, ignore, createWorkItem }
}
