<script setup lang="ts">
import type { TestPlan, TestStatus, TestType } from '../types/test'

defineProps<{
  tests: TestPlan[]
  loading?: boolean
  selectedTestId?: string | null
}>()

const emit = defineEmits<{
  select: [plan: TestPlan]
}>()

const typeLabels: Record<TestType, string> = {
  UNIT_TEST: '单元测试',
  API_TEST: 'API 测试',
  UI_TEST: 'UI 测试',
  BUILD_VERIFY: '构建验证',
}

function statusTone(status: TestStatus): 'success' | 'danger' | 'info' | 'warning' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
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
    :data="tests"
    v-loading="loading"
    stripe
    empty-text="暂无测试任务"
    highlight-current-row
    :current-row-key="selectedTestId"
    row-key="testId"
    @current-change="(row: TestPlan | null) => row && emit('select', row)"
  >
    <el-table-column label="Test ID" min-width="180">
      <template #default="{ row }: { row: TestPlan }">
        <code class="test-id">{{ row.testId }}</code>
      </template>
    </el-table-column>
    <el-table-column label="类型" min-width="110">
      <template #default="{ row }: { row: TestPlan }">
        {{ typeLabels[row.testType] || row.testType }}
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="100">
      <template #default="{ row }: { row: TestPlan }">
        <el-tag :type="statusTone(row.status)" effect="dark" size="small">
          {{ row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="taskId" label="Task ID" min-width="150">
      <template #default="{ row }: { row: TestPlan }">
        {{ row.taskId || '—' }}
      </template>
    </el-table-column>
    <el-table-column prop="command" label="命令" min-width="200" show-overflow-tooltip>
      <template #default="{ row }: { row: TestPlan }">
        <code>{{ row.command }}</code>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" min-width="160">
      <template #default="{ row }: { row: TestPlan }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.test-id {
  color: var(--color-primary-strong);
  font-weight: 600;
}
</style>
