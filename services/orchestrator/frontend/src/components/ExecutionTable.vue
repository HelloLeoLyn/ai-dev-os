<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { ExecutionSummaryDTO } from '../types/dashboard'

defineProps<{
  executions: ExecutionSummaryDTO[]
  loading?: boolean
}>()

function statusType(status: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
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
  <el-table :data="executions" v-loading="loading" stripe empty-text="暂无 Execution">
    <el-table-column prop="executionId" label="Execution ID" min-width="180" />
    <el-table-column prop="jobId" label="Job ID" min-width="180" />
    <el-table-column label="状态" min-width="120">
      <template #default="{ row }: { row: ExecutionSummaryDTO }">
        <el-tag :type="statusType(row.status)" effect="dark" size="small">
          {{ row.status || '—' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="attempt" label="尝试次数" width="100" sortable />
    <el-table-column label="失败原因" min-width="180">
      <template #default="{ row }: { row: ExecutionSummaryDTO }">
        <span class="failure-reason">{{ row.failureReason || '—' }}</span>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" min-width="170">
      <template #default="{ row }: { row: ExecutionSummaryDTO }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="Timeline" width="120" fixed="right">
      <template #default="{ row }: { row: ExecutionSummaryDTO }">
        <RouterLink
          class="timeline-link"
          :to="`/timeline?id=${encodeURIComponent(row.executionId)}`"
        >
          查看 →
        </RouterLink>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.failure-reason {
  color: var(--color-danger);
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
