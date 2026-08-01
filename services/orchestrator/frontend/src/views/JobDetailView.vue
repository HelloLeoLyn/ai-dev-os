<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { getJob } from '../api/jobs'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { ExecutionJob, JobStatus } from '../types/job'

const route = useRoute()
const job = ref<ExecutionJob | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

function statusTone(status: JobStatus): 'neutral' | 'info' | 'success' | 'danger' {
  switch (status) {
    case 'RUNNING':
      return 'info'
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

async function loadJob(): Promise<void> {
  const jobId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id

  if (!jobId) {
    errorMessage.value = 'Job ID is required.'
    loading.value = false
    return
  }

  try {
    job.value = await getJob(jobId)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load job.'
  } finally {
    loading.value = false
  }
}

onMounted(loadJob)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <RouterLink class="back-link" to="/jobs">← All jobs</RouterLink>
        <p class="page-eyebrow">Job details</p>
        <h1>{{ job?.id || 'Job' }}</h1>
      </div>
      <StatusBadge v-if="job" :tone="statusTone(job.status)">
        {{ job.status }}
      </StatusBadge>
    </header>

    <BaseCard v-if="loading">
      <p class="detail-state">Loading job…</p>
    </BaseCard>

    <BaseCard v-else-if="errorMessage">
      <p class="detail-state detail-state--error">{{ errorMessage }}</p>
    </BaseCard>

    <BaseCard v-else-if="job">
      <dl class="detail-grid">
        <div>
          <dt>Task ID</dt>
          <dd>{{ job.taskId }}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>
            <StatusBadge :tone="statusTone(job.status)">{{ job.status }}</StatusBadge>
          </dd>
        </div>
        <div class="detail-grid__wide">
          <dt>Result summary</dt>
          <dd>{{ job.resultSummary || '—' }}</dd>
        </div>
        <div class="detail-grid__wide">
          <dt>Error message</dt>
          <dd :class="{ 'error-text': job.errorMessage }">
            {{ job.errorMessage || '—' }}
          </dd>
        </div>
        <div class="detail-grid__wide">
          <dt>Execution record</dt>
          <dd>
            <RouterLink
              v-if="job.executionRecordId"
              class="record-link"
              :to="`/execution-records/${job.executionRecordId}`"
            >
              {{ job.executionRecordId }} →
            </RouterLink>
            <span v-else>—</span>
          </dd>
        </div>
      </dl>
    </BaseCard>
  </section>
</template>

<style scoped>
.back-link {
  display: inline-block;
  margin-bottom: 1.25rem;
  color: var(--color-text-muted);
  text-decoration: none;
}

.back-link:hover,
.record-link:hover {
  text-decoration: underline;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin: 0;
}

.detail-grid > div {
  min-width: 0;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  background: rgb(11 16 32 / 35%);
}

.detail-grid__wide {
  grid-column: 1 / -1;
}

.detail-grid dt {
  margin-bottom: 0.55rem;
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.detail-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.record-link {
  color: var(--color-primary-strong);
  font-weight: 700;
  text-decoration: none;
}

.error-text,
.detail-state--error {
  color: var(--color-danger);
}

.detail-state {
  color: var(--color-text-muted);
  text-align: center;
}

@media (max-width: 640px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid__wide {
    grid-column: auto;
  }
}
</style>
