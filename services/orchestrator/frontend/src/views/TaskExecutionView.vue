<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
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
import { getJob } from '../api/jobs'
import { approveCodingApproval, getCodingApprovals, rejectCodingApproval } from '../api/codingApprovals'
import type { CodingApprovalRequest } from '../types/codingApproval'
import type { ExecutionJob } from '../types/job'
import { useTaskNotifications } from '../composables/useTaskNotifications'
import { ElMessage } from 'element-plus'
import { getExecutionWorkspace, getExecutionWorkspaceReview, promoteExecutionWorkspace, rejectExecutionWorkspace } from '../api/executions'
import type { ExecutionWorkspaceReview } from '../api/executions'
import type { ExecutionWorkspace } from '../types/executionWorkspace'
import { getRemotePushApprovals, approveRemotePush, rejectRemotePush, pushRemote } from '../api/remotePush'
import type { RemotePushApproval } from '../api/remotePush'
import { getTaskChanges, reviewChange, approveChange, rejectChange, commitChange, retryChangeProjection as retryChangeProjectionApi } from '../api/changes'
import type { ChangeSet } from '../api/changes'
import { getTaskCommits } from '../api/commits'
import type { CommitRecord } from '../api/commits'

const route = useRoute()
const taskId = String(route.params.taskId || '')
const context = useTaskContext()
const executions = useTaskExecution()
const taskTimeline = useTimeline()
const artifactVisible = ref(false)
const selectedArtifact = ref<ExecutionArtifact | null>(null)
const jobs = ref<Record<string, ExecutionJob>>({})
const codingApprovals = ref<Record<string, CodingApprovalRequest>>({})
const executionWorkspace = ref<ExecutionWorkspace | null>(null)
const workspaceReview = ref<ExecutionWorkspaceReview | null>(null)
const promotionBusy = ref(false)
const diffVisible = ref(false)
const approvalBusy = ref(false)
const taskNotifications = useTaskNotifications()
const remotePushApprovals = ref<RemotePushApproval[]>([])
const changes = ref<ChangeSet[]>([])
const commits = ref<CommitRecord[]>([])
const changeRetrying = ref(false)
const monitoredTask = taskNotifications.taskState(taskId)
const pendingRecord = computed(() => executions.records.value.slice().reverse().find(record => record.status === 'WAITING_APPROVAL' && record.approvalId && record.jobId && jobs.value[record.jobId]?.status === 'WAITING_APPROVAL' && codingApprovals.value[record.approvalId]?.status === 'PENDING') ?? null)
const pendingApproval = computed(() => pendingRecord.value?.approvalId ? codingApprovals.value[pendingRecord.value.approvalId] ?? null : null)
const approvalHistory = computed(() => Object.values(codingApprovals.value).filter(approval => approval.taskId === taskId && (!latestJob.value?.id || approval.jobId === latestJob.value.id)).sort((a, b) => a.createdAt.localeCompare(b.createdAt)))
const requiresCodingApproval = computed(() => pendingApproval.value?.status === 'PENDING' || pendingApproval.value?.status === 'APPROVED')
const pendingRemotePush = computed(() => remotePushApprovals.value.find(item => item.status === 'PENDING') ?? null)
const latestRecord = computed(() => executions.records.value.at(-1) ?? null)
const latestJob = computed(() => latestRecord.value?.jobId ? jobs.value[latestRecord.value.jobId] ?? null : null)
const lastFlowEvent = computed(() => taskTimeline.timeline.value?.events.at(-1) ?? null)
const currentPlanRunStatus = computed(() => taskTimeline.timeline.value?.events
  .slice().reverse().find(event => event.sourceType === 'plan-run' || event.eventType.startsWith('PLAN_RUN_'))?.status || 'Unknown')
const workflow = computed(() => context.task.value ? projectTaskWorkflow(context.task.value, context.approval.value?.status, null, 0, 0, requiresCodingApproval.value) : null)

function openArtifact(artifact: ExecutionArtifact): void { selectedArtifact.value = artifact; artifactVisible.value = true }
function isLong(value: string | null): boolean { return (value?.length ?? 0) > 500 }
function reviewValidationStatus(review: ExecutionWorkspaceReview): string {
  if (review.completeness !== 'COMPLETE') return 'INCOMPLETE'
  if (review.errorCode) return `FAILED (${review.errorCode})`
  if (review.diffCheck !== undefined) {
    return review.changedFiles.length === 0 && review.untrackedFiles.length === 0
      ? 'PASS (NO_CHANGES)' : 'PASS'
  }
  return 'UNKNOWN'
}
async function loadExecutionState(): Promise<void> {
  await executions.load(taskId)
  const jobIds = [...new Set(executions.records.value.map(record => record.jobId).filter((id): id is string => Boolean(id)))]
  const loadedJobs = await Promise.all(jobIds.map(id => getJob(id)))
  jobs.value = Object.fromEntries(loadedJobs.map(job => [job.id, job]))
  const loadedApprovals = await getCodingApprovals()
  codingApprovals.value = Object.fromEntries(loadedApprovals.filter(item => item.taskId === taskId).map(item => [item.id, item]))
  executionWorkspace.value = await getExecutionWorkspace(taskId).catch(() => null)
  workspaceReview.value = executionWorkspace.value ? await getExecutionWorkspaceReview(taskId).catch(() => null) : null
  remotePushApprovals.value = await getRemotePushApprovals(taskId).catch(() => [])
  changes.value = await getTaskChanges(taskId).catch(() => [])
  commits.value = await getTaskCommits(taskId).catch(() => [])
}
async function changeAction(id: string, action: 'review'|'approve'|'reject'|'commit'): Promise<void> {
  try {
    if (action === 'review') await reviewChange(id)
    else if (action === 'approve') await approveChange(id)
    else if (action === 'reject') await rejectChange(id)
    else await commitChange(id)
    await loadExecutionState()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : 'Change action failed.') }
}
async function retryChangeProjection(): Promise<void> {
  if (changeRetrying.value) return
  changeRetrying.value = true
  try { await retryChangeProjectionApi(taskId); await loadExecutionState() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : 'ChangeSet projection failed.') }
  finally { changeRetrying.value = false }
}
async function decideRemotePush(action: 'approve' | 'reject'): Promise<void> {
  const approval = pendingRemotePush.value
  if (!approval || approvalBusy.value) return
  approvalBusy.value = true
  try {
    if (action === 'approve') {
      const approved = await approveRemotePush(approval.approvalId)
      await pushRemote(approved.commitId, approved.remote, approved.approvalId)
    }
    else await rejectRemotePush(approval.approvalId)
    await loadExecutionState()
    ElMessage.success(action === 'approve' ? 'Remote push approved. Push execution may proceed.' : 'Remote push rejected.')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : 'Remote push approval failed.') }
  finally { approvalBusy.value = false }
}
async function promoteWorkspace(): Promise<void> {
  if (promotionBusy.value || !['COMPLETED', 'PROMOTION_FAILED'].includes(executionWorkspace.value?.status || '') || workspaceReview.value?.completeness !== 'COMPLETE') return
  promotionBusy.value = true
  try { executionWorkspace.value = await promoteExecutionWorkspace(taskId); workspaceReview.value = await getExecutionWorkspaceReview(taskId); ElMessage.success('Changes promoted to source workspace.') }
  catch (error) {
    await Promise.all([
      getExecutionWorkspace(taskId).then(value => { executionWorkspace.value = value }).catch(() => undefined),
      getExecutionWorkspaceReview(taskId).then(value => { workspaceReview.value = value }).catch(() => undefined),
    ])
    ElMessage.error(error instanceof Error ? error.message : 'Promotion failed.')
  }
  finally { promotionBusy.value = false }
}
async function rejectWorkspace(): Promise<void> {
  if (promotionBusy.value || !['COMPLETED', 'PROMOTION_FAILED'].includes(executionWorkspace.value?.status || '')) return
  promotionBusy.value = true
  try { executionWorkspace.value = await rejectExecutionWorkspace(taskId); workspaceReview.value = await getExecutionWorkspaceReview(taskId); ElMessage.success('Changes rejected. Source workspace was not modified.') }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : 'Reject failed.') }
  finally { promotionBusy.value = false }
}
async function decideCodingApproval(action: 'approve' | 'reject'): Promise<void> {
  const approval = pendingApproval.value
  if (!approval || approvalBusy.value) return
  approvalBusy.value = true
  try {
    if (action === 'approve') await approveCodingApproval(approval.id)
    else await rejectCodingApproval(approval.id)
    ElMessage.success(action === 'approve' ? 'Workspace write approved. Execution will resume.' : 'Workspace write rejected.')
    await Promise.all([context.load(taskId), loadExecutionState(), taskTimeline.load(taskId)])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Coding Approval operation failed.')
  } finally { approvalBusy.value = false }
}
onMounted(async () => {
  await Promise.all([context.load(taskId), loadExecutionState(), taskTimeline.load(taskId)])
  if (context.task.value) taskNotifications.track(context.task.value)
})
watch(monitoredTask, () => { void Promise.all([context.load(taskId), loadExecutionState(), taskTimeline.load(taskId)]) })
function reload(): void { void Promise.all([context.load(taskId), loadExecutionState(), taskTimeline.load(taskId)]) }
</script>

<template><section class="page-stack">
  <AsyncState :loading="context.loading.value || executions.loading.value" :error="context.errorMessage.value || executions.errorMessage.value" :empty="!context.loading.value && !context.task.value" empty-text="Task 不存在" @retry="reload">
  <template v-if="context.task.value">
    <TaskWorkspaceHeader v-if="workflow" :task="context.task.value" :approval="context.approval.value" :workflow="workflow" :next-action-href="requiresCodingApproval ? '#coding-approval' : undefined" />
    <section v-if="pendingApproval && pendingRecord" id="coding-approval" class="approval-action" aria-live="polite">
      <p class="page-eyebrow">Action Required</p><h2>Codex needs permission to modify this workspace.</h2>
      <div class="approval-distinction"><span>Plan Approval: <StatusBadge :status="context.approval.value?.status || 'UNKNOWN'" /></span><span>Coding Approval: <StatusBadge :status="pendingApproval.status" /></span></div>
      <dl class="approval-grid"><div><dt>Workspace</dt><dd>{{ pendingApproval.workspace || pendingRecord.workspace || 'Unknown' }}</dd></div><div><dt>Sandbox / execution mode</dt><dd>{{ pendingApproval.sandbox || pendingRecord.sandbox || 'Unknown' }} / {{ context.task.value.executionMode }}</dd></div><div><dt>Reason</dt><dd>{{ pendingApproval.reason || 'Workspace write requested' }}</dd></div><div><dt>Agent</dt><dd>{{ pendingRecord.agentName || 'Unknown' }}</dd></div><div><dt>Job</dt><dd><TechnicalId :value="pendingRecord.jobId" label="Job" /></dd></div></dl>
      <p>Authority: {{ pendingApproval.authority }} · Operation: {{ pendingApproval.operation }}</p><p>This approval resumes the same Job. It does not approve a new Plan or bypass the workspace-write gate.</p>
      <div class="approval-actions"><el-button type="primary" :loading="approvalBusy" :disabled="pendingApproval.status !== 'PENDING'" @click="decideCodingApproval('approve')">Approve Workspace Write</el-button><el-button :loading="approvalBusy" :disabled="pendingApproval.status !== 'PENDING'" @click="decideCodingApproval('reject')">Reject</el-button></div>
    </section>
    <section v-if="approvalHistory.length" class="approval-history" aria-label="Approval History"><h2>Approval History</h2><ol><li v-for="approval in approvalHistory" :key="approval.id"><strong>{{ approval.operation }}</strong><span>{{ approval.authority }}</span><StatusBadge :status="approval.status" /><TechnicalId :value="approval.id" label="Approval" /></li></ol></section>
    <section v-if="remotePushApprovals.length" class="approval-action remote-push-approval" aria-label="Remote Push Approval"><p class="page-eyebrow">Remote Push</p><h2>{{ pendingRemotePush ? 'Action Required: Approve Remote Push' : 'Remote Push Approval' }}</h2><dl class="approval-grid"><div><dt>Authority</dt><dd>REMOTE</dd></div><div><dt>Operation</dt><dd>PUSH_TASK_BRANCH</dd></div><div><dt>Remote</dt><dd>{{ (pendingRemotePush || remotePushApprovals[0]).remote }}</dd></div><div><dt>Branch</dt><dd><code>{{ (pendingRemotePush || remotePushApprovals[0]).executionBranch }}</code></dd></div><div><dt>Commit</dt><dd><TechnicalId :value="(pendingRemotePush || remotePushApprovals[0]).commitId" label="Commit" /></dd></div><div><dt>Approval Status</dt><dd><StatusBadge :status="(pendingRemotePush || remotePushApprovals[0]).status" /></dd></div></dl><div v-if="pendingRemotePush" class="approval-actions"><el-button type="primary" :loading="approvalBusy" @click="decideRemotePush('approve')">Approve Remote Push</el-button><el-button :loading="approvalBusy" @click="decideRemotePush('reject')">Reject Remote Push</el-button></div></section>
    <div class="execution-overview"><article><span>PlanRun</span><TechnicalId :value="context.task.value.planRunId" label="PlanRun" /></article><article><span>Current StepRun</span><TechnicalId :value="executions.records.value.at(-1)?.stepRunId" label="StepRun" /></article><article><span>Execution history</span><strong>{{ executions.records.value.length }}</strong></article><article><span>Result</span><StatusBadge :status="context.task.value.status" /></article></div>
    <section v-if="executionWorkspace" class="workspace-isolation" aria-label="Workspace Isolation"><h2>Workspace Isolation</h2><p>Execution is isolated from the source workspace.</p><dl><div><dt>Source Workspace</dt><dd>{{ executionWorkspace.sourceWorkspace }}</dd></div><div><dt>Execution Workspace</dt><dd>{{ executionWorkspace.executionWorkspace }}</dd></div><div><dt>Strategy</dt><dd>{{ executionWorkspace.strategy }}</dd></div><div><dt>Base Revision</dt><dd>{{ executionWorkspace.baseRevision }}</dd></div><div><dt>Status</dt><dd><StatusBadge :status="executionWorkspace.status" /></dd></div></dl></section>
    <section v-if="workspaceReview && ['COMPLETED','PROMOTING','PROMOTED','REJECTED','PROMOTION_FAILED'].includes(executionWorkspace?.status || '')" class="review-changes" aria-label="Review Changes">
      <div class="review-header"><div><p class="page-eyebrow">Review Changes</p><h2>{{ executionWorkspace?.status === 'PROMOTED' ? 'Changes promoted' : executionWorkspace?.status === 'REJECTED' ? 'Changes rejected' : 'Review required' }}</h2></div><StatusBadge :status="executionWorkspace?.status || 'UNKNOWN'" /></div>
      <p v-if="executionWorkspace?.status === 'COMPLETED'">Promote is an explicit action and will modify the source workspace.</p><p v-if="executionWorkspace?.status === 'REJECTED'">Source workspace remains unchanged. Execution workspace is retained.</p>
      <dl class="review-grid"><div><dt>Files Changed</dt><dd>{{ workspaceReview.changedFiles.length }}</dd></div><div><dt>Review Completeness</dt><dd>{{ reviewValidationStatus(workspaceReview) }}</dd></div><div><dt>Base Revision</dt><dd>{{ workspaceReview.baseRevision }}</dd></div><div><dt>Tests / Artifacts</dt><dd>{{ workspaceReview.artifacts.length ? workspaceReview.artifacts.join(', ') : 'UNKNOWN' }}</dd></div></dl>
      <p v-if="workspaceReview.completeness !== 'COMPLETE'" class="error">Review is incomplete: {{ workspaceReview.incompleteReasons.join(', ') || 'Unable to display all changes.' }} Promotion is disabled.</p>
      <ul class="changed-files"><li v-for="file in workspaceReview.changedFiles" :key="file">{{ file }}</li></ul>
      <div class="review-actions"><el-button @click="diffVisible = true">View Diff</el-button><el-button v-if="['COMPLETED','PROMOTION_FAILED'].includes(executionWorkspace?.status || '')" type="primary" :loading="promotionBusy" :disabled="workspaceReview.completeness !== 'COMPLETE'" @click="promoteWorkspace">{{ executionWorkspace?.status === 'PROMOTION_FAILED' ? 'Retry Promote to Source Workspace' : 'Promote to Source Workspace' }}</el-button><el-button v-if="['COMPLETED','PROMOTION_FAILED'].includes(executionWorkspace?.status || '')" :loading="promotionBusy" @click="rejectWorkspace">Reject Changes</el-button></div>
      <p v-if="executionWorkspace?.promotionErrorCode" class="error">{{ executionWorkspace.promotionErrorCode }}: {{ executionWorkspace.promotionReason }}</p>
    </section>
    <section v-if="changes.length || commits.length || remotePushApprovals.length" class="delivery-flow" aria-label="Delivery Flow">
      <p class="page-eyebrow">Delivery</p><h2>ChangeSet → Commit → Remote Push</h2>
      <div v-for="change in changes" :key="change.changeId" class="delivery-card">
        <div><strong>ChangeSet</strong> <TechnicalId :value="change.changeId" label="ChangeSet" /><StatusBadge :status="change.status" /></div>
        <p>Branch: <code>{{ change.branch }}</code> · Files Changed: {{ change.filesChanged }}</p>
        <div class="approval-actions">
          <el-button v-if="change.status === 'CREATED'" @click="changeAction(change.changeId, 'review')">Start Review</el-button>
          <el-button v-if="change.status === 'REVIEWING'" type="primary" @click="changeAction(change.changeId, 'approve')">Approve Change</el-button>
          <el-button v-if="change.status === 'REVIEWING'" @click="changeAction(change.changeId, 'reject')">Reject Change</el-button>
          <el-button v-if="change.status === 'APPROVED'" type="primary" @click="changeAction(change.changeId, 'commit')">Commit Change</el-button>
        </div>
      </div>
      <div v-for="commit in commits" :key="commit.commitId" class="delivery-card"><strong>Commit</strong> <TechnicalId :value="commit.commitId" label="Commit" /> <StatusBadge :status="commit.status" /><span> {{ commit.branch }} {{ commit.gitHash || '' }}</span></div>
    </section>
    <section v-else-if="executionWorkspace?.status === 'COMPLETED' && context.task.value.executionMode === 'READ_WRITE'" class="delivery-flow" aria-label="Delivery Flow">
      <p class="page-eyebrow">Delivery</p><h2>ChangeSet not generated</h2><p class="error">Execution succeeded, but the ChangeSet projection is not available.</p>
      <el-button :loading="changeRetrying" @click="retryChangeProjection">Retry ChangeSet projection</el-button>
    </section>
    <section class="flow-summary" aria-label="Execution diagnostics"><article><span>Current Task Status</span><strong>{{ context.task.value.status }}</strong></article><article><span>Current PlanRun Status</span><strong>{{ currentPlanRunStatus }}</strong></article><article><span>Current Job Status</span><strong>{{ latestJob?.status || 'Unknown' }}</strong></article><article><span>Current Approval Status</span><strong>{{ pendingApproval?.status || context.approval.value?.status || 'None' }}</strong></article><article><span>Latest Attempt</span><strong>{{ latestRecord?.status || 'Unknown' }}</strong></article><article><span>Resolved Executor</span><strong>{{ latestRecord?.executorName || 'Unknown' }}</strong></article><article><span>Last Flow Event</span><strong>{{ lastFlowEvent?.eventType || 'Unknown' }}</strong></article><article><span>Blocked Reason</span><strong>{{ latestRecord?.status === 'WAITING_APPROVAL' ? (latestRecord.message || 'Approval required') : 'None' }}</strong></article></section>
    <el-card v-for="(record, index) in executions.records.value" :key="record.id" shadow="never" :class="['record-card', { 'historical-attempt': record !== latestRecord }]">
      <template #header><div class="record-header"><div><p class="page-eyebrow">Attempt {{ index + 1 }} <span v-if="record !== latestRecord">· Historical Attempt</span><span v-else>· Latest Attempt</span></p><h2>{{ record.status }}</h2></div><StatusBadge :status="record.status" /></div></template>
      <dl class="record-grid"><div><dt>Agent</dt><dd>{{ record.agentName || 'Unknown' }}</dd></div><div><dt>Actual Executor</dt><dd>{{ record.executorName || 'Unknown' }}</dd></div><div v-if="record.operation"><dt>Operation</dt><dd>{{ record.operation }}</dd></div><div><dt>Workspace</dt><dd>{{ record.workspace || 'Unknown' }}</dd></div><div><dt>Exit Code</dt><dd>{{ record.exitCode ?? '—' }}</dd></div></dl>
      <section class="result"><h3>Execution Result</h3><p :class="{ error: record.status === 'FAILED' }">{{ record.message || record.output || 'No result captured.' }}</p></section>
      <section v-if="record.artifacts.length" class="artifacts"><h3>Artifacts</h3><div class="artifact-grid"><button v-for="artifact in record.artifacts" :key="`${record.id}-${artifact.name}-${artifact.uri}`" type="button" @click="openArtifact(artifact)"><strong>{{ artifact.name || 'Unnamed artifact' }}</strong><span>{{ artifact.mediaType || artifact.type || 'unknown' }}</span><p v-if="artifact.content && !isLong(artifact.content)">{{ artifact.content }}</p><em v-else-if="artifact.content">Open long content →</em></button></div></section>
      <details class="technical-details"><summary>Technical Details</summary><dl><div><dt>Job</dt><dd><TechnicalId :value="record.jobId" label="Job" /></dd></div><div><dt>PlanRun</dt><dd><TechnicalId :value="record.planRunId" label="PlanRun" /></dd></div><div><dt>StepRun</dt><dd><TechnicalId :value="record.stepRunId" label="StepRun" /></dd></div><div><dt>Attempt</dt><dd><TechnicalId :value="record.attemptId" label="Attempt" /></dd></div></dl></details>
    </el-card>
    <el-empty v-if="!executions.records.value.length" description="当前 Task 暂无 Execution Record" />
  </template>
  </AsyncState>
  <el-drawer v-model="artifactVisible" :title="selectedArtifact?.name || 'Artifact Result'" size="min(760px, 94vw)" append-to-body><dl class="artifact-meta"><div><dt>Type</dt><dd>{{ selectedArtifact?.type || '—' }}</dd></div><div><dt>Media Type</dt><dd>{{ selectedArtifact?.mediaType || '—' }}</dd></div><div><dt>URI</dt><dd><code>{{ selectedArtifact?.uri || '—' }}</code></dd></div></dl><pre class="artifact-content">{{ selectedArtifact?.content || 'No inline content.' }}</pre></el-drawer>
  <el-drawer v-model="diffVisible" title="Execution Workspace Diff" size="min(900px, 96vw)" append-to-body><pre class="artifact-content">{{ workspaceReview?.diff || (workspaceReview?.untrackedFiles?.length ? 'Unable to render untracked file diff.' : 'No changes.') }}</pre></el-drawer>
</section></template>

<style scoped>.back-link { display: inline-block; margin-bottom: 1rem; color: var(--color-primary-strong); text-decoration: none; }.state { text-align: center; color: var(--color-text-muted); }.error { color: var(--color-danger); }.approval-action{padding:1.2rem;border:2px solid var(--color-warning);border-radius:var(--radius-small);scroll-margin-top:1rem}.approval-action h2{margin:.2rem 0 .8rem}.approval-distinction,.approval-actions,.review-actions{display:flex;flex-wrap:wrap;align-items:center;gap:1rem}.approval-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:.75rem}.approval-grid div{padding:.7rem;border:1px solid var(--color-border);border-radius:var(--radius-small)}.execution-overview { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; }.execution-overview article, .record-grid div,.flow-summary article { display: grid; min-width: 0; gap: .4rem; padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }.execution-overview span, dt,.flow-summary span { color: var(--color-text-muted); font-size: .75rem; text-transform: uppercase; }.flow-summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.75rem;margin:1rem 0}.flow-summary strong{overflow-wrap:anywhere}.record-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }.record-header h2 { margin: 0; }.record-header .page-eyebrow span{color:var(--color-primary-strong)}.historical-attempt{opacity:.78;border-left:3px solid var(--color-warning)}.record-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .75rem; margin: 0; }.record-grid dd,.approval-grid dd { margin: 0; overflow-wrap:anywhere; }.review-changes{padding:1.2rem;margin:1rem 0;border:2px solid var(--color-primary-strong);border-radius:var(--radius-small)}.review-header{display:flex;justify-content:space-between;align-items:center;gap:1rem}.review-header h2{margin:0}.review-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.75rem}.review-grid div{padding:.7rem;border:1px solid var(--color-border);border-radius:var(--radius-small)}.review-grid dd{margin:0;overflow-wrap:anywhere}.changed-files{max-height:10rem;overflow:auto;padding-left:1.2rem}.result { margin-top: 1rem; }.result p { white-space: pre-wrap; }.technical-details{margin-top:1rem}.technical-details dl{display:grid;gap:.6rem}.artifact-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: .75rem; }.artifact-grid button { padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); color: inherit; background: rgb(255 255 255 / 2%); cursor: pointer; text-align: left; }.artifact-grid button > * { display: block; margin: .3rem 0; }.artifact-grid span, .artifact-grid em { color: var(--color-text-muted); }.artifact-meta { display: grid; gap: .75rem; }.artifact-meta div { display: grid; grid-template-columns: 8rem 1fr; }.artifact-meta dd { margin: 0; overflow-wrap: anywhere; }.artifact-content { padding: 1rem; overflow: auto; border-radius:var(--radius-small);background:#080d19;white-space:pre-wrap; }@media(max-width:900px){.execution-overview,.record-grid,.flow-summary,.review-grid{grid-template-columns:repeat(2,minmax(0,1fr));}}@media(max-width:560px){.execution-overview,.record-grid,.flow-summary,.review-grid{grid-template-columns:1fr;}}</style>
