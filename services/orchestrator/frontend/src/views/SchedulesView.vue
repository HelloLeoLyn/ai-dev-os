<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import {
  createSchedule,
  deleteSchedule,
  getSchedules,
} from '../api/schedules'
import BaseButton from '../components/BaseButton.vue'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { CreateScheduleRequest, ScheduledTask } from '../types/schedule'

interface ScheduleForm {
  taskId: string
  cron: string
  zoneId: string
  enabled: boolean
}

const defaultZoneId = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

const schedules = ref<ScheduledTask[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)
const formError = ref<string | null>(null)
const submitting = ref(false)
const deletingId = ref<string | null>(null)
const form = reactive<ScheduleForm>({
  taskId: '',
  cron: '',
  zoneId: defaultZoneId,
  enabled: true,
})

function resetForm(): void {
  form.taskId = ''
  form.cron = ''
  form.zoneId = defaultZoneId
  form.enabled = true
}

async function loadSchedules(): Promise<void> {
  try {
    schedules.value = await getSchedules()
  } catch (error) {
    loadError.value =
      error instanceof Error ? error.message : 'Unable to load schedules.'
  } finally {
    loading.value = false
  }
}

async function submitSchedule(): Promise<void> {
  formError.value = null
  submitting.value = true

  const request: CreateScheduleRequest = {
    id: `schedule-${crypto.randomUUID()}`,
    taskId: form.taskId.trim(),
    cron: form.cron.trim(),
    zoneId: form.zoneId.trim(),
    enabled: form.enabled,
  }

  try {
    const created = await createSchedule(request)
    schedules.value = [...schedules.value, created].sort((left, right) =>
      left.id.localeCompare(right.id),
    )
    resetForm()
  } catch (error) {
    formError.value =
      error instanceof Error ? error.message : 'Unable to create schedule.'
  } finally {
    submitting.value = false
  }
}

async function removeSchedule(schedule: ScheduledTask): Promise<void> {
  deletingId.value = schedule.id
  loadError.value = null

  try {
    await deleteSchedule(schedule.id)
    schedules.value = schedules.value.filter((item) => item.id !== schedule.id)
  } catch (error) {
    loadError.value =
      error instanceof Error ? error.message : 'Unable to delete schedule.'
  } finally {
    deletingId.value = null
  }
}

onMounted(loadSchedules)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Automation</p>
        <h1>Schedules</h1>
      </div>
      <StatusBadge tone="info">{{ schedules.length }} configured</StatusBadge>
    </header>

    <BaseCard>
      <div class="section-heading">
        <div>
          <p class="page-eyebrow">New automation</p>
          <h2>Create Schedule</h2>
        </div>
      </div>

      <form class="schedule-form" @submit.prevent="submitSchedule">
        <label>
          <span>Task ID</span>
          <input v-model.trim="form.taskId" required placeholder="task-id" />
        </label>

        <label>
          <span>Cron</span>
          <input v-model.trim="form.cron" required placeholder="0 */5 * * * *" />
        </label>

        <label>
          <span>Zone ID</span>
          <input v-model.trim="form.zoneId" required placeholder="Asia/Shanghai" />
        </label>

        <label class="enabled-field">
          <input v-model="form.enabled" type="checkbox" />
          <span>Enabled</span>
        </label>

        <BaseButton type="submit" :disabled="submitting">
          {{ submitting ? 'Creating…' : 'Create schedule' }}
        </BaseButton>
      </form>

      <p v-if="formError" class="form-message form-message--error">
        {{ formError }}
      </p>
    </BaseCard>

    <BaseCard>
      <div class="section-heading">
        <div>
          <p class="page-eyebrow">Registered triggers</p>
          <h2>Schedule List</h2>
        </div>
      </div>

      <p v-if="loading" class="table-state">Loading schedules…</p>
      <p v-else-if="loadError" class="table-state table-state--error">
        {{ loadError }}
      </p>

      <div v-else class="table-scroll">
        <table class="schedules-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Task</th>
              <th>Cron</th>
              <th>Enabled</th>
              <th>Zone</th>
              <th><span class="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="schedules.length === 0">
              <td colspan="6" class="table-state">No schedules configured.</td>
            </tr>
            <tr v-for="schedule in schedules" :key="schedule.id">
              <td class="schedule-id">{{ schedule.id }}</td>
              <td>{{ schedule.taskId }}</td>
              <td><code>{{ schedule.cron }}</code></td>
              <td>
                <StatusBadge :tone="schedule.enabled ? 'success' : 'neutral'">
                  {{ schedule.enabled ? 'ENABLED' : 'DISABLED' }}
                </StatusBadge>
              </td>
              <td>{{ schedule.zoneId }}</td>
              <td class="action-cell">
                <BaseButton
                  class="button--danger"
                  :disabled="deletingId !== null"
                  @click="removeSchedule(schedule)"
                >
                  {{ deletingId === schedule.id ? 'Deleting…' : 'Delete' }}
                </BaseButton>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </BaseCard>
  </section>
</template>

<style scoped>
.section-heading {
  margin-bottom: 1.25rem;
}

.section-heading h2 {
  margin: 0;
}

.schedule-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto auto;
  align-items: end;
  gap: 1rem;
}

.schedule-form label:not(.enabled-field) {
  display: grid;
  gap: 0.4rem;
}

.schedule-form label > span {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.schedule-form input:not([type='checkbox']) {
  width: 100%;
  min-height: 2.5rem;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-small);
  color: var(--color-text);
  background: var(--color-surface-raised);
}

.enabled-field {
  display: flex;
  align-items: center;
  min-height: 2.5rem;
  gap: 0.5rem;
}

.enabled-field input {
  width: 1rem;
  height: 1rem;
  accent-color: var(--color-primary);
}

.table-scroll {
  overflow-x: auto;
}

.schedules-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  text-align: left;
}

.schedules-table th,
.schedules-table td {
  padding: 0.9rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  vertical-align: middle;
  white-space: nowrap;
}

.schedules-table th {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.schedules-table tbody tr:last-child td {
  border-bottom: 0;
}

.schedule-id {
  color: var(--color-primary-strong);
  font-weight: 700;
}

.action-cell {
  text-align: right;
}

.button--danger {
  color: var(--color-danger);
  background: rgb(255 123 139 / 12%);
}

.table-state {
  color: var(--color-text-muted);
  text-align: center;
}

.table-state--error,
.form-message--error {
  color: var(--color-danger);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1100px) {
  .schedule-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .schedule-form {
    grid-template-columns: 1fr;
  }
}
</style>
