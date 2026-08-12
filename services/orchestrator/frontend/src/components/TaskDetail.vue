<script setup lang="ts">
import { ref, watch } from 'vue'

import { getTimeline } from '../api/timeline'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord, TaskStatus } from '../types/task'
import type { UnifiedTimeline } from '../types/timeline'
import PlanApprovalDetail from './PlanApprovalDetail.vue'
import TimelineDetail from './TimelineDetail.vue'

const props = defineProps<{
  task: TaskRecord | null
  approval: PlanApprovalRequest | null
  approvalLoading: boolean
  decisionBusy: boolean
}>()
defineEmits<{ approve: [approver: string]; reject: [approver: string, reason: string] }>()

const activeTab = ref('overview')
const timeline = ref<UnifiedTimeline | null>(null)
const timelineLoading = ref(false)
const timelineError = ref<string | null>(null)

function statusType(status: TaskStatus): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCESS':
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
    case 'REJECTED':
      return 'danger'
    case 'RUNNING':
      return 'info'
    case 'PLANNING':
    case 'APPROVED':
      return 'warning'
    default:
      return 'info'
  }
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

async function loadTaskTimeline(taskId: string): Promise<void> {
  timelineLoading.value = true
  timelineError.value = null
  try {
    const result = await getTimeline(taskId)
    if (props.task?.taskId === taskId) timeline.value = result
  } catch (error) {
    if (props.task?.taskId === taskId) {
      timeline.value = null
      timelineError.value = error instanceof Error ? error.message : 'Unable to load timeline.'
    }
  } finally {
    if (props.task?.taskId === taskId) timelineLoading.value = false
  }
}

watch(() => props.task?.taskId, (taskId) => {
  timeline.value = null
  timelineError.value = null
  if (taskId && activeTab.value === 'timeline') void loadTaskTimeline(taskId)
})

watch(activeTab, (tab) => {
  const taskId = props.task?.taskId
  if (tab === 'timeline' && taskId && !timeline.value && !timelineLoading.value) {
    void loadTaskTimeline(taskId)
  }
})
</script>

<template>
  <el-card v-if="task" shadow="never" class="task-detail">
    <template #header>
      <header class="task-header">
        <div class="task-header__identity">
          <p class="task-header__eyebrow">Task Detail</p>
          <h2>{{ task.name || task.taskId }}</h2>
          <p v-if="task.description" class="task-header__description">{{ task.description }}</p>
        </div>
        <div class="task-header__tags">
          <el-tag :type="statusType(task.status)" effect="dark">{{ task.status }}</el-tag>
          <el-tag :type="task.executionMode === 'READ_ONLY' ? 'warning' : 'danger'" effect="dark">
            {{ task.executionMode }}
          </el-tag>
        </div>
        <dl class="task-header__meta">
          <div><dt>Planner</dt><dd>—</dd></div>
          <div><dt>Project</dt><dd><code>{{ task.projectId }}</code></dd></div>
          <div><dt>Workspace</dt><dd><code>{{ task.workspaceId || '—' }}</code></dd></div>
        </dl>
      </header>
    </template>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="Overview" name="overview">
        <section class="tab-section">
          <div class="section-heading">
            <div><p class="section-kicker">Summary</p><h3>Task Overview</h3></div>
          </div>
          <el-descriptions :column="2" border size="small" class="overview-grid">
            <el-descriptions-item label="Task ID"><code>{{ task.taskId }}</code></el-descriptions-item>
            <el-descriptions-item label="Project"><code>{{ task.projectId }}</code></el-descriptions-item>
            <el-descriptions-item label="Workspace"><code>{{ task.workspaceId || '—' }}</code></el-descriptions-item>
            <el-descriptions-item label="Planner">—</el-descriptions-item>
            <el-descriptions-item label="Execution Mode">
              <el-tag :type="task.executionMode === 'READ_ONLY' ? 'warning' : 'danger'" size="small">
                {{ task.executionMode }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Approval Status">{{ approval?.status || '—' }}</el-descriptions-item>
            <el-descriptions-item label="PlanRun Status">{{ task.planRunId ? task.status : '—' }}</el-descriptions-item>
            <el-descriptions-item label="Created">{{ formatDate(task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="Updated">{{ formatDate(task.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
          <div class="result-summary" :class="{ 'result-summary--error': task.errorMessage }">
            <span>Result summary</span>
            <strong>{{ task.errorMessage || (['SUCCESS', 'COMPLETED'].includes(task.status) ? '已完成' : '暂无执行结果') }}</strong>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="Plan" name="plan">
        <p v-if="approvalLoading" class="tab-state">正在加载 Plan Approval…</p>
        <PlanApprovalDetail
          v-else-if="approval"
          :approval="approval"
          :task="task"
          :busy="decisionBusy"
          @approve="$emit('approve', $event)"
          @reject="(approver, reason) => $emit('reject', approver, reason)"
        />
        <el-empty v-else description="当前 Task 暂无 Plan Approval" />
      </el-tab-pane>

      <el-tab-pane label="Execution" name="execution">
        <section class="tab-section">
          <div class="section-heading">
            <div><p class="section-kicker">Runtime</p><h3>Execution</h3></div>
          </div>
          <div class="execution-grid">
            <article class="info-card"><span>PlanRun</span><code>{{ task.planRunId || '—' }}</code></article>
            <article class="info-card"><span>StepRun</span><strong>查看 Timeline 中的执行链</strong></article>
            <article class="info-card"><span>Agent</span><strong>{{ approval?.plan.steps.map(step => step.assignment.agentName).filter(Boolean).join(', ') || '—' }}</strong></article>
            <article class="info-card"><span>Artifact</span><strong>{{ approval?.plan.steps.flatMap(step => step.expectedArtifacts).map(item => item.name).join(', ') || '—' }}</strong></article>
          </div>
          <div class="execution-result" :class="{ 'execution-result--error': task.errorMessage }">
            <span>Execution Result</span>
            <p>{{ task.errorMessage || (['SUCCESS', 'COMPLETED'].includes(task.status) ? 'Task execution completed successfully.' : 'No execution result is available yet.') }}</p>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="Timeline" name="timeline">
        <section class="tab-section">
          <div class="section-heading section-heading--timeline">
            <div><p class="section-kicker">Execution Chain</p><h3>Task Timeline</h3></div>
            <el-button size="small" :loading="timelineLoading" @click="loadTaskTimeline(task.taskId)">刷新</el-button>
          </div>
          <p v-if="timelineError" class="tab-state tab-state--error">{{ timelineError }}</p>
          <TimelineDetail :timeline="timeline" :loading="timelineLoading" />
        </section>
      </el-tab-pane>
    </el-tabs>
  </el-card>

  <el-empty v-else description="选择任务查看详情" />
</template>

<style scoped>
.task-detail { width: 100%; min-width: 0; }
.task-header { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 1rem 1.5rem; }
.task-header__identity { min-width: 0; }
.task-header__eyebrow, .section-kicker { margin: 0 0 .35rem; color: var(--color-primary-strong); font-size: .72rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.task-header h2, .section-heading h3 { margin: 0; }
.task-header__description { margin: .5rem 0 0; color: var(--color-text-muted); }
.task-header__tags { display: flex; align-items: flex-start; flex-wrap: wrap; justify-content: flex-end; gap: .5rem; }
.task-header__meta { display: grid; grid-column: 1 / -1; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .75rem; margin: 0; }
.task-header__meta div { min-width: 0; padding: .7rem .85rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }
.task-header__meta dt, .info-card span, .result-summary span, .execution-result span { color: var(--color-text-muted); font-size: .72rem; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
.task-header__meta dd { overflow: hidden; margin: .25rem 0 0; text-overflow: ellipsis; white-space: nowrap; }
.detail-tabs { min-width: 0; }
.tab-section { min-width: 0; padding-top: .4rem; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1rem; }
.overview-grid { width: 100%; }
.result-summary, .execution-result { display: grid; gap: .35rem; margin-top: 1rem; padding: 1rem; border: 1px solid var(--color-border); border-left: 4px solid var(--color-success); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }
.result-summary--error, .execution-result--error { border-left-color: var(--color-danger); }
.execution-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; }
.info-card { display: grid; min-width: 0; gap: .5rem; padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }
.info-card code, .info-card strong { overflow-wrap: anywhere; }
.execution-result p { margin: 0; white-space: pre-wrap; }
.tab-state { padding: 1rem; color: var(--color-text-muted); text-align: center; }
.tab-state--error { color: var(--color-danger); }
@media (max-width: 720px) {
  .task-header, .task-header__meta, .execution-grid { grid-template-columns: minmax(0, 1fr); }
  .task-header__tags { justify-content: flex-start; }
  .task-header__meta { grid-column: auto; }
  :deep(.el-descriptions__body) { overflow-x: auto; }
}
</style>
