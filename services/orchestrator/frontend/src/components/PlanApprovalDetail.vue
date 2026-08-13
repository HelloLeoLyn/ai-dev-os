<script setup lang="ts">
import { computed, ref } from 'vue'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'
import { canDecide, isLongPlanGoal, planApprovalRisk, toolLabel, validRejectReason } from './planApprovalView'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ approval: PlanApprovalRequest; task: TaskRecord; busy: boolean }>()
const emit = defineEmits<{
  approve: [approver: string]
  reject: [approver: string, reason: string]
}>()

const approver = ref('USER')
const rejectReason = ref('')
const rejectError = ref('')
const approveDialogVisible = ref(false)
const snapshotVisible = ref(false)
const goalExpanded = ref(false)
const metadata = computed(() => props.approval.plan.snapshot.plannerMetadata)
const risk = computed(() => planApprovalRisk(props.approval))
const isSafeReadOnly = computed(() => risk.value.readOnly && !risk.value.hasWriteAgent &&
  !risk.value.hasWriteTool && !risk.value.hasDangerousTool && !risk.value.hasWorkspaceWritePermission)
const assignedAgents = computed(() => [...new Set(props.approval.plan.steps
  .map((step) => step.assignment.agentName).filter((name): name is string => Boolean(name)))])
const goalIsLong = computed(() => isLongPlanGoal(props.approval.plan.goal))

function dependencies(stepId: string): string[] {
  return props.approval.plan.dependencies
    .filter((item) => item.toStepId === stepId).map((item) => item.fromStepId)
}

function submitReject(): void {
  const reason = rejectReason.value.trim()
  if (!validRejectReason(reason)) {
    rejectError.value = 'Reject 必须填写原因。'
    return
  }
  rejectError.value = ''
  emit('reject', approver.value.trim() || 'USER', reason)
}

function confirmApprove(): void {
  approveDialogVisible.value = false
  emit('approve', approver.value.trim() || 'USER')
}
</script>

<template>
  <section class="approval-detail">
    <section class="plan-summary">
      <div><span>Approval Status</span><StatusBadge :status="approval.status" /></div>
      <div><span>Plan Version</span><strong>v{{ approval.planVersion }}</strong></div>
      <div><span>Execution Mode</span><StatusBadge :status="metadata.executionMode || task.executionMode" /></div>
      <div><span>Agent</span><strong>{{ assignedAgents.join(', ') || '无' }}</strong></div>
      <div><span>Risk</span><StatusBadge :status="isSafeReadOnly ? 'SAFE' : 'REVIEW'" /></div>
    </section>

    <section class="plan-goal">
      <p class="section-kicker">Goal · AI 准备做什么</p>
      <p :class="{ 'goal-content--collapsed': goalIsLong && !goalExpanded }">{{ approval.plan.goal }}</p>
      <el-button v-if="goalIsLong" text type="primary" @click="goalExpanded = !goalExpanded">{{ goalExpanded ? '收起 Goal' : '展开完整 Goal' }}</el-button>
    </section>

    <section class="risk-panel" :class="isSafeReadOnly ? 'risk-panel--safe' : 'risk-panel--warning'">
      <div class="risk-panel__heading">
        <div>
          <p class="section-kicker">Security · 是否安全</p>
          <h3>{{ isSafeReadOnly ? 'Read-only execution verified' : 'Execution risk requires review' }}</h3>
        </div>
        <StatusBadge :status="metadata.executionMode || 'UNKNOWN'" />
      </div>
      <div class="security-rows">
        <div><span>Mode</span><StatusBadge :status="metadata.executionMode || 'UNKNOWN'" size="small" /></div>
        <div><span>Allowed</span><el-tag type="success" size="small">read workspace</el-tag><el-tag type="success" size="small">read-only agent</el-tag></div>
        <div><span>Blocked</span><el-tag :type="risk.hasWriteTool ? 'danger' : 'info'" size="small">WRITE Tool: {{ risk.hasWriteTool ? 'detected' : 'none' }}</el-tag><el-tag :type="risk.hasDangerousTool ? 'danger' : 'info'" size="small">DANGEROUS: {{ risk.hasDangerousTool ? 'detected' : 'none' }}</el-tag><el-tag :type="risk.hasWorkspaceWritePermission ? 'danger' : 'info'" size="small">workspace-write: {{ risk.hasWorkspaceWritePermission ? 'granted' : 'denied' }}</el-tag></div>
      </div>
    </section>

    <section class="steps-section">
      <div class="section-heading">
        <div><p class="section-kicker">Execution Plan · 如何执行</p><h3>Plan Steps</h3></div>
        <span class="step-count">{{ approval.plan.steps.length }} steps</span>
      </div>
      <div class="step-list">
        <article v-for="(step, index) in approval.plan.steps" :key="step.id" class="step-card">
          <header class="step-card__header">
            <span>Step {{ index + 1 }}</span>
            <h4>{{ step.name }}</h4>
          </header>
          <p v-if="step.description" class="step-description">{{ step.description }}</p>
          <dl class="step-facts">
            <div><dt>Agent</dt><dd>{{ step.assignment.agentName || '无' }}</dd></div>
            <div><dt>Capabilities</dt><dd>{{ step.assignment.requiredCapabilities.join(', ') || '无' }}</dd></div>
            <div><dt>Tool</dt><dd>{{ toolLabel(step) }}</dd></div>
            <div><dt>Failure Policy</dt><dd><code>{{ step.failurePolicy }}</code></dd></div>
          </dl>
          <div class="step-card__footer">
            <div>
              <strong>Expected Artifact</strong>
              <ul v-if="step.expectedArtifacts.length">
                <li v-for="artifact in step.expectedArtifacts" :key="`${artifact.name}-${artifact.type}`">
                  <code>{{ artifact.name }}</code><span>{{ artifact.type }}</span><span>{{ artifact.required ? 'required' : 'optional' }}</span>
                </li>
              </ul>
              <span v-else>无</span>
            </div>
            <div>
              <strong>Dependencies</strong>
              <p>{{ dependencies(step.id).join(', ') || '无' }}</p>
            </div>
          </div>
        </article>
      </div>
    </section>

    <div class="snapshot-trigger">
      <div><p class="section-kicker">Advanced</p><strong>Plan Snapshot</strong></div>
      <el-button @click="snapshotVisible = true">Open Snapshot</el-button>
    </div>

    <el-drawer v-model="snapshotVisible" title="Advanced Plan Snapshot" size="min(640px, 92vw)" append-to-body>
        <el-collapse accordion>
          <el-collapse-item title="Policy" name="policy">
            <dl class="snapshot-list"><div><dt>Policy Version</dt><dd><code>{{ approval.plan.snapshot.policyVersion }}</code></dd></div><div><dt>Snapshot Hash</dt><dd><code>{{ approval.planSnapshotHash }}</code></dd></div></dl>
          </el-collapse-item>
          <el-collapse-item title="Agents" name="agents">
            <div v-if="approval.plan.snapshot.agents.length" class="snapshot-items"><article v-for="agent in approval.plan.snapshot.agents" :key="agent.name"><strong>{{ agent.name }}</strong><span>{{ agent.executor }}</span><span>{{ agent.capabilities.join(', ') || 'no capabilities' }}</span><code>{{ agent.permissionLevel || 'no permission' }}</code></article></div><p v-else>无</p>
          </el-collapse-item>
          <el-collapse-item title="Tools" name="tools">
            <div v-if="approval.plan.snapshot.tools.length" class="snapshot-items"><article v-for="tool in approval.plan.snapshot.tools" :key="`${tool.providerId}-${tool.name}`"><strong>{{ tool.providerId }}/{{ tool.name }}</strong><el-tag :type="tool.access === 'READ_ONLY' ? 'success' : 'danger'" size="small">{{ tool.access }}</el-tag></article></div><p v-else>无</p>
          </el-collapse-item>
          <el-collapse-item title="Executors" name="executors"><p>{{ approval.plan.snapshot.executors.join(', ') || '无' }}</p></el-collapse-item>
          <el-collapse-item title="Technical IDs" name="ids">
            <dl class="snapshot-list"><div><dt>Task ID</dt><dd><code>{{ task.taskId }}</code></dd></div><div><dt>Plan ID</dt><dd><code>{{ approval.planId }}</code></dd></div><div><dt>Approval ID</dt><dd><code>{{ approval.id }}</code></dd></div><div><dt>PlanRun ID</dt><dd><code>{{ task.planRunId || '—' }}</code></dd></div></dl>
          </el-collapse-item>
          <el-collapse-item title="Raw metadata" name="metadata"><pre class="raw-metadata">{{ JSON.stringify(metadata, null, 2) }}</pre></el-collapse-item>
        </el-collapse>
    </el-drawer>

    <section class="approval-actions">
      <div class="approval-actions__status">
        <span>Approval</span>
        <strong>{{ approval.status }}</strong>
      </div>
      <template v-if="approval.status === 'PENDING'">
        <div class="approval-fields">
          <el-input v-model="approver" placeholder="Approver" :disabled="busy" />
          <el-input v-model="rejectReason" placeholder="Reject reason（Reject 时必填）" :disabled="busy" />
        </div>
        <span v-if="rejectError" class="error">{{ rejectError }}</span>
        <div class="approval-buttons">
          <el-button type="danger" plain :loading="busy" :disabled="!canDecide(approval, busy)" @click="submitReject">Reject</el-button>
          <el-button type="primary" :loading="busy" :disabled="!canDecide(approval, busy)" @click="approveDialogVisible = true">Approve Plan</el-button>
        </div>
      </template>
      <p v-else class="decision-summary">{{ approval.decision || approval.status }} by {{ approval.approver || '—' }}<template v-if="approval.rejectionReason"> · {{ approval.rejectionReason }}</template></p>
    </section>

    <el-dialog v-model="approveDialogVisible" title="Approve Plan" width="min(520px, 92vw)" append-to-body>
      <p class="dialog-intro">You are approving this plan for execution.</p>
      <div class="approval-confirm-mode" :class="{ 'approval-confirm-mode--write': metadata.executionMode !== 'READ_ONLY' }">{{ metadata.executionMode || 'UNKNOWN' }}</div>
      <dl class="confirm-list">
        <div><dt>Task</dt><dd>{{ task.name || task.taskId }}</dd></div>
        <div><dt>Workspace</dt><dd><code>{{ task.workspaceId || metadata.workspacePath || '—' }}</code></dd></div>
        <div><dt>Execution Mode</dt><dd>{{ metadata.executionMode || task.executionMode }}</dd></div>
        <div><dt>Agent</dt><dd>{{ assignedAgents.join(', ') || '无' }}</dd></div>
        <div><dt>Steps</dt><dd>{{ approval.plan.steps.length }}</dd></div>
      </dl>
      <template #footer>
        <el-button :disabled="busy" @click="approveDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="busy" @click="confirmApprove">Confirm Approval</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.approval-detail { display: grid; gap: 1.25rem; padding-top: .4rem; }
.section-kicker { margin: 0 0 .35rem; color: var(--color-primary-strong); font-size: .72rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.plan-summary, .plan-goal, .risk-panel, .step-card, .approval-actions { padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }
.plan-summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: .75rem; }
.plan-summary div { display: grid; min-width: 0; gap: .4rem; }
.plan-summary span { color: var(--color-text-muted); font-size: .75rem; text-transform: uppercase; }
.plan-goal > p:nth-child(2) { margin: 0; font-size: 1rem; line-height: 1.6; white-space: pre-wrap; }
.goal-content--collapsed { display: -webkit-box; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 5; }
.plan-meta, .risk-panel__heading, .section-heading, .approval-buttons { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.plan-meta { margin-top: 1rem; color: var(--color-text-muted); font-size: .85rem; }
.risk-panel { border-left: 4px solid var(--color-warning); }
.risk-panel--safe { border-left-color: var(--color-success); background: rgb(103 194 58 / 7%); }
.risk-panel--warning { background: rgb(245 108 108 / 7%); }
.risk-panel h3, .section-heading h3 { margin: 0; }
.security-rows { display: grid; gap: .65rem; margin-top: 1rem; }
.security-rows > div { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem; }
.security-rows > div > span:first-child { width: 5rem; color: var(--color-text-muted); font-size: .8rem; }
.step-count { color: var(--color-text-muted); font-size: .85rem; }
.step-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr)); gap: 1rem; }
.step-card { min-width: 0; }
.step-card__header { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: .75rem; padding-bottom: .75rem; border-bottom: 1px solid var(--color-border); }
.step-card__header span { display: inline-flex; padding: .25rem .55rem; border-radius: 999px; color: var(--color-primary-strong); background: rgb(124 156 255 / 12%); font-size: .75rem; font-weight: 800; }
.step-card__header h4 { margin: 0; overflow-wrap: anywhere; }
.step-description { color: var(--color-text-muted); white-space: pre-wrap; }
.step-facts, .snapshot-list, .confirm-list { display: grid; gap: .65rem; margin: 1rem 0 0; }
.step-facts div, .snapshot-list div, .confirm-list div { display: grid; grid-template-columns: 8rem minmax(0, 1fr); gap: .75rem; }
.step-facts dt, .snapshot-list dt, .confirm-list dt { color: var(--color-text-muted); font-size: .8rem; }
.step-facts dd, .snapshot-list dd, .confirm-list dd { margin: 0; overflow-wrap: anywhere; }
.step-card__footer { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(8rem, .6fr); gap: 1rem; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--color-border); }
.step-card__footer ul { display: grid; gap: .4rem; margin: .6rem 0 0; padding: 0; list-style: none; }
.step-card__footer li { display: flex; flex-wrap: wrap; gap: .5rem; color: var(--color-text-muted); font-size: .8rem; }
.step-card__footer p { margin: .6rem 0 0; }
.snapshot-trigger { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }
.snapshot-items { display: grid; gap: .5rem; }
.snapshot-items article { display: flex; align-items: center; flex-wrap: wrap; gap: .65rem; padding: .65rem; border-radius: var(--radius-small); background: rgb(255 255 255 / 3%); }
.snapshot-items article > span { color: var(--color-text-muted); }
.raw-metadata { max-height: 22rem; margin: 0; padding: .75rem; overflow: auto; border-radius: var(--radius-small); background: #080d19; white-space: pre-wrap; }
.approval-actions { position: sticky; bottom: 0; z-index: 2; display: grid; gap: .75rem; border-top: 3px solid var(--color-primary); background: var(--el-bg-color-overlay); box-shadow: 0 -10px 24px rgb(0 0 0 / 18%); }
.approval-actions__status { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; }
.approval-actions__status span { color: var(--color-text-muted); text-transform: uppercase; }
.approval-actions__status strong { color: var(--color-primary-strong); }
.approval-fields { display: grid; grid-template-columns: minmax(10rem, .6fr) minmax(14rem, 1.4fr); gap: .75rem; }
.approval-buttons { justify-content: flex-end; }
.error { color: var(--color-danger); }
.decision-summary { margin: 0; color: var(--color-text-muted); }
.dialog-intro { margin-top: 0; }
.approval-confirm-mode { padding: .85rem; border: 1px solid var(--color-success); border-radius: var(--radius-small); color: var(--color-success); background: rgb(103 194 58 / 8%); font-size: 1.1rem; font-weight: 900; text-align: center; }
.approval-confirm-mode--write { border-color: var(--color-danger); color: var(--color-danger); background: rgb(245 108 108 / 8%); }
@media (max-width: 700px) {
  .plan-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .approval-fields, .step-card__footer { grid-template-columns: minmax(0, 1fr); }
  .risk-panel__heading, .plan-meta { align-items: flex-start; flex-direction: column; }
  .step-facts div, .snapshot-list div, .confirm-list div { grid-template-columns: minmax(0, 1fr); gap: .2rem; }
}
</style>
