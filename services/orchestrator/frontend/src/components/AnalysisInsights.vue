<script setup lang="ts">
import { computed, reactive, toRef } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAnalysisInsights } from '../composables/useAnalysisInsights'
import { canCreateRecommendationWorkItem, canDeferRecommendation, canIgnoreRecommendation, canViewRecommendation, confidencePercent, requiresApprovalWarning } from '../services/recommendationUx'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'
import type { RecommendationView } from '../types/analysis'
import type { BacklogPriority } from '../types/backlog'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ task: TaskRecord | null; approval: PlanApprovalRequest | null }>()
const task = toRef(props, 'task')
const analysisTask = computed(() => props.approval?.plan.snapshot.plannerMetadata.taskType === 'project-analysis')
const state = useAnalysisInsights(task, analysisTask)
const visible = computed(() => Boolean(task.value && ['SUCCESS', 'COMPLETED'].includes(task.value.status) && (analysisTask.value || state.response.value?.status !== 'NOT_GENERATED')))
const form = reactive<{ open: boolean; id: string; title: string; description: string; priority?: BacklogPriority }>({ open: false, id: '', title: '', description: '' })
const deferForm = reactive({ open: false, id: '', deferUntil: '', reason: '' })
const recommendation = (id: string): RecommendationView | null => state.recommendations.value[id] ?? null
async function markViewed(id: string) { if (await state.view(id)) ElMessage.success('Recommendation marked as VIEWED.') }
function openDefer(id: string) { Object.assign(deferForm, { open: true, id, deferUntil: '', reason: '' }) }
async function submitDefer() { if (await state.defer(deferForm.id, deferForm.deferUntil || undefined, deferForm.reason.trim() || undefined)) { deferForm.open = false; ElMessage.success('Recommendation deferred.') } }
async function ignore(id: string) { try { const value = await ElMessageBox.prompt('Optional ignore reason', 'Ignore Recommendation'); if (await state.ignore(id, value.value)) ElMessage.success('Recommendation ignored.') } catch { /* cancelled */ } }
function openWorkItem(item: RecommendationView) { Object.assign(form, { open: true, id: item.recommendationId, title: '', description: '', priority: undefined }) }
async function submitWorkItem() { const result = await state.createWorkItem(form.id, { title: form.title.trim() || undefined, description: form.description.trim() || undefined, priority: form.priority }); if (!result) return; form.open = false; ElMessage.success(result.created ? 'Backlog WorkItem created as IDEA.' : 'Existing Backlog WorkItem restored; no duplicate was created.') }
function evidenceLabel(value: { label: string | null; ref: string; uri: string | null; line: number | null }) { return `${value.label || value.ref}${value.uri ? ` · ${value.uri}` : ''}${value.line ? `:${value.line}` : ''}` }
</script>

<template>
  <section v-if="visible" class="analysis-panel" aria-label="Analysis insights">
    <header class="analysis-header"><div><p class="eyebrow">Analysis</p><h3>Findings &amp; Recommendations</h3></div><StatusBadge :status="state.response.value?.status || 'NOT_GENERATED'" /></header>
    <el-alert v-if="state.error.value" type="error" :title="state.error.value" show-icon :closable="false"><template #default><el-button size="small" @click="state.load">Retry load</el-button></template></el-alert>
    <el-skeleton v-if="state.loading.value" :rows="4" animated />
    <el-alert v-else-if="!state.response.value || ['NOT_GENERATED','PENDING','RUNNING'].includes(state.response.value.status)" type="info" :closable="false" show-icon title="Analysis Projection is processing"><p>The source Task has completed. Structured Analysis is being generated independently.</p></el-alert>
    <el-alert v-else-if="state.response.value.status === 'FAILED'" type="error" :closable="false" show-icon title="Analysis extraction / projection failed"><p><strong>The source Task itself completed successfully.</strong> Only the derived Analysis Projection failed.</p><p>{{ state.response.value.insight?.errorCode }} · {{ state.response.value.insight?.errorMessage }}</p><el-button size="small" type="danger" plain :loading="state.retrying.value" @click="state.retry">Retry Analysis Projection</el-button></el-alert>
    <template v-else-if="state.response.value.insight">
      <div class="analysis-group"><h4>Findings</h4><el-empty v-if="!state.response.value.insight.findings.length" description="No findings" :image-size="48" />
        <article v-for="finding in state.response.value.insight.findings" :key="finding.findingId" class="insight-card"><div class="card-title"><h5>{{ finding.title }}</h5><StatusBadge :status="finding.severity" /></div><p>{{ finding.summary }}</p><dl><div><dt>Category</dt><dd>{{ finding.category }}</dd></div><div><dt>Confidence</dt><dd>{{ confidencePercent(finding.confidence) }}</dd></div><div><dt>Scope</dt><dd>{{ finding.scope.join(', ') || '—' }}</dd></div></dl><details v-if="finding.evidenceRefs.length"><summary>Evidence ({{ finding.evidenceRefs.length }})</summary><ul><li v-for="evidence in finding.evidenceRefs" :key="`${evidence.type}:${evidence.ref}`"><StatusBadge :status="evidence.type" size="small" /> {{ evidenceLabel(evidence) }}<code v-if="evidence.contentHash"> · {{ evidence.contentHash.slice(0, 12) }}</code></li></ul></details></article>
      </div>
      <div class="analysis-group"><h4>Recommendations</h4><el-empty v-if="!state.response.value.insight.recommendations.length" description="No recommendations" :image-size="48" />
        <article v-for="source in state.response.value.insight.recommendations" :key="source.recommendationId" class="insight-card recommendation-card"><template v-for="item in [recommendation(source.recommendationId)]" :key="source.recommendationId"><template v-if="item">
          <div class="card-title"><div><h5>{{ item.title }}</h5><p>{{ item.rationale }}</p></div><StatusBadge :status="item.status" /></div>
          <el-alert v-if="requiresApprovalWarning(item)" class="approval-warning" type="warning" :closable="false" show-icon title="需要审批 / Approval Required"><p>READ_WRITE is a suggestion only and grants no execution authority.</p></el-alert>
          <dl><div><dt>Priority</dt><dd>{{ item.priority }}</dd></div><div><dt>Risk</dt><dd>{{ item.risk }}</dd></div><div><dt>Benefit</dt><dd>{{ item.benefit }}</dd></div><div><dt>Confidence</dt><dd>{{ confidencePercent(item.confidence) }}</dd></div><div><dt>Execution Mode</dt><dd>{{ item.suggestedExecutionMode }}</dd></div><div><dt>Scope</dt><dd>{{ item.scope.join(', ') || '—' }}</dd></div><div><dt>Dependencies</dt><dd>{{ item.dependencies.join(', ') || '—' }}</dd></div></dl>
          <section class="next-action"><p class="eyebrow">Recommended Next Action</p><h5>{{ item.recommendedNextAction.title }}</h5><p>{{ item.recommendedNextAction.description }}</p><p><strong>Goal:</strong> {{ item.recommendedNextAction.goal }}</p><p><strong>Estimated Complexity:</strong> {{ item.recommendedNextAction.estimatedComplexity }}</p><strong>Acceptance Criteria</strong><ul><li v-for="criterion in item.recommendedNextAction.acceptanceCriteria" :key="criterion">{{ criterion }}</li></ul></section>
          <div v-if="item.status === 'WORKITEM_CREATED' && item.convertedBacklogItemId" class="work-item-result"><strong>Backlog WorkItem</strong><p>{{ state.workItems.value[item.convertedBacklogItemId]?.title || item.convertedBacklogItemId }} · {{ state.workItems.value[item.convertedBacklogItemId]?.status || 'IDEA' }}</p><RouterLink :to="`/backlog?item=${encodeURIComponent(item.convertedBacklogItemId)}`">Open in Backlog →</RouterLink></div>
          <div class="actions"><el-button v-if="canViewRecommendation(item.status)" size="small" :loading="state.busy.value[item.recommendationId]" @click="markViewed(item.recommendationId)">View</el-button><el-button v-if="canDeferRecommendation(item.status)" size="small" :loading="state.busy.value[item.recommendationId]" @click="openDefer(item.recommendationId)">Defer</el-button><el-button v-if="canIgnoreRecommendation(item.status)" size="small" :loading="state.busy.value[item.recommendationId]" @click="ignore(item.recommendationId)">Ignore</el-button><el-button v-if="canCreateRecommendationWorkItem(item.status)" size="small" type="primary" :loading="state.busy.value[item.recommendationId]" @click="openWorkItem(item)">Create WorkItem</el-button></div>
        </template></template></article>
      </div>
    </template>
    <el-dialog v-model="deferForm.open" title="Defer Recommendation" width="520px"><el-form label-position="top"><el-form-item label="Defer until (optional)"><el-date-picker v-model="deferForm.deferUntil" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="No automatic reactivation" /></el-form-item><el-form-item label="Reason (optional)"><el-input v-model="deferForm.reason" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="deferForm.open = false">Cancel</el-button><el-button type="primary" :loading="state.busy.value[deferForm.id]" @click="submitDefer">Defer</el-button></template></el-dialog>
    <el-dialog v-model="form.open" title="Create Backlog WorkItem" width="600px"><el-alert type="info" :closable="false" title="Creates one existing BacklogItem with status IDEA. It does not create or execute a Task." /><el-form label-position="top"><el-form-item label="Title override"><el-input v-model="form.title" /></el-form-item><el-form-item label="Description override"><el-input v-model="form.description" type="textarea" /></el-form-item><el-form-item label="Priority override"><el-select v-model="form.priority" clearable><el-option v-for="value in ['LOW','MEDIUM','HIGH','CRITICAL']" :key="value" :value="value" /></el-select></el-form-item></el-form><template #footer><el-button @click="form.open = false">Cancel</el-button><el-button type="primary" :loading="state.busy.value[form.id]" @click="submitWorkItem">Create WorkItem</el-button></template></el-dialog>
  </section>
</template>

<style scoped>
.analysis-panel{display:grid;gap:1rem;margin-top:1rem;padding:1rem;border:1px solid var(--color-border);border-radius:var(--radius-small);background:rgb(255 255 255 / 2%)}.analysis-header,.card-title,.actions{display:flex;align-items:flex-start;justify-content:space-between;gap:1rem}.analysis-header h3,.insight-card h5{margin:0}.analysis-group{display:grid;gap:.75rem}.analysis-group>h4{margin:.25rem 0}.insight-card{display:grid;gap:.75rem;padding:1rem;border:1px solid var(--color-border);border-radius:var(--radius-small)}.insight-card p{margin:0;color:var(--color-text-muted)}dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.65rem;margin:0}dt{font-size:.72rem;color:var(--color-text-muted);text-transform:uppercase}dd{margin:.2rem 0 0;overflow-wrap:anywhere}.next-action,.work-item-result{padding:.85rem;border-left:3px solid var(--color-primary);background:rgb(255 255 255 / 3%)}.next-action ul,details ul{margin:.5rem 0 0}.approval-warning{margin:.25rem 0}.actions{justify-content:flex-end;flex-wrap:wrap}.eyebrow{margin:0 0 .35rem;color:var(--color-primary-strong);font-size:.72rem;font-weight:800;letter-spacing:.12em;text-transform:uppercase}@media(max-width:760px){dl{grid-template-columns:1fr}.analysis-header,.card-title{flex-direction:column}.actions{justify-content:flex-start}}
</style>
