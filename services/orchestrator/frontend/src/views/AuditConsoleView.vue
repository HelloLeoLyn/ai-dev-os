<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import BaseCard from '../components/BaseCard.vue'

const router = useRouter()
const scope = ref<'plan-runs' | 'executions' | 'jobs'>('plan-runs')
const timelineId = ref('')

function openTimeline(): void {
  const id = timelineId.value.trim()
  if (id) void router.push(`/audit/${scope.value}/${encodeURIComponent(id)}`)
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Audit Console</p>
        <h1>Execution timelines</h1>
        <p class="page-description">
          Inspect lifecycle and related audit events using an existing runtime ID.
        </p>
      </div>
    </header>

    <BaseCard>
      <form class="timeline-form" @submit.prevent="openTimeline">
        <label>
          Timeline type
          <select v-model="scope">
            <option value="plan-runs">PlanRun</option>
            <option value="executions">Execution</option>
            <option value="jobs">Job</option>
          </select>
        </label>

        <label class="timeline-form__id">
          Runtime ID
          <input v-model="timelineId" type="text" required placeholder="Enter an ID" />
        </label>

        <button class="button" type="submit" :disabled="!timelineId.trim()">
          Open timeline
        </button>
      </form>
    </BaseCard>

    <div class="scope-grid">
      <BaseCard>
        <p class="page-eyebrow">PlanRun</p>
        <h2>Plan lifecycle</h2>
        <p>Approval, StepRun, Job, and Execution events correlated to a PlanRun.</p>
      </BaseCard>
      <BaseCard>
        <p class="page-eyebrow">Execution</p>
        <h2>Agent and tools</h2>
        <p>Agent lifecycle, Tool/MCP calls, approvals, and execution status changes.</p>
      </BaseCard>
      <BaseCard>
        <p class="page-eyebrow">Job</p>
        <h2>Job state flow</h2>
        <p>Queued, running, waiting approval, success, and failure transitions.</p>
      </BaseCard>
    </div>
  </section>
</template>

<style scoped>
.page-description,
.scope-grid p {
  color: var(--color-text-muted);
}

.timeline-form {
  display: flex;
  align-items: end;
  gap: 1rem;
}

.timeline-form label {
  display: grid;
  gap: 0.5rem;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 700;
}

.timeline-form__id {
  flex: 1;
}

.timeline-form input,
.timeline-form select {
  min-height: 2.5rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  color: var(--color-text);
  background: var(--color-background);
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.scope-grid h2,
.scope-grid p {
  margin: 0;
}

.scope-grid p:last-child {
  margin-top: 0.75rem;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .timeline-form {
    align-items: stretch;
    flex-direction: column;
  }

  .scope-grid {
    grid-template-columns: 1fr;
  }
}
</style>
