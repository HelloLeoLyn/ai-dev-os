<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { getJobs } from '../api/jobs'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { ExecutionJob, JobStatus } from '../types/job'

const statusOptions: JobStatus[] = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED']

const jobs = ref<ExecutionJob[]>([])
const selectedStatus = ref<JobStatus | ''>('')
const loading = ref(true)
const errorMessage = ref<string | null>(null)

function statusTone(status: JobStatus): 'neutral' | 'info' | 'success' | 'danger' {
  switch (status) {
    case 'RUNNING':
      return 'info'
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

async function loadJobs(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    jobs.value = await getJobs({
      status: selectedStatus.value || undefined,
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load jobs.'
  } finally {
    loading.value = false
  }
}

onMounted(loadJobs)
</script>

<template>
  <section class="page-stack">
    <header class="page-header jobs-header">
      <div>
        <p class="page-eyebrow">Execution queue</p>
        <h1>Jobs</h1>
      </div>

      <label class="status-filter">
        <span>Status</span>
        <select v-model="selectedStatus" @change="loadJobs">
          <option value="">All statuses</option>
          <option v-for="status in statusOptions" :key="status" :value="status">
            {{ status }}
          </option>
        </select>
      </label>
    </header>

    <BaseCard>
      <p v-if="loading" class="table-state">Loading jobs…</p>
      <p v-else-if="errorMessage" class="table-state table-state--error">
        {{ errorMessage }}
      </p>

      <div v-else class="table-scroll">
        <table class="jobs-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Task</th>
              <th>Status</th>
              <th>Created</th>
              <th>Started</th>
              <th>Completed</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="jobs.length === 0">
              <td colspan="6" class="table-state">No jobs found.</td>
            </tr>
            <tr v-for="job in jobs" :key="job.id">
              <td>
                <RouterLink class="detail-link" :to="`/jobs/${job.id}`">
                  {{ job.id }}
                </RouterLink>
              </td>
              <td>{{ job.taskId }}</td>
              <td>
                <StatusBadge :tone="statusTone(job.status)">
                  {{ job.status }}
                </StatusBadge>
              </td>
              <td>{{ formatDate(job.createdAt) }}</td>
              <td>{{ formatDate(job.startedAt) }}</td>
              <td>{{ formatDate(job.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </BaseCard>
  </section>
</template>

<style scoped>
.jobs-header {
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

.jobs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  text-align: left;
}

.jobs-table th,
.jobs-table td {
  padding: 0.9rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.jobs-table th {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.jobs-table tbody tr:last-child td {
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

.table-state {
  color: var(--color-text-muted);
  text-align: center;
}

.table-state--error {
  color: var(--color-danger);
}

@media (max-width: 560px) {
  .jobs-header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
