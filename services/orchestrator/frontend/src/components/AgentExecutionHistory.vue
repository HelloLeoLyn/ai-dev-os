<script setup lang="ts">
import type { AgentExecutionSummary, AgentHistoryDTO } from '../types/agent'

defineProps<{
  history: AgentHistoryDTO | null
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
  <el-card v-loading="loading" shadow="never">
    <template #header>
      <span class="card-title">执行历史</span>
    </template>

    <div v-if="history" class="summary-row">
      <div class="summary-stat">
        <span class="summary-value summary-value--success">{{ history.successCount }}</span>
        <span class="summary-label">成功</span>
      </div>
      <div class="summary-stat">
        <span class="summary-value summary-value--danger">{{ history.failedCount }}</span>
        <span class="summary-label">失败</span>
      </div>
      <div class="summary-stat summary-stat--wide">
        <span class="summary-label">最近错误</span>
        <span :class="{ 'error-text': history.lastError }">
          {{ history.lastError || '—' }}
        </span>
      </div>
    </div>

    <el-table
      v-if="history && history.recentExecutions.length > 0"
      :data="history.recentExecutions"
      size="small"
      stripe
    >
      <el-table-column prop="executionId" label="Execution ID" min-width="170" />
      <el-table-column prop="jobId" label="Job ID" min-width="140" />
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }: { row: AgentExecutionSummary }">
          <el-tag :type="statusType(row.status)" effect="dark" size="small">
            {{ row.status || '—' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" min-width="160">
        <template #default="{ row }: { row: AgentExecutionSummary }">
          {{ formatDate(row.startedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="消息" min-width="180">
        <template #default="{ row }: { row: AgentExecutionSummary }">
          {{ row.message || '—' }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-else-if="history"
      description="暂无执行记录"
    />
  </el-card>
</template>

<style scoped>
.card-title {
  font-weight: 700;
}

.summary-row {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 1rem;
}

.summary-stat {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.summary-stat--wide {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.summary-value {
  font-size: 1.5rem;
  font-weight: 700;
}

.summary-value--success {
  color: var(--color-success);
}

.summary-value--danger {
  color: var(--color-danger);
}

.summary-label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.error-text {
  color: var(--color-danger);
}
</style>
