<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { approveTask, rejectTask } from '../api/planApprovals'
import PlanApprovalDetail from '../components/PlanApprovalDetail.vue'
import { useTaskContext } from '../composables/useTaskContext'
import { useTaskNotifications } from '../composables/useTaskNotifications'

const route = useRoute()
const taskNotifications = useTaskNotifications()
const { task, approval, loading, errorMessage, load } = useTaskContext()
const decisionBusy = ref(false)
const taskId = String(route.params.taskId || '')

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
  } finally { decisionBusy.value = false }
}

onMounted(() => load(taskId))
</script>

<template><section class="page-stack">
  <header class="page-header"><div><RouterLink class="back-link" :to="`/tasks/${encodeURIComponent(taskId)}`">← Task Summary</RouterLink><p class="page-eyebrow">Professional Detail</p><h1>{{ task?.name || 'Task Plan' }}</h1><p class="page-description">Plan approval, security constraints and execution intent.</p></div></header>
  <el-card v-if="loading" shadow="never"><p class="state">Loading plan…</p></el-card>
  <el-card v-else-if="errorMessage" shadow="never"><p class="state error">{{ errorMessage }}</p></el-card>
  <PlanApprovalDetail v-else-if="task && approval" :task="task" :approval="approval" :busy="decisionBusy" @approve="(name) => decide('approve', name)" @reject="(name, reason) => decide('reject', name, reason)" />
  <el-empty v-else description="当前 Task 暂无 Plan Approval" />
</section></template>

<style scoped>.back-link { display: inline-block; margin-bottom: 1rem; color: var(--color-primary-strong); text-decoration: none; }.state { text-align: center; color: var(--color-text-muted); }.error { color: var(--color-danger); }</style>
