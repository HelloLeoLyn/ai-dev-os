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
    <el-table-column prop="name" label="任务名称" min-width="160">
      <template #default="{ row }: { row: TaskRecord }">
        <span class="task-name">{{ row.name || row.taskId }}</span>
      </template>
    </el-table-column>
    <el-table-column label="描述" min-width="220">
      <template #default="{ row }: { row: TaskRecord }">
        {{ row.description || '—' }}
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="110">
      <template #default="{ row }: { row: TaskRecord }">
        <el-tag :type="statusType(row.status)" effect="dark" size="small">
          {{ row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" min-width="160">
      <template #default="{ row }: { row: TaskRecord }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="更新时间" min-width="160">
      <template #default="{ row }: { row: TaskRecord }">
        {{ formatDate(row.updatedAt) }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.task-name {
  font-weight: 600;
}
</style>
