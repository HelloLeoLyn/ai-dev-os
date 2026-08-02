<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { getExecutionRecords } from '../api/executions'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type {
  ExecutionRecordSummary,
  ExecutionStatus,
} from '../types/execution'

const statusOptions: ExecutionStatus[] = ['SUCCESS', 'FAILED', 'WAITING_APPROVAL']

const records = ref<ExecutionRecordSummary[]>([])
const selectedStatus = ref<ExecutionStatus | ''>('')
const loading = ref(true)
const errorMessage = ref<string | null>(null)

function statusTone(status: ExecutionStatus): 'success' | 'danger' {
  return status === 'SUCCESS' ? 'success' : 'danger'
}

async function loadRecords(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    records.value = await getExecutionRecords({
      status: selectedStatus.value || undefined,
    })
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Unable to load execution records.'
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <section class="page-stack">
    <header class="page-header records-header">
      <div>
        <p class="page-eyebrow">Execution history</p>
        <h1>Execution Records</h1>
      </div>

      <label class="status-filter">
        <span>Status</span>
        <select v-model="selectedStatus" @change="loadRecords">
          <option value="">All statuses</option>
          <option v-for="status in statusOptions" :key="status" :value="status">
            {{ status }}
          </option>
        </select>
      </label>
    </header>

    <BaseCard>
      <p v-if="loading" class="table-state">Loading execution records…</p>
      <p v-else-if="errorMessage" class="table-state table-state--error">
        {{ errorMessage }}
      </p>

      <div v-else class="table-scroll">
        <table class="records-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Task</th>
              <th>Agent</th>
              <th>Status</th>
              <th>Message</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="records.length === 0">
              <td colspan="5" class="table-state">No execution records found.</td>
            </tr>
            <tr v-for="record in records" :key="record.id">
              <td>
                <RouterLink
                  class="detail-link"
                  :to="`/execution-records/${record.id}`"
                >
                  {{ record.id }}
                </RouterLink>
              </td>
              <td>{{ record.taskId || '—' }}</td>
              <td>{{ record.agentName || '—' }}</td>
              <td>
                <StatusBadge :tone="statusTone(record.status)">
                  {{ record.status }}
                </StatusBadge>
              </td>
              <td class="message-cell">{{ record.message || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </BaseCard>
  </section>
</template>

<style scoped>
.records-header {
  align-items: flex-end;
}

.status-filter {
  display: grid;
  gap: 0.4rem;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.status-filter select {
  min-width: 12rem;
  padding: 0.65rem 2rem 0.65rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  color: var(--color-text);
  background: var(--color-surface-raised);
}

.table-scroll {
  overflow-x: auto;
}

.records-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  text-align: left;
}

.records-table th,
.records-table td {
  padding: 0.9rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  vertical-align: top;
}

.records-table th {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.records-table tbody tr:last-child td {
  border-bottom: 0;
}

.detail-link {
  color: var(--color-primary-strong);
  font-weight: 700;
  text-decoration: none;
}

.detail-link:hover {
  text-decoration: underline;
}

.message-cell {
  min-width: 14rem;
  overflow-wrap: anywhere;
}

.table-state {
  color: var(--color-text-muted);
  text-align: center;
}

.table-state--error {
  color: var(--color-danger);
}

@media (max-width: 560px) {
  .records-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
