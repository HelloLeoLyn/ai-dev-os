<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useTaskContext } from '../composables/useTaskContext'
import { useTaskExecution } from '../composables/useTaskExecution'
import { useTimeline } from '../composables/useTimeline'
import type { ExecutionArtifact } from '../types/execution'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import TechnicalId from '../components/TechnicalId.vue'
import TaskWorkspaceHeader from '../components/TaskWorkspaceHeader.vue'
import { projectTaskWorkflow } from '../services/taskWorkflow'

const route = useRoute()
const taskId = String(route.params.taskId || '')
const context = useTaskContext()
const executions = useTaskExecution()
const taskTimeline = useTimeline()
const artifactVisible = ref(false)
const selectedArtifact = ref<ExecutionArtifact | null>(null)
const stepRuns = computed(() => taskTimeline.timeline.value?.events.filter((event) => event.sourceType === 'STEP_RUN') ?? [])
const workflow = computed(() => context.task.value ? projectTaskWorkflow(context.task.value, context.approval.value?.status) : null)

function openArtifact(artifact: ExecutionArtifact): void { selectedArtifact.value = artifact; artifactVisible.value = true }
function isLong(value: string | null): boolean { return (value?.length ?? 0) > 500 }
onMounted(() => Promise.all([context.load(taskId), executions.load(taskId), taskTimeline.load(taskId)]))
function reload(): void { void Promise.all([context.load(taskId), executions.load(taskId), taskTimeline.load(taskId)]) }
</script>

<template><section class="page-stack">
  <AsyncState :loading="context.loading.value || executions.loading.value" :error="context.errorMessage.value || executions.errorMessage.value" :empty="!context.loading.value && !context.task.value" empty-text="Task 不存在" @retry="reload">
  <template v-if="context.task.value">
    <TaskWorkspaceHeader v-if="workflow" :task="context.task.value" :approval="context.approval.value" :workflow="workflow" />
    <div class="execution-overview"><article><span>PlanRun</span><TechnicalId :value="context.task.value.planRunId" label="PlanRun" /></article><article><span>StepRun</span><strong>{{ stepRuns.length || '—' }}</strong></article><article><span>Executions</span><strong>{{ executions.records.value.length }}</strong></article><article><span>Result</span><StatusBadge :status="context.task.value.status" /></article></div>
    <el-card v-for="record in executions.records.value" :key="record.id" shadow="never" class="record-card">
      <template #header><div class="record-header"><div><p class="page-eyebrow">Execution Record</p><h2>{{ record.status }}</h2></div><StatusBadge :status="record.status" /></div></template>
      <dl class="record-grid"><div><dt>Job</dt><dd><TechnicalId :value="record.jobId" label="Job" /></dd></div><div><dt>Agent</dt><dd>{{ record.agentName || '—' }}</dd></div><div><dt>Executor</dt><dd>—</dd></div><div><dt>Exit Code / Retry</dt><dd>{{ record.exitCode ?? '—' }} / —</dd></div></dl>
      <section class="result"><h3>Execution Result</h3><p :class="{ error: record.status === 'FAILED' }">{{ record.message || record.output || 'No result captured.' }}</p></section>
      <section v-if="record.artifacts.length" class="artifacts"><h3>Artifacts</h3><div class="artifact-grid"><button v-for="artifact in record.artifacts" :key="`${record.id}-${artifact.name}-${artifact.uri}`" type="button" @click="openArtifact(artifact)"><strong>{{ artifact.name || 'Unnamed artifact' }}</strong><span>{{ artifact.mediaType || artifact.type || 'unknown' }}</span><p v-if="artifact.content && !isLong(artifact.content)">{{ artifact.content }}</p><em v-else-if="artifact.content">Open long content →</em></button></div></section>
    </el-card>
    <el-empty v-if="!executions.records.value.length" description="当前 Task 暂无 Execution Record" />
  </template>
  </AsyncState>
  <el-drawer v-model="artifactVisible" :title="selectedArtifact?.name || 'Artifact Result'" size="min(760px, 94vw)" append-to-body><dl class="artifact-meta"><div><dt>Type</dt><dd>{{ selectedArtifact?.type || '—' }}</dd></div><div><dt>Media Type</dt><dd>{{ selectedArtifact?.mediaType || '—' }}</dd></div><div><dt>URI</dt><dd><code>{{ selectedArtifact?.uri || '—' }}</code></dd></div></dl><pre class="artifact-content">{{ selectedArtifact?.content || 'No inline content.' }}</pre></el-drawer>
</section></template>

<style scoped>.back-link { display: inline-block; margin-bottom: 1rem; color: var(--color-primary-strong); text-decoration: none; }.state { text-align: center; color: var(--color-text-muted); }.error { color: var(--color-danger); }.execution-overview { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; }.execution-overview article, .record-grid div { display: grid; min-width: 0; gap: .4rem; padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }.execution-overview span, dt { color: var(--color-text-muted); font-size: .75rem; text-transform: uppercase; }.record-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }.record-header h2 { margin: 0; }.record-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .75rem; margin: 0; }.record-grid dd { margin: 0; overflow-wrap: anywhere; }.result { margin-top: 1rem; }.result p { white-space: pre-wrap; }.artifact-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: .75rem; }.artifact-grid button { padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); color: inherit; background: rgb(255 255 255 / 2%); cursor: pointer; text-align: left; }.artifact-grid button > * { display: block; margin: .3rem 0; }.artifact-grid span, .artifact-grid em { color: var(--color-text-muted); }.artifact-meta { display: grid; gap: .75rem; }.artifact-meta div { display: grid; grid-template-columns: 8rem 1fr; }.artifact-meta dd { margin: 0; overflow-wrap: anywhere; }.artifact-content { padding: 1rem; overflow: auto; border-radius: var(--radius-small); background: #080d19; white-space: pre-wrap; }@media(max-width:900px){.execution-overview,.record-grid{grid-template-columns:repeat(2,minmax(0,1fr));}}@media(max-width:560px){.execution-overview,.record-grid{grid-template-columns:1fr;}}</style>
