<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { JobSummaryDTO } from '../types/dashboard'

defineProps<{
  jobs: JobSummaryDTO[]
  loading?: boolean
}>()

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'RUNNING':
      return 'info'
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
    case 'CANCELLED':
      return 'danger'
    case 'RECOVERY_REQUIRED':
    case 'RETRY_WAIT':
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
  <el-table :data="jobs" v-loading="loading" stripe empty-text="暂无 Job">
    <el-table-column label="Job ID" min-width="180">
      <template #default="{ row }: { row: JobSummaryDTO }">
        <RouterLink class="job-link" :to="`/jobs/${encodeURIComponent(row.jobId)}`">
          {{ row.jobId }}
        </RouterLink>
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="130">
      <template #default="{ row }: { row: JobSummaryDTO }">
        <el-tag :type="statusType(row.status)" effect="dark" size="small">
          {{ row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="priority" label="优先级" width="90" sortable />
    <el-table-column label="Lease 持有者" min-width="130">
      <template #default="{ row }: { row: JobSummaryDTO }">
        {{ row.leaseOwner || '—' }}
      </template>
    </el-table-column>
    <el-table-column label="创建时间" min-width="170">
      <template #default="{ row }: { row: JobSummaryDTO }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="更新时间" min-width="170">
      <template #default="{ row }: { row: JobSummaryDTO }">
        {{ formatDate(row.updatedAt) }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.job-link {
  color: var(--color-primary-strong);
  font-weight: 600;
  text-decoration: none;
}

.job-link:hover {
  text-decoration: underline;
}
</style>
