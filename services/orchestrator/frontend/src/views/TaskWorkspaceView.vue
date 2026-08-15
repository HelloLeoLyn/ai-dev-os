<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTaskAnalysisInsights } from '../api/analysis'
import AsyncState from '../components/AsyncState.vue'
import TaskDetail from '../components/TaskDetail.vue'
import TaskWorkspaceHeader from '../components/TaskWorkspaceHeader.vue'
import { useTaskContext } from '../composables/useTaskContext'
import { useTaskNotifications } from '../composables/useTaskNotifications'
import { projectTaskWorkflow } from '../services/taskWorkflow'
import type { AnalysisInsightResponse } from '../types/analysis'

const route = useRoute(), router = useRouter(), taskId = String(route.params.taskId || '')
const context = useTaskContext(), notifications = useTaskNotifications()
const analysis = ref<AnalysisInsightResponse | null>(null)
const monitored = notifications.taskState(taskId)
const task = computed(() => monitored.value ?? context.task.value)
const findingCount = computed(() => analysis.value?.insight?.findings.length ?? 0)
const recommendationCount = computed(() => analysis.value?.insight?.recommendations.length ?? 0)
const workItemCount = ref(0)
const workflow = computed(() => task.value ? projectTaskWorkflow(task.value, context.approval.value?.status, analysis.value?.status, recommendationCount.value, workItemCount.value) : null)

async function load(): Promise<void> {
  await context.load(taskId)
  if (!context.task.value) return
  notifications.track(context.task.value)
  if (['SUCCESS', 'COMPLETED'].includes(context.task.value.status)) {
    try { analysis.value = await getTaskAnalysisInsights(taskId) } catch { analysis.value = null }
  }
}
watch(monitored, current => { if (current && ['SUCCESS', 'COMPLETED'].includes(current.status) && !analysis.value) void getTaskAnalysisInsights(taskId).then(value => { analysis.value = value }).catch(() => undefined) })
function duplicate(): void { void router.push({ path: '/tasks', query: { duplicate: taskId } }) }
onMounted(load)
</script>

<template><section class="page-stack"><AsyncState :loading="context.loading.value" :error="context.errorMessage.value" :empty="!context.loading.value && !task" empty-text="Task 不存在" @retry="load"><template v-if="task && workflow"><TaskWorkspaceHeader :task="task" :approval="context.approval.value" :workflow="workflow" /><TaskDetail :task="task" :approval="context.approval.value" :approval-loading="context.loading.value" :analysis-status="analysis?.status ?? null" :finding-count="findingCount" :recommendation-count="recommendationCount" @duplicate="duplicate" /></template></AsyncState></section></template>
