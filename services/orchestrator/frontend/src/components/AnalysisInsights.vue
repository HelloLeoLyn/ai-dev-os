<script setup lang="ts">
import { computed, reactive, toRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAnalysisInsights } from '../composables/useAnalysisInsights'
import { sortFindings, sortRecommendations } from '../services/analysisPresentation'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'
import type { RecommendationView } from '../types/analysis'
import type { BacklogPriority } from '../types/backlog'
import AnalysisSummary from './AnalysisSummary.vue'
import FindingCard from './FindingCard.vue'
import RecommendationCard from './RecommendationCard.vue'

const props = defineProps<{ task: TaskRecord | null; approval: PlanApprovalRequest | null }>()
const task = toRef(props, 'task')
const analysisTask = computed(() => props.approval?.plan.snapshot.plannerMetadata.taskType === 'project-analysis')
const state = useAnalysisInsights(task, analysisTask)
const visible = computed(() => Boolean(task.value && ['SUCCESS', 'COMPLETED'].includes(task.value.status) && (analysisTask.value || state.response.value?.status !== 'NOT_GENERATED')))
const insight = computed(() => state.response.value?.insight ?? null)
const findings = computed(() => sortFindings(insight.value?.findings ?? []))
const recommendationSources = computed(() => sortRecommendations(insight.value?.recommendations ?? []))
const form = reactive<{ open: boolean; id: string; title: string; description: string; priority?: BacklogPriority }>({ open: false, id: '', title: '', description: '' })
const deferForm = reactive({ open: false, id: '', deferUntil: '', reason: '' })
const recommendation = (id: string): RecommendationView | null => state.recommendations.value[id] ?? null
async function markViewed(id: string) { if (await state.view(id)) ElMessage.success('Recommendation marked as VIEWED.') }
function openDefer(id: string) { Object.assign(deferForm, { open: true, id, deferUntil: '', reason: '' }) }
async function submitDefer() { if (await state.defer(deferForm.id, deferForm.deferUntil || undefined, deferForm.reason.trim() || undefined)) { deferForm.open = false; ElMessage.success('Recommendation deferred.') } }
async function ignore(id: string) { try { const value = await ElMessageBox.prompt('Optional ignore reason', 'Ignore Recommendation'); if (await state.ignore(id, value.value)) ElMessage.success('Recommendation ignored.') } catch { /* cancelled */ } }
function openWorkItem(item: RecommendationView) { Object.assign(form, { open: true, id: item.recommendationId, title: '', description: '', priority: undefined }) }
async function submitWorkItem() { const result = await state.createWorkItem(form.id, { title: form.title.trim() || undefined, description: form.description.trim() || undefined, priority: form.priority }); if (!result) return; form.open = false; ElMessage.success(result.created ? 'Backlog WorkItem created as IDEA.' : 'Existing Backlog WorkItem restored; no duplicate was created.') }
</script>

<template><section v-if="visible" class="analysis-panel" aria-label="Analysis insights">
  <AnalysisSummary :status="state.response.value?.status || 'NOT_GENERATED'" :insight="insight" />
  <el-alert v-if="state.error.value" type="error" :title="state.error.value" show-icon :closable="false"><template #default><el-button size="small" @click="state.load">Retry load</el-button></template></el-alert>
  <el-skeleton v-if="state.loading.value" :rows="4" animated />
  <el-alert v-else-if="!state.response.value || ['NOT_GENERATED','PENDING','RUNNING'].includes(state.response.value.status)" type="info" :closable="false" show-icon title="Analysis Projection is processing"><p>The source Task has completed. Structured Analysis is being generated independently.</p></el-alert>
  <el-alert v-else-if="state.response.value.status === 'FAILED'" type="error" :closable="false" show-icon title="Analysis extraction / projection failed"><p><strong>The source Task itself completed successfully.</strong> Only the derived Analysis Projection failed.</p><p>{{ insight?.errorCode }} · {{ insight?.errorMessage }}</p><el-button size="small" type="danger" plain :loading="state.retrying.value" @click="state.retry">Retry Analysis Projection</el-button></el-alert>
  <template v-else-if="insight">
    <section class="analysis-group"><header><div><p class="eyebrow">Scan findings by severity</p><h2>Findings</h2></div><span>{{ findings.length }} total</span></header><el-empty v-if="!findings.length" description="No findings" :image-size="48" /><FindingCard v-for="finding in findings" :key="finding.findingId" :finding="finding" /></section>
    <section class="analysis-group"><header><div><p class="eyebrow">Decide what happens next</p><h2>Recommendations</h2></div><span>{{ recommendationSources.length }} total</span></header><el-empty v-if="!recommendationSources.length" description="No recommendations" :image-size="48" /><template v-for="source in recommendationSources" :key="source.recommendationId"><RecommendationCard v-if="recommendation(source.recommendationId)" :item="recommendation(source.recommendationId)!" :work-item="state.workItems.value[recommendation(source.recommendationId)!.convertedBacklogItemId || '']" :busy="state.busy.value[source.recommendationId]" @view="markViewed" @defer="openDefer" @ignore="ignore" @create-work-item="openWorkItem" /></template></section>
  </template>
  <el-dialog v-model="deferForm.open" title="Defer Recommendation" width="min(520px, 94vw)"><el-form label-position="top"><el-form-item label="Defer until (optional)"><el-date-picker v-model="deferForm.deferUntil" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="No automatic reactivation" /></el-form-item><el-form-item label="Reason (optional)"><el-input v-model="deferForm.reason" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="deferForm.open = false">Cancel</el-button><el-button type="primary" :loading="state.busy.value[deferForm.id]" @click="submitDefer">Defer</el-button></template></el-dialog>
  <el-dialog v-model="form.open" title="Create Backlog WorkItem" width="min(600px, 94vw)"><el-alert type="info" :closable="false" title="Recommendation → Backlog IDEA"><p>This creates a Backlog IDEA. It does not create or execute a Task.</p></el-alert><el-form label-position="top"><el-form-item label="Title override"><el-input v-model="form.title" /></el-form-item><el-form-item label="Description override"><el-input v-model="form.description" type="textarea" /></el-form-item><el-form-item label="Priority override"><el-select v-model="form.priority" clearable><el-option v-for="value in ['LOW','MEDIUM','HIGH','CRITICAL']" :key="value" :value="value" /></el-select></el-form-item></el-form><template #footer><el-button @click="form.open = false">Cancel</el-button><el-button type="primary" :loading="state.busy.value[form.id]" @click="submitWorkItem">Create WorkItem</el-button></template></el-dialog>
</section></template>

<style scoped>.analysis-panel{display:grid;gap:1rem;margin-top:1rem}.analysis-group{display:grid;gap:.75rem;padding:1rem;border:1px solid var(--color-border);border-radius:var(--radius-small);background:rgb(255 255 255 / 2%)}.analysis-group>header{display:flex;align-items:flex-start;justify-content:space-between;gap:1rem}.analysis-group h2{margin:0}.analysis-group>header>span{color:var(--color-text-muted);font-size:.82rem}.eyebrow{margin:0 0 .3rem;color:var(--color-primary-strong);font-size:.7rem;font-weight:800;letter-spacing:.1em;text-transform:uppercase}@media(max-width:560px){.analysis-group{padding:.75rem}.analysis-group>header{flex-direction:column}}</style>
