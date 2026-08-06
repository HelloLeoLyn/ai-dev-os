<script setup lang="ts">
import type { DashboardTimeline } from '../types/dashboard'

defineProps<{
  timeline: DashboardTimeline | null
  loading?: boolean
}>()

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <div v-loading="loading" class="timeline-view">
    <div v-if="timeline" class="timeline-meta">
      <el-tag type="info" effect="dark" size="small">{{ timeline.scopeType }}</el-tag>
      <code class="timeline-scope-id">{{ timeline.scopeId }}</code>
      <span class="timeline-count">{{ timeline.events.length }} 个事件</span>
    </div>

    <el-timeline v-if="timeline && timeline.events.length > 0">
      <el-timeline-item
        v-for="event in timeline.events"
        :key="event.id"
        :timestamp="formatDate(event.occurredAt)"
        placement="top"
      >
        <div class="event">
          <div class="event-header">
            <el-tag type="primary" effect="plain" size="small">{{ event.type }}</el-tag>
            <span v-if="event.toStatus" class="event-status">
              → {{ event.toStatus }}
            </span>
          </div>
          <p v-if="event.summary" class="event-summary">{{ event.summary }}</p>
          <p class="event-meta">
            <span v-if="event.aggregateType">{{ event.aggregateType }}</span>
            <span v-if="event.aggregateId">#{{ event.aggregateId }}</span>
            <span v-if="event.jobId">job: {{ event.jobId }}</span>
            <span v-if="event.executionId">exec: {{ event.executionId }}</span>
          </p>
        </div>
      </el-timeline-item>
    </el-timeline>

    <el-empty
      v-else-if="timeline"
      :description="`${timeline.scopeType} ${timeline.scopeId} 暂无事件`"
    />
  </div>
</template>

<style scoped>
.timeline-view {
  min-height: 8rem;
}

.timeline-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}

.timeline-scope-id {
  color: var(--color-primary-strong);
}

.timeline-count {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.event-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.event-status {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.event-summary {
  margin: 0.4rem 0 0;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin: 0.4rem 0 0;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}
</style>
