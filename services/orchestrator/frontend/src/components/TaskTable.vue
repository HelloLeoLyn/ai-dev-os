<script setup lang="ts">
import type { TaskRecord, TaskStatus } from '../types/task'

defineProps<{
  tasks: TaskRecord[]
  loading?: boolean
  selectedTaskId?: string | null
}>()

const emit = defineEmits<{
  select: [task: TaskRecord]
}>()

function statusType(status: TaskStatus): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS':
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
    case 'CODING':
    case 'TESTING':
      return 'info'
    case 'PLANNING':
    case 'APPROVED':
      return 'warning'
    default:
      return 'info'
  }
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <el-table
    :data="tasks"
    v-loading="loading"
    stripe
    empty-text="暂无任务"
    highlight-current-row
    :current-row-key="selectedTaskId"
    row-key="taskId"
    @current-change="(row: TaskRecord | null) => row && emit('select', row)"
  >
    <el-table-column prop="name" label="Task Name" min-width="150">
      <template #default="{ row }: { row: TaskRecord }">
        <span class="task-name">{{ row.name || row.taskId }}</span>
      </template>
    </el-table-column>
    <el-table-column label="Status" width="112">
      <template #default="{ row }: { row: TaskRecord }">
        <el-tag :type="statusType(row.status)" effect="dark" size="small">
          {{ row.status }}
        </el-tag>
        <small v-if="['RUNNING', 'CODING', 'TESTING'].includes(row.status)" class="status-copy">执行中...</small>
        <small v-else-if="row.status === 'FAILED' && row.errorMessage" class="status-copy status-copy--error" :title="row.errorMessage">{{ row.errorMessage }}</small>
        <RouterLink v-else-if="['SUCCESS', 'COMPLETED'].includes(row.status)" class="result-link" :to="`/tasks/${encodeURIComponent(row.taskId)}/execution`" @click.stop>查看结果 →</RouterLink>
        <RouterLink v-else-if="row.status === 'FAILED'" class="result-link result-link--error" :to="`/tasks/${encodeURIComponent(row.taskId)}/execution`" @click.stop>查看 Execution →</RouterLink>
      </template>
    </el-table-column>
    <el-table-column prop="projectId" label="Project" min-width="130" show-overflow-tooltip />
    <el-table-column label="Mode" width="112">
      <template #default="{ row }: { row: TaskRecord }">
        <el-tag :type="row.executionMode === 'READ_ONLY' ? 'warning' : 'danger'" size="small">
          {{ row.executionMode }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="Created" min-width="150">
      <template #default="{ row }: { row: TaskRecord }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.task-name {
  font-weight: 600;
}

.status-copy, .result-link { display: block; max-width: 10rem; margin-top: .35rem; color: var(--color-text-muted); font-size: .72rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-copy--error, .result-link--error { color: var(--color-danger); }
.result-link { color: var(--color-primary-strong); text-decoration: none; }

:deep(.el-table) {
  width: 100%;
}
</style>
