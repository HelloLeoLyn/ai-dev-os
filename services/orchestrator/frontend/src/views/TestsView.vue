<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getValidations, startValidation, validationArtifactUrl } from '../api/validations'
import AsyncState from '../components/AsyncState.vue'
import ConsoleCard from '../components/ConsoleCard.vue'
import SectionHeader from '../components/SectionHeader.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { ValidationRun } from '../types/validation'

const runs = ref<ValidationRun[]>([])
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const taskId = ref('')

const running = computed(() => runs.value.filter((run) => run.status === 'RUNNING').length)
const passed = computed(() => runs.value.filter((run) => run.decision === 'PASS').length)
const failed = computed(() => runs.value.filter((run) => run.decision === 'FAIL').length)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = null
  try {
    runs.value = await getValidations()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load validations.'
  } finally {
    loading.value = false
  }
}

async function start(): Promise<void> {
  const value = taskId.value.trim()
  if (!value) return
  submitting.value = true
  try {
    const run = await startValidation(value)
    taskId.value = ''
    ElMessage.success(`Validation completed: ${run.decision ?? run.status}`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Unable to start validation.')
  } finally {
    submitting.value = false
  }
}

function duration(run: ValidationRun): string {
  if (!run.completedAt) return 'Running'
  const ms = new Date(run.completedAt).getTime() - new Date(run.startedAt).getTime()
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

function checkDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}

function command(check: ValidationRun['checks'][number]): string {
  const value = check.metadata.command
  return Array.isArray(value) ? value.join(' ') : typeof value === 'string' ? value : '—'
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <SectionHeader
      eyebrow="Evidence-driven delivery"
      title="Validation Center"
      description="Task-scoped build, test, E2E and CI evidence in one validation result."
    >
      <el-tag type="info" effect="dark">{{ runs.length }} runs</el-tag>
    </SectionHeader>

    <div class="overview-grid">
      <ConsoleCard title="Running"><p class="metric">{{ running }}</p></ConsoleCard>
      <ConsoleCard title="Passed"><p class="metric metric--success">{{ passed }}</p></ConsoleCard>
      <ConsoleCard title="Failed"><p class="metric metric--failed">{{ failed }}</p></ConsoleCard>
      <ConsoleCard title="Recent Runs"><p class="metric">{{ runs.length }}</p></ConsoleCard>
    </div>

    <ConsoleCard title="Run validation" eyebrow="Task entry">
      <el-form inline @submit.prevent="start">
        <el-form-item label="Task ID">
          <el-input v-model="taskId" placeholder="task-…" clearable />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!taskId.trim()">
          Validate
        </el-button>
      </el-form>
    </ConsoleCard>

    <AsyncState :loading="loading" :error="errorMessage" :empty="runs.length === 0" empty-text="No validation runs yet" @retry="load">
      <ConsoleCard title="Recent Runs" eyebrow="Validation history">
        <el-table :data="runs" row-key="validationRunId">
          <el-table-column type="expand">
            <template #default="scope">
              <div class="check-list">
                <article v-for="check in scope.row.checks" :key="check.checkId" class="check-card">
                  <div class="check-card__header">
                    <div><strong>{{ check.name }}</strong><span>{{ check.type }}</span></div>
                    <StatusBadge :status="check.status" size="small" />
                  </div>
                  <p v-if="check.errorMessage" class="check-error">{{ check.errorMessage }}</p>
                  <p v-else>{{ check.summary || 'No summary.' }}</p>
                  <dl>
                    <dt>Duration</dt><dd>{{ checkDuration(check.durationMs) }}</dd>
                    <dt>Provider</dt><dd>{{ check.metadata.provider || '—' }}</dd>
                    <dt>Command</dt><dd><code>{{ command(check) }}</code></dd>
                    <dt>Required</dt><dd>{{ check.required ? 'Yes' : 'No' }}</dd>
                    <dt>Artifacts</dt>
                    <dd>
                      <a v-for="artifactId in check.artifactIds" :key="artifactId" :href="validationArtifactUrl(artifactId)" target="_blank" rel="noreferrer">
                        {{ artifactId }}
                      </a>
                      <span v-if="check.artifactIds.length === 0">—</span>
                    </dd>
                  </dl>
                </article>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="taskId" label="Task" min-width="180" />
          <el-table-column prop="projectId" label="Project" min-width="130" />
          <el-table-column label="Started" min-width="170">
            <template #default="scope">{{ new Date(scope.row.startedAt).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="Duration" min-width="100">
            <template #default="scope">{{ duration(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="Checks" min-width="90">
            <template #default="scope">{{ scope.row.checks.length }}</template>
          </el-table-column>
          <el-table-column label="Result" min-width="110">
            <template #default="scope"><StatusBadge :status="scope.row.decision || scope.row.status" size="small" /></template>
          </el-table-column>
        </el-table>
      </ConsoleCard>
    </AsyncState>
  </section>
</template>

<style scoped>
.overview-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1rem}.metric{margin:0;font-size:2rem;font-weight:750}.metric--success{color:var(--color-success)}.metric--failed,.check-error{color:var(--color-danger)}.check-list{display:grid;gap:.75rem;padding:1rem}.check-card{padding:1rem;border:1px solid var(--color-border);border-radius:var(--radius-md);background:var(--color-surface)}.check-card__header{display:flex;justify-content:space-between;gap:1rem}.check-card__header div{display:flex;flex-direction:column;gap:.2rem}.check-card__header span{color:var(--color-text-muted);font-size:.75rem}.check-card p{white-space:pre-wrap}.check-card dl{display:grid;grid-template-columns:7rem 1fr;gap:.35rem 1rem;margin:.75rem 0 0}.check-card dt{color:var(--color-text-muted)}.check-card dd{margin:0;min-width:0;overflow-wrap:anywhere}.check-card dd a{display:block}@media(max-width:900px){.overview-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:560px){.overview-grid{grid-template-columns:1fr}.check-card dl{grid-template-columns:1fr}.check-card dd{margin-bottom:.5rem}}
</style>
