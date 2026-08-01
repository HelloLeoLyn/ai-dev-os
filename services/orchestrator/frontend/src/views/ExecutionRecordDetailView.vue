<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { getExecutionRecord } from '../api/executions'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type {
  ExecutionRecordDetail,
  ExecutionStatus,
} from '../types/execution'

const route = useRoute()
const record = ref<ExecutionRecordDetail | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

function statusTone(status: ExecutionStatus): 'success' | 'danger' {
  return status === 'SUCCESS' ? 'success' : 'danger'
}

async function loadRecord(): Promise<void> {
  const recordId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id

  if (!recordId) {
    errorMessage.value = 'Execution record ID is required.'
    loading.value = false
    return
  }

  try {
    record.value = await getExecutionRecord(recordId)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Unable to load execution record.'
  } finally {
    loading.value = false
  }
}

onMounted(loadRecord)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <RouterLink class="back-link" to="/execution-records">
          ← All execution records
        </RouterLink>
        <p class="page-eyebrow">Execution details</p>
        <h1>{{ record?.id || 'Execution Record' }}</h1>
      </div>
      <StatusBadge v-if="record" :tone="statusTone(record.status)">
        {{ record.status }}
      </StatusBadge>
    </header>

    <BaseCard v-if="loading">
      <p class="detail-state">Loading execution record…</p>
    </BaseCard>

    <BaseCard v-else-if="errorMessage">
      <p class="detail-state detail-state--error">{{ errorMessage }}</p>
    </BaseCard>

    <template v-else-if="record">
      <BaseCard>
        <dl class="detail-grid">
          <div>
            <dt>Task ID</dt>
            <dd>{{ record.taskId || '—' }}</dd>
          </div>
          <div>
            <dt>Agent</dt>
            <dd>{{ record.agentName || '—' }}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>
              <StatusBadge :tone="statusTone(record.status)">
                {{ record.status }}
              </StatusBadge>
            </dd>
          </div>
          <div>
            <dt>Message</dt>
            <dd>{{ record.message || '—' }}</dd>
          </div>
        </dl>
      </BaseCard>

      <BaseCard>
        <p class="page-eyebrow">Process output</p>
        <h2>Output</h2>
        <pre class="output-block">{{ record.output || 'No output captured.' }}</pre>
      </BaseCard>

      <BaseCard>
        <p class="page-eyebrow">Captured context</p>
        <h2>Execution Report</h2>

        <p v-if="!record.report" class="detail-state">No execution report available.</p>
        <div v-else class="report-stack">
          <dl class="detail-grid">
            <div>
              <dt>Task ID</dt>
              <dd>{{ record.report.taskId || '—' }}</dd>
            </div>
            <div>
              <dt>Agent</dt>
              <dd>{{ record.report.agentName || '—' }}</dd>
            </div>
            <div>
              <dt>Success</dt>
              <dd>{{ record.report.success ? 'Yes' : 'No' }}</dd>
            </div>
            <div>
              <dt>Report output</dt>
              <dd>{{ record.report.output || '—' }}</dd>
            </div>
          </dl>

          <details class="report-details">
            <summary>Before Git Status</summary>
            <pre>{{ record.report.beforeGitStatus || 'No Git status captured.' }}</pre>
          </details>

          <details class="report-details">
            <summary>After Git Diff</summary>
            <pre>{{ record.report.afterGitDiff || 'No Git diff captured.' }}</pre>
          </details>
        </div>
      </BaseCard>
    </template>
  </section>
</template>

<style scoped>
.back-link {
  display: inline-block;
  margin-bottom: 1.25rem;
  color: var(--color-text-muted);
  text-decoration: none;
}

.back-link:hover {
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

.output-block,
.report-details pre {
  max-height: 32rem;
  margin: 1rem 0 0;
  padding: 1rem;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  color: var(--color-text);
  background: #080d19;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.8rem;
  line-height: 1.6;
  white-space: pre-wrap;
}

.report-stack {
  display: grid;
  gap: 1rem;
  margin-top: 1rem;
}

.report-details {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  background: rgb(11 16 32 / 35%);
}

.report-details summary {
  padding: 0.9rem 1rem;
  cursor: pointer;
  color: var(--color-primary-strong);
  font-weight: 700;
}

.report-details pre {
  margin: 0 1rem 1rem;
}

.detail-state {
  color: var(--color-text-muted);
  text-align: center;
}

.detail-state--error {
  color: var(--color-danger);
}

@media (max-width: 640px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
