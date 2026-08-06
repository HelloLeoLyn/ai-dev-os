<script setup lang="ts">
import { computed } from 'vue'

import type { TimelineEventDTO } from '../types/timeline'

const props = defineProps<{
  event: TimelineEventDTO
}>()

const sourceLabel = computed(() => {
  switch (props.event.sourceType) {
    case 'PLAN_RUN':
      return 'PlanRun'
    case 'STEP_RUN':
      return 'StepRun'
    case 'JOB':
      return 'Job'
    case 'EXECUTION':
      return 'Execution'
    case 'TASK':
      return 'Task'
    default:
      return 'Audit'
  }
})

const statusTone = computed<
  'neutral' | 'info' | 'success' | 'danger'
>(() => {
  const text = `${props.event.eventType} ${props.event.status ?? ''}`.toUpperCase()
  if (/(FAILED|ERROR|CANCELLED|ABANDONED|RECOVERY)/.test(text)) {
    return 'danger'
  }
  if (/(SUCCEEDED|SUCCESS|COMPLETED|DONE)/.test(text)) {
    return 'success'
  }
  if (/(STARTED|RUNNING|PLANNING|APPROVED|CREATED|QUEUED)/.test(text)) {
    return 'info'
  }
  return 'neutral'
})

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <div class="timeline-event" :data-tone="statusTone">
    <div class="event-header">
      <el-tag type="primary" effect="plain" size="small">{{ event.eventType }}</el-tag>
      <el-tag
        :type="
          statusTone === 'danger'
            ? 'danger'
            : statusTone === 'success'
              ? 'success'
              : statusTone === 'info'
                ? 'info'
                : 'info'
        "
        effect="dark"
        size="small"
      >
        {{ event.status || '—' }}
      </el-tag>
      <span class="event-source">
        {{ sourceLabel }}
        <code>{{ event.sourceId }}</code>
      </span>
    </div>

    <p v-if="event.message" class="event-message">{{ event.message }}</p>

    <p class="event-meta">
      <span class="event-id">#{{ event.eventId }}</span>
      <span class="event-time">{{ formatDate(event.timestamp) }}</span>
    </p>
  </div>
</template>

<style scoped>
.timeline-event {
  border-left: 3px solid var(--color-border, #e4e7ed);
  padding: 0.5rem 0 0.5rem 0.75rem;
}

.timeline-event[data-tone='danger'] {
  border-left-color: var(--color-danger, #f56c6c);
}

.timeline-event[data-tone='success'] {
  border-left-color: var(--color-success, #67c23a);
}

.timeline-event[data-tone='info'] {
  border-left-color: var(--color-primary, #409eff);
}

.event-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.event-source {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.event-source code {
  color: var(--color-primary-strong);
}

.event-message {
  margin: 0.4rem 0 0;
}

.event-meta {
  display: flex;
  gap: 0.75rem;
  margin: 0.4rem 0 0;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}
</style>
