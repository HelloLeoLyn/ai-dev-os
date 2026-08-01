<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { submitJob } from '../api/jobs'
import { executeTask, getTasks } from '../api/tasks'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { ExecutionResult } from '../types/execution'
import type { TaskDefinition } from '../types/task'

type TaskAction = 'sync' | 'async'

interface ActiveAction {
  taskId: string
  action: TaskAction
}

const router = useRouter()
const tasks = ref<TaskDefinition[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const activeAction = ref<ActiveAction | null>(null)
const actionErrors = ref<Record<string, string>>({})
const syncResults = ref<Record<string, ExecutionResult>>({})

function statusTone(status: string | null): 'neutral' | 'info' | 'success' | 'danger' {
  const normalized = status?.toUpperCase()

  if (normalized === 'SUCCESS' || normalized === 'ACTIVE' || normalized === 'READY') {
    return 'success'
  }
  if (normalized === 'FAILED' || normalized === 'DISABLED') {
    return 'danger'
  }
  if (normalized === 'RUNNING' || normalized === 'PENDING') {
    return 'info'
  }
  return 'neutral'
}

function isTaskBusy(taskId: string): boolean {
  return activeAction.value?.taskId === taskId
}

function actionLabel(taskId: string, action: TaskAction, idleLabel: string): string {
  const active = activeAction.value
  return active?.taskId === taskId && active.action === action ? 'Working…' : idleLabel
}

async function loadTasks(): Promise<void> {
  try {
    tasks.value = await getTasks()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load tasks.'
  } finally {
    loading.value = false
  }
}

async function runSynchronously(task: TaskDefinition): Promise<void> {
  activeAction.value = { taskId: task.id, action: 'sync' }
  delete actionErrors.value[task.id]

  try {
    syncResults.value[task.id] = await executeTask(task.id)
  } catch (error) {
    actionErrors.value[task.id] =
      error instanceof Error ? error.message : 'Unable to execute task.'
  } finally {
    activeAction.value = null
  }
}

async function submitAsJob(task: TaskDefinition): Promise<void> {
  activeAction.value = { taskId: task.id, action: 'async' }
  delete actionErrors.value[task.id]

  try {
    const submission = await submitJob(task.id)
    await router.push(`/jobs/${submission.jobId}`)
  } catch (error) {
    actionErrors.value[task.id] =
      error instanceof Error ? error.message : 'Unable to submit job.'
    activeAction.value = null
  }
}

onMounted(loadTasks)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Task catalog</p>
        <h1>Tasks</h1>
      </div>
      <StatusBadge tone="info">{{ tasks.length }} registered</StatusBadge>
    </header>

    <BaseCard v-if="loading">
      <p class="page-state">Loading tasks…</p>
    </BaseCard>

    <BaseCard v-else-if="errorMessage">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </BaseCard>

    <BaseCard v-else-if="tasks.length === 0">
      <p class="page-state">No tasks registered.</p>
    </BaseCard>

    <div v-else class="task-list">
      <BaseCard v-for="task in tasks" :key="task.id">
        <div class="task-heading">
          <div>
            <p class="task-id">{{ task.id }}</p>
            <h2>{{ task.name || 'Unnamed task' }}</h2>
          </div>
          <StatusBadge :tone="statusTone(task.status)">
            {{ task.status || 'UNKNOWN' }}
          </StatusBadge>
        </div>

        <dl class="task-metadata">
          <div>
            <dt>Agent</dt>
            <dd>{{ task.agentName || '—' }}</dd>
          </div>
          <div>
            <dt>Capabilities</dt>
            <dd class="capability-list">
              <span
                v-for="capability in task.requiredCapabilities || []"
                :key="capability"
                class="capability"
              >
                {{ capability }}
              </span>
              <span v-if="!task.requiredCapabilities?.length">—</span>
            </dd>
          </div>
        </dl>

        <div class="task-actions">
          <BaseButton
            :disabled="isTaskBusy(task.id)"
            @click="runSynchronously(task)"
          >
            {{ actionLabel(task.id, 'sync', 'Run synchronously') }}
          </BaseButton>
          <BaseButton
            class="button--secondary"
            :disabled="isTaskBusy(task.id)"
            @click="submitAsJob(task)"
          >
            {{ actionLabel(task.id, 'async', 'Submit as job') }}
          </BaseButton>
        </div>

        <p v-if="actionErrors[task.id]" class="action-message action-message--error">
          {{ actionErrors[task.id] }}
        </p>

        <div
          v-if="syncResults[task.id]"
          class="sync-result"
          :data-success="syncResults[task.id].success"
        >
          <div class="sync-result__heading">
            <strong>Sync execution result</strong>
            <StatusBadge :tone="syncResults[task.id].success ? 'success' : 'danger'">
              {{ syncResults[task.id].success ? 'SUCCESS' : 'FAILED' }}
            </StatusBadge>
          </div>
          <p>{{ syncResults[task.id].message || 'No result message.' }}</p>
          <pre v-if="syncResults[task.id].output">{{ syncResults[task.id].output }}</pre>
        </div>
      </BaseCard>
    </div>
  </section>
</template>

<style scoped>
.task-list {
  display: grid;
  gap: 1rem;
}

.task-heading,
.task-actions,
.sync-result__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.task-heading h2 {
  margin: 0;
}

.task-id {
  margin: 0 0 0.35rem;
  color: var(--color-primary-strong);
  font-size: 0.8rem;
  font-weight: 700;
}

.task-metadata {
  display: grid;
  grid-template-columns: minmax(10rem, 0.35fr) 1fr;
  gap: 1rem;
  margin: 1.25rem 0;
}

.task-metadata > div {
  padding: 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  background: rgb(11 16 32 / 35%);
}

.task-metadata dt {
  margin-bottom: 0.5rem;
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.task-metadata dd {
  margin: 0;
}

.capability-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.capability {
  padding: 0.2rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-info);
  font-size: 0.75rem;
}

.task-actions {
  justify-content: flex-start;
}

.button--secondary {
  border: 1px solid var(--color-border);
  color: var(--color-text);
  background: var(--color-surface-raised);
}

.sync-result {
  margin-top: 1rem;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-danger);
  border-radius: var(--radius-small);
  background: rgb(11 16 32 / 45%);
}

.sync-result[data-success='true'] {
  border-left-color: var(--color-success);
}

.sync-result p {
  margin-bottom: 0;
}

.sync-result pre {
  max-height: 20rem;
  margin: 1rem 0 0;
  padding: 0.9rem;
  overflow: auto;
  border-radius: var(--radius-small);
  background: #080d19;
  white-space: pre-wrap;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error,
.action-message--error {
  color: var(--color-danger);
}

@media (max-width: 640px) {
  .task-heading,
  .task-actions,
  .task-metadata {
    align-items: stretch;
    flex-direction: column;
    grid-template-columns: 1fr;
  }
}
</style>
