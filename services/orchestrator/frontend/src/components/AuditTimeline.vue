<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import BaseCard from './BaseCard.vue'
import StatusBadge from './StatusBadge.vue'
import type { AuditEvent, ExecutionTimeline, TimelineLoader } from '../types/audit'

const props = defineProps<{
  scopeId: string
  loader: TimelineLoader
  showJobFlow?: boolean
}>()

const timeline = ref<ExecutionTimeline | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const jobStates = ['QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'SUCCESS', 'FAILED'] as const

const observedJobStates = computed(() => {
  const states = new Set<string>()
  for (const event of timeline.value?.events ?? []) {
    if (event.type === 'JOB_SUBMITTED') states.add('QUEUED')
    if (event.type === 'JOB_STARTED' || event.type === 'JOB_RESUBMITTED') states.add('RUNNING')
    if (event.type === 'JOB_WAITING_APPROVAL') states.add('WAITING_APPROVAL')
    if (event.type === 'JOB_SUCCEEDED') states.add('SUCCESS')
    if (event.type === 'JOB_FAILED' || event.type === 'JOB_APPROVAL_REJECTED') states.add('FAILED')
    if (event.fromStatus) states.add(event.fromStatus)
    if (event.toStatus) states.add(event.toStatus)
  }
  return states
})

function eventTone(event: AuditEvent): 'neutral' | 'info' | 'success' | 'danger' {
  if (event.type.includes('FAILED') || event.type.includes('REJECTED') || event.type.includes('DENIED')) {
    return 'danger'
  }
  if (event.type.includes('SUCCEEDED') || event.type.includes('COMPLETED') || event.type.includes('APPROVED')) {
    return 'success'
  }
  if (event.type.includes('STARTED') || event.type.includes('CREATED') || event.type.includes('REQUESTED')) {
    return 'info'
  }
  return 'neutral'
}

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function references(event: AuditEvent): Array<{ label: string; value: string }> {
  const values = [
    ['PlanRun', event.planRunId],
    ['StepRun', event.stepRunId],
    ['Attempt', event.attemptId],
    ['Job', event.jobId],
    ['Execution', event.executionId],
    ['Execution Record', event.executionRecordId],
    ['Approval', event.approvalId],
    ['Invocation', event.invocationId],
  ]
  return values
    .filter((value): value is [string, string] => Boolean(value[1]))
    .map(([label, value]) => ({ label, value }))
}

function hasMetadata(event: AuditEvent): boolean {
  return Object.keys(event.metadata ?? {}).length > 0
}

async function loadTimeline(): Promise<void> {
  loading.value = true
  errorMessage.value = null
  timeline.value = null

  try {
    timeline.value = await props.loader(props.scopeId, 0, 100)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load timeline.'
  } finally {
    loading.value = false
  }
}

watch(() => [props.scopeId, props.loader], loadTimeline, { immediate: true })
</script>

<template>
  <BaseCard v-if="loading">
    <p class="timeline-state">Loading audit timeline…</p>
  </BaseCard>

  <BaseCard v-else-if="errorMessage">
    <p class="timeline-state timeline-state--error">{{ errorMessage }}</p>
    <button class="button retry-button" type="button" @click="loadTimeline">Retry</button>
  </BaseCard>

  <template v-else-if="timeline">
    <BaseCard v-if="showJobFlow" class="job-flow-card">
      <p class="page-eyebrow">State flow</p>
      <div class="job-flow" aria-label="Observed job states">
        <div
          v-for="state in jobStates"
          :key="state"
          class="job-flow__state"
          :class="{ 'job-flow__state--observed': observedJobStates.has(state) }"
        >
          <span class="job-flow__dot"></span>
          <span>{{ state }}</span>
        </div>
      </div>
      <p class="flow-note">Highlighted states were observed in the returned audit events.</p>
    </BaseCard>

    <BaseCard>
      <div class="timeline-heading">
        <div>
          <p class="page-eyebrow">Audit events</p>
          <h2>{{ timeline.count }} event{{ timeline.count === 1 ? '' : 's' }}</h2>
        </div>
        <span class="timeline-window">Latest window: {{ timeline.limit }}</span>
      </div>

      <p v-if="timeline.events.length === 0" class="timeline-state">
        No audit events were found for this ID.
      </p>

      <ol v-else class="timeline-list">
        <li v-for="event in timeline.events" :key="event.id" class="timeline-event">
          <span class="timeline-event__marker"></span>
          <article>
            <div class="timeline-event__heading">
              <StatusBadge :tone="eventTone(event)">{{ event.type }}</StatusBadge>
              <time :datetime="event.occurredAt">{{ formatTime(event.occurredAt) }}</time>
            </div>

            <p v-if="event.summary" class="timeline-event__summary">{{ event.summary }}</p>

            <div v-if="event.fromStatus || event.toStatus" class="status-change">
              <span>{{ event.fromStatus || '—' }}</span>
              <span aria-hidden="true">→</span>
              <strong>{{ event.toStatus || '—' }}</strong>
            </div>

            <dl v-if="references(event).length > 0" class="reference-list">
              <div v-for="reference in references(event)" :key="reference.label">
                <dt>{{ reference.label }}</dt>
                <dd>{{ reference.value }}</dd>
              </div>
            </dl>

            <p v-if="event.actorType || event.actorId" class="actor-line">
              Actor: {{ event.actorType || 'unknown' }}<template v-if="event.actorId"> / {{ event.actorId }}</template>
            </p>

            <details v-if="hasMetadata(event)" class="metadata-details">
              <summary>Metadata</summary>
              <pre>{{ JSON.stringify(event.metadata, null, 2) }}</pre>
            </details>
          </article>
        </li>
      </ol>
    </BaseCard>
  </template>
</template>

<style scoped>
.timeline-state {
  margin: 0;
  color: var(--color-text-muted);
  text-align: center;
}

.timeline-state--error {
  color: var(--color-danger);
}

.retry-button {
  display: block;
  margin: 1rem auto 0;
}

.timeline-heading,
.timeline-event__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.timeline-heading h2 {
  margin: 0;
}

.timeline-window,
.timeline-event time,
.flow-note,
.actor-line {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.timeline-list {
  display: grid;
  gap: 0;
  margin: 1.5rem 0 0;
  padding: 0;
  list-style: none;
}

.timeline-event {
  position: relative;
  padding: 0 0 1.5rem 2rem;
  border-left: 1px solid var(--color-border);
}

.timeline-event:last-child {
  padding-bottom: 0;
  border-left-color: transparent;
}

.timeline-event__marker {
  position: absolute;
  top: 0.35rem;
  left: -0.35rem;
  width: 0.7rem;
  height: 0.7rem;
  border: 2px solid var(--color-primary);
  border-radius: 50%;
  background: var(--color-background);
}

.timeline-event article {
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  background: rgb(11 16 32 / 35%);
}

.timeline-event__summary {
  margin: 0.9rem 0 0;
}

.status-change {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-top: 0.9rem;
  color: var(--color-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
}

.status-change strong {
  color: var(--color-text);
}

.reference-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem 1rem;
  margin: 0.9rem 0 0;
}

.reference-list div {
  display: flex;
  min-width: 0;
  gap: 0.35rem;
}

.reference-list dt {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.reference-list dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
}

.actor-line {
  margin: 0.9rem 0 0;
}

.metadata-details {
  margin-top: 0.9rem;
}

.metadata-details summary {
  cursor: pointer;
  color: var(--color-primary-strong);
  font-size: 0.8rem;
  font-weight: 700;
}

.metadata-details pre {
  max-height: 24rem;
  margin: 0.7rem 0 0;
  padding: 0.8rem;
  overflow: auto;
  border-radius: var(--radius-small);
  background: #080d19;
  font-size: 0.75rem;
  white-space: pre-wrap;
}

.job-flow {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 1rem;
}

.job-flow__state {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 0.55rem;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 700;
  text-align: center;
}

.job-flow__state::before {
  position: absolute;
  top: 0.34rem;
  right: 50%;
  left: -50%;
  height: 1px;
  background: var(--color-border);
  content: '';
}

.job-flow__state:first-child::before {
  display: none;
}

.job-flow__dot {
  z-index: 1;
  width: 0.75rem;
  height: 0.75rem;
  border: 2px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-background);
}

.job-flow__state--observed {
  color: var(--color-text);
}

.job-flow__state--observed .job-flow__dot {
  border-color: var(--color-primary);
  background: var(--color-primary);
}

.flow-note {
  margin: 1rem 0 0;
  text-align: center;
}

@media (max-width: 700px) {
  .timeline-heading,
  .timeline-event__heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .timeline-event {
    padding-left: 1.25rem;
  }

  .job-flow {
    grid-template-columns: 1fr;
    gap: 0.75rem;
  }

  .job-flow__state {
    grid-template-columns: auto 1fr;
    justify-items: start;
    text-align: left;
  }

  .job-flow__state::before {
    display: none;
  }
}
</style>
