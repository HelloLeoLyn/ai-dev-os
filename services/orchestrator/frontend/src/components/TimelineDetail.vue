<script setup lang="ts">
import { computed } from 'vue'

import TimelineEvent from './TimelineEvent.vue'
import type { TimelineEventDTO, UnifiedTimeline } from '../types/timeline'

const props = defineProps<{
  timeline: UnifiedTimeline | null
  loading?: boolean
}>()

const chain = computed<string[]>(() => {
  if (!props.timeline) {
    return []
  }
  const order = ['TASK', 'PLAN_RUN', 'STEP_RUN', 'JOB', 'EXECUTION', 'AUDIT']
  const labels: Record<string, string> = {
    TASK: 'Task',
    PLAN_RUN: 'PlanRun',
    STEP_RUN: 'StepRun',
    JOB: 'Job',
    EXECUTION: 'Execution',
    AUDIT: 'Audit',
  }
  const present = new Set(props.timeline.events.map((event) => event.sourceType))
  return order.filter((type) => present.has(type)).map((type) => labels[type])
})

const scopeLabel = computed(() => {
  switch (props.timeline?.scopeType) {
    case 'TASK':
      return 'Task'
    case 'PLAN_RUN':
      return 'PlanRun'
    case 'JOB':
      return 'Job'
    case 'EXECUTION':
      return 'Execution'
    default:
      return 'Audit'
  }
})

function eventKey(event: TimelineEventDTO, index: number): string {
  return event.eventId || `${event.sourceType}-${event.sourceId}-${index}`
}
</script>

<template>
  <div v-loading="loading" class="timeline-detail">
    <template v-if="timeline">
      <div class="timeline-meta">
        <el-tag type="info" effect="dark" size="small">
          {{ scopeLabel }}
        </el-tag>
        <code class="timeline-scope-id">{{ timeline.scopeId }}</code>
        <span class="timeline-count">{{ timeline.events.length }} 个事件</span>
      </div>

      <div v-if="chain.length > 1" class="timeline-chain">
        <span class="chain-label">执行链路</span>
        <span
          v-for="(node, index) in chain"
          :key="node"
          class="chain-node"
        >
          <template v-if="index > 0">→</template>
          {{ node }}
        </span>
      </div>

      <el-timeline v-if="timeline.events.length > 0">
        <el-timeline-item
          v-for="(event, index) in timeline.events"
          :key="eventKey(event, index)"
          :timestamp="event.timestamp ?? undefined"
          placement="top"
        >
          <TimelineEvent :event="event" />
        </el-timeline-item>
      </el-timeline>

      <el-empty
        v-else
        :description="`${scopeLabel} ${timeline.scopeId} 暂无事件`"
      />
    </template>

    <el-empty v-else-if="!loading" description="输入 ID 查询 Timeline" />
  </div>
</template>

<style scoped>
.timeline-detail {
  min-height: 8rem;
}

.timeline-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.timeline-scope-id {
  color: var(--color-primary-strong);
}

.timeline-count {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.timeline-chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.375rem;
  background: var(--color-surface-muted, #f5f7fa);
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.chain-label {
  font-weight: 700;
}

.chain-node {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
</style>
