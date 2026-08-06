<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { TaskRecord, TaskStatus } from '../types/task'

defineProps<{
  task: TaskRecord | null
}>()

function statusType(status: TaskStatus): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'info'
    case 'PLANNING':
    case 'APPROVED':
      return 'warning'
    default:
      return 'info'
  }
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <el-card v-if="task" shadow="never" class="task-detail">
    <template #header>
      <div class="detail-header">
        <span class="card-title">{{ task.name || task.taskId }}</span>
        <el-tag :type="statusType(task.status)" effect="dark" size="small">
          {{ task.status }}
        </el-tag>
      </div>
    </template>

    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="Task ID">
        <code>{{ task.taskId }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="描述">
        {{ task.description || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">
        {{ formatDate(task.createdAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="更新时间">
        {{ formatDate(task.updatedAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="Approval ID">
        <code>{{ task.approvalId || '—' }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="PlanRun ID">
        <code>{{ task.planRunId || '—' }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="执行结果">
        <span :class="{ 'error-text': task.errorMessage }">
          {{ task.errorMessage || (task.status === 'SUCCESS' ? '已完成' : '—') }}
        </span>
      </el-descriptions-item>
    </el-descriptions>

    <div class="detail-actions">
      <RouterLink
        class="timeline-link"
        :to="`/timeline?id=${encodeURIComponent(task.taskId)}`"
      >
        查看 Timeline →
      </RouterLink>
    </div>
  </el-card>

  <el-empty v-else description="选择左侧任务查看详情" />
</template>

<style scoped>
.card-title {
  font-weight: 700;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.error-text {
  color: var(--color-danger);
}

.detail-actions {
  margin-top: 1rem;
}

.timeline-link {
  color: var(--color-primary-strong);
  font-weight: 600;
  text-decoration: none;
}

.timeline-link:hover {
  text-decoration: underline;
}
</style>
