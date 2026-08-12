<script setup lang="ts">
import { computed, ref } from 'vue'
import type { PlanApprovalRequest } from '../types/planApproval'
import { canDecide, planApprovalRisk, toolLabel, validRejectReason } from './planApprovalView'

const props = defineProps<{ approval: PlanApprovalRequest; busy: boolean }>()
const emit = defineEmits<{
  approve: [approver: string]
  reject: [approver: string, reason: string]
}>()

const approver = ref('USER')
const rejectReason = ref('')
const rejectError = ref('')
const metadata = computed(() => props.approval.plan.snapshot.plannerMetadata)
const risk = computed(() => planApprovalRisk(props.approval))

function dependencies(stepId: string): string {
  const values = props.approval.plan.dependencies
    .filter((item) => item.toStepId === stepId).map((item) => item.fromStepId)
  return values.length ? values.join(', ') : '无'
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
</script>

<template>
  <section class="approval-detail">
    <h3>Plan Approval</h3>
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="Approval">{{ approval.status }}</el-descriptions-item>
      <el-descriptions-item label="Plan"><code>{{ approval.planId }}</code> v{{ approval.planVersion }}</el-descriptions-item>
      <el-descriptions-item label="Snapshot Hash"><code>{{ approval.planSnapshotHash }}</code></el-descriptions-item>
      <el-descriptions-item label="Policy">{{ approval.plan.snapshot.policyVersion }}</el-descriptions-item>
      <el-descriptions-item label="Project"><code>{{ metadata.projectId || '—' }}</code></el-descriptions-item>
      <el-descriptions-item label="Workspace"><code>{{ metadata.workspaceId || '—' }}</code><br>{{ metadata.workspacePath || '—' }}</el-descriptions-item>
      <el-descriptions-item label="Execution Mode">
        <el-tag :type="metadata.executionMode === 'READ_ONLY' ? 'warning' : 'danger'">{{ metadata.executionMode || '—' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="风险摘要">
        {{ metadata.executionMode === 'READ_ONLY' ? 'READ_ONLY' : 'READ_WRITE' }}；
        写权限 Agent：{{ risk.hasWriteAgent ? '是' : '否' }}；
        WRITE / DANGEROUS Tool：{{ risk.hasWriteTool ? '是' : '否' }}
      </el-descriptions-item>
    </el-descriptions>

    <p class="goal"><strong>Goal：</strong>{{ approval.plan.goal }}</p>
    <el-table :data="approval.plan.steps" size="small" border>
      <el-table-column label="Step" min-width="150"><template #default="{ row }">{{ row.name }}</template></el-table-column>
      <el-table-column label="Agent" min-width="120"><template #default="{ row }">{{ row.assignment.agentName || '无' }}</template></el-table-column>
      <el-table-column label="Capabilities" min-width="150"><template #default="{ row }">{{ row.assignment.requiredCapabilities.join(', ') || '无' }}</template></el-table-column>
      <el-table-column label="Tool" min-width="130"><template #default="{ row }">{{ toolLabel(row) }}</template></el-table-column>
      <el-table-column label="Dependencies" min-width="120"><template #default="{ row }">{{ dependencies(row.id) }}</template></el-table-column>
      <el-table-column prop="failurePolicy" label="Failure" min-width="120" />
      <el-table-column label="Artifacts" min-width="140"><template #default="{ row }">{{ row.expectedArtifacts.map((item: { name: string }) => item.name).join(', ') || '无' }}</template></el-table-column>
    </el-table>

    <el-collapse>
      <el-collapse-item title="Snapshot Agents / Tools / Executors">
        <p>Agents：{{ approval.plan.snapshot.agents.map((item) => `${item.name}(${item.executor})`).join(', ') || '无' }}</p>
        <p>Tools：{{ approval.plan.snapshot.tools.map((item) => `${item.providerId}/${item.name}:${item.access}`).join(', ') || '无' }}</p>
        <p>Executors：{{ approval.plan.snapshot.executors.join(', ') || '无' }}</p>
      </el-collapse-item>
    </el-collapse>

    <div v-if="approval.status === 'PENDING'" class="actions">
      <el-input v-model="approver" placeholder="Approver" :disabled="busy" />
      <el-button type="primary" :loading="busy" :disabled="!canDecide(approval, busy)" @click="emit('approve', approver.trim() || 'USER')">Approve</el-button>
      <el-input v-model="rejectReason" placeholder="Reject reason" :disabled="busy" />
      <el-button type="danger" :loading="busy" :disabled="!canDecide(approval, busy)" @click="submitReject">Reject</el-button>
      <span v-if="rejectError" class="error">{{ rejectError }}</span>
    </div>
  </section>
</template>

<style scoped>
.approval-detail { margin-top: 1rem; }
.goal { white-space: pre-wrap; }
.actions { display: grid; gap: .5rem; margin-top: 1rem; }
.error { color: var(--color-danger); }
</style>
