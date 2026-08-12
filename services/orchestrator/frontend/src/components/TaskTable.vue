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

:deep(.el-table) {
  width: 100%;
}
</style>
