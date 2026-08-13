<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { approveTask, rejectTask } from '../api/planApprovals'
import PlanApprovalDetail from '../components/PlanApprovalDetail.vue'
import { useTaskContext } from '../composables/useTaskContext'
import { useTaskNotifications } from '../composables/useTaskNotifications'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute()
const taskNotifications = useTaskNotifications()
const { task, approval, loading, errorMessage, load } = useTaskContext()
const decisionBusy = ref(false)
const taskId = String(route.params.taskId || '')
const monitoredTask = taskNotifications.taskState(taskId)
const visibleTask = computed(() => monitoredTask.value ?? task.value)
const terminalNotification = computed(() => taskNotifications.notifications.value
  .find((item) => item.taskId === taskId && ['SUCCESS', 'COMPLETED', 'FAILED'].includes(item.status)) ?? null)
const isRunning = computed(() => ['APPROVED', 'RUNNING', 'CODING', 'TESTING'].includes(visibleTask.value?.status || ''))
const isSuccess = computed(() => ['SUCCESS', 'COMPLETED'].includes(visibleTask.value?.status || ''))
const isFailed = computed(() => visibleTask.value?.status === 'FAILED')

async function decide(action: 'approve' | 'reject', approver: string, reason = ''): Promise<void> {
  if (!task.value || decisionBusy.value) return
  decisionBusy.value = true
  try {
    if (action === 'approve') {
      const approvedTask = await approveTask(task.value.taskId, approver)
      taskNotifications.track(approvedTask)
      ElMessage.success('Plan 已批准，任务开始执行')
    } else {
      const rejectedTask = await rejectTask(task.value.taskId, approver, reason)
      taskNotifications.track(rejectedTask)
      ElMessage.info('Plan 已拒绝')
    }
    await load(task.value.taskId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${action === 'approve' ? 'Approve' : 'Reject'} 失败。`)
  } finally { decisionBusy.value = false }
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(() => load(taskId))
</script>

<template><section class="page-stack">
  <header class="page-header"><div><RouterLink class="back-link" :to="`/tasks/${encodeURIComponent(taskId)}`">← Task Summary</RouterLink><p class="page-eyebrow">Professional Detail</p><h1>{{ task?.name || 'Task Plan' }}</h1><p class="page-description">Plan approval, security constraints and execution intent.</p></div></header>
  <AsyncState :loading="loading" :error="errorMessage" :empty="!loading && !approval" empty-text="当前 Task 暂无 Plan Approval" @retry="load(taskId)">
  <section v-if="visibleTask && (isRunning || isSuccess || isFailed)" class="execution-feedback" :class="{ 'is-success': isSuccess, 'is-failed': isFailed }">
    <div>
      <p class="feedback-kicker">Task Status</p>
      <h2 v-if="isRunning"><StatusBadge :status="visibleTask.status" /></h2>
      <h2 v-else-if="isSuccess">任务执行成功</h2>
      <h2 v-else>任务执行失败</h2>
      <p v-if="isRunning">任务正在执行...</p>
      <template v-else>
        <strong>{{ visibleTask.name || visibleTask.taskId }}</strong>
        <p :class="{ error: isFailed }">{{ terminalNotification?.message || visibleTask.errorMessage || (isSuccess ? '任务执行完成。' : '后端未返回错误详情。') }}</p>
        <small>完成时间：{{ formatDate(terminalNotification?.completedAt || visibleTask.updatedAt) }}</small>
        <small v-if="terminalNotification?.artifactCount !== null && terminalNotification?.artifactCount !== undefined">Artifacts：{{ terminalNotification.artifactCount }}</small>
      </template>
    </div>
    <div class="feedback-actions">
      <RouterLink class="action-link" :to="`/tasks/${encodeURIComponent(taskId)}/execution`">{{ isSuccess ? '查看结果' : '查看 Execution' }}</RouterLink>
      <RouterLink class="action-link" :to="`/timeline?id=${encodeURIComponent(taskId)}`">查看 Timeline</RouterLink>
    </div>
  </section>
  <PlanApprovalDetail v-if="task && approval" :task="task" :approval="approval" :busy="decisionBusy" @approve="(name) => decide('approve', name)" @reject="(name, reason) => decide('reject', name, reason)" />
  </AsyncState>
</section></template>

<style scoped>.back-link { display: inline-block; margin-bottom: 1rem; color: var(--color-primary-strong); text-decoration: none; }.state { text-align: center; color: var(--color-text-muted); }.error { color: var(--color-danger); }.execution-feedback { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 1rem; border: 1px solid var(--color-primary); border-left: 4px solid var(--color-primary); border-radius: var(--radius-small); background: rgb(124 156 255 / 8%); }.execution-feedback.is-success { border-color: var(--color-success); background: rgb(103 194 58 / 8%); }.execution-feedback.is-failed { border-color: var(--color-danger); background: rgb(245 108 108 / 8%); }.execution-feedback h2, .execution-feedback p { margin: .25rem 0; }.execution-feedback small { display: block; margin-top: .3rem; color: var(--color-text-muted); }.feedback-kicker { color: var(--color-text-muted); font-size: .72rem; font-weight: 800; text-transform: uppercase; }.feedback-actions { display: flex; flex-wrap: wrap; gap: .75rem; }.action-link { padding: .55rem .75rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); color: var(--color-primary-strong); text-decoration: none; }@media(max-width:700px){.execution-feedback{align-items:flex-start;flex-direction:column;}}</style>
