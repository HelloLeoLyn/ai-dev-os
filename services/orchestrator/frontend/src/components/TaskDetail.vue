<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { useTimeline } from '../composables/useTimeline'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'
import StatusBadge from './StatusBadge.vue'
import { planApprovalRisk } from './planApprovalView'

const props = defineProps<{ task: TaskRecord | null; approval: PlanApprovalRequest | null; approvalLoading: boolean }>()
const emit = defineEmits<{ duplicate: [task: TaskRecord] }>()
const advancedVisible = ref(false)
const { timeline, loading: timelineLoading, errorMessage: timelineError, load: loadTimeline } = useTimeline()

const recentEvents = computed(() => [...(timeline.value?.events ?? [])]
  .sort((a, b) => (b.timestamp ?? '').localeCompare(a.timestamp ?? '')).slice(0, 5))
const agents = computed(() => [...new Set(props.approval?.plan.steps.map((step) => step.assignment.agentName).filter(Boolean) ?? [])])
const riskLabel = computed(() => {
  if (!props.approval) return '—'
  const risk = planApprovalRisk(props.approval)
  return risk.readOnly && !risk.hasWriteAgent && !risk.hasWriteTool && !risk.hasDangerousTool && !risk.hasWorkspaceWritePermission ? 'SAFE' : 'REVIEW'
})

watch(() => props.task?.taskId, (id) => { if (id) void loadTimeline(id) }, { immediate: true })

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <el-card v-if="task" shadow="never" class="task-summary">
    <template #header>
      <header class="summary-header">
        <div><p class="eyebrow">Task Summary</p><h2>{{ task.name || 'Untitled Task' }}</h2><p>{{ task.description || '暂无描述' }}</p></div>
        <div class="summary-actions"><div class="summary-tags"><StatusBadge :status="task.status" /><StatusBadge :status="task.executionMode" /></div><el-button size="small" @click="emit('duplicate', task)">复制任务</el-button></div>
      </header>
    </template>

    <div class="summary-grid">
      <article class="summary-card summary-card--wide">
        <div class="card-heading"><div><p class="eyebrow">Context</p><h3>Task</h3></div><el-button text @click="advancedVisible = true">Advanced Information</el-button></div>
        <dl class="compact-facts"><div><dt>Project</dt><dd>{{ task.projectId }}</dd></div><div><dt>Workspace</dt><dd>{{ task.workspaceId || '—' }}</dd></div><div><dt>Created</dt><dd>{{ formatDate(task.createdAt) }}</dd></div><div><dt>Result</dt><dd :class="{ error: task.errorMessage }">{{ task.errorMessage || (['SUCCESS', 'COMPLETED'].includes(task.status) ? 'Completed' : 'Pending') }}</dd></div></dl>
      </article>

      <article class="summary-card">
        <div><p class="eyebrow">Plan</p><h3>{{ approvalLoading ? 'Loading…' : approval?.status || 'Not available' }}</h3></div>
        <dl class="summary-stats"><div><dt>Agent</dt><dd>{{ agents.join(', ') || '—' }}</dd></div><div><dt>Steps</dt><dd>{{ approval?.plan.steps.length ?? '—' }}</dd></div><div><dt>Risk</dt><dd :class="riskLabel === 'SAFE' ? 'safe' : 'warning'">{{ riskLabel }}</dd></div></dl>
        <RouterLink class="detail-link" :to="`/tasks/${encodeURIComponent(task.taskId)}/plan`">查看 Plan →</RouterLink>
      </article>

      <article class="summary-card">
        <div><p class="eyebrow">Execution</p><h3>{{ task.planRunId ? task.status : 'Not started' }}</h3></div>
        <p class="summary-copy">{{ task.errorMessage || (task.planRunId ? 'PlanRun 已创建，可查看执行链与产物。' : '当前尚无执行记录。') }}</p>
        <RouterLink class="detail-link" :to="`/tasks/${encodeURIComponent(task.taskId)}/execution`">查看 Execution →</RouterLink>
      </article>

      <article class="summary-card summary-card--wide">
        <div class="card-heading"><div><p class="eyebrow">Recent Activity</p><h3>Timeline</h3></div><RouterLink class="detail-link" :to="`/timeline?id=${encodeURIComponent(task.taskId)}`">查看完整 Timeline →</RouterLink></div>
        <p v-if="timelineLoading" class="muted">Loading recent events…</p><p v-else-if="timelineError" class="error">{{ timelineError }}</p>
        <ol v-else-if="recentEvents.length" class="recent-events"><li v-for="event in recentEvents" :key="event.eventId"><span class="event-dot"></span><div><strong>{{ event.eventType }}</strong><p>{{ event.message || event.status || '—' }}</p></div><time>{{ formatDate(event.timestamp) }}</time></li></ol>
        <p v-else class="muted">暂无 Timeline 事件。</p>
      </article>
    </div>

    <el-drawer v-model="advancedVisible" title="Advanced Information" size="min(520px, 92vw)" append-to-body>
      <dl class="advanced-list"><div><dt>taskId</dt><dd><code>{{ task.taskId }}</code></dd></div><div><dt>projectId</dt><dd><code>{{ task.projectId }}</code></dd></div><div><dt>workspaceId</dt><dd><code>{{ task.workspaceId || '—' }}</code></dd></div><div><dt>approvalId</dt><dd><code>{{ task.approvalId || '—' }}</code></dd></div><div><dt>planRunId</dt><dd><code>{{ task.planRunId || '—' }}</code></dd></div><div><dt>snapshotHash</dt><dd><code>{{ approval?.planSnapshotHash || '—' }}</code></dd></div></dl>
    </el-drawer>
  </el-card>
  <el-empty v-else description="选择任务查看摘要" />
</template>

<style scoped>
.task-summary { width: 100%; min-width: 0; }
.summary-header, .card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.summary-header h2, .summary-card h3 { margin: 0; }.summary-header p:not(.eyebrow) { margin: .5rem 0 0; color: var(--color-text-muted); }
.summary-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .5rem; }.eyebrow { margin: 0 0 .35rem; color: var(--color-primary-strong); font-size: .72rem; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.summary-actions { display: grid; justify-items: end; gap: .65rem; }
.summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }.summary-card { display: grid; min-width: 0; gap: 1rem; padding: 1rem; border: 1px solid var(--color-border); border-radius: var(--radius-small); background: rgb(255 255 255 / 2%); }.summary-card--wide { grid-column: 1 / -1; }
.compact-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .75rem; margin: 0; }.compact-facts div, .summary-stats div { min-width: 0; }.compact-facts dt, .summary-stats dt, .advanced-list dt { color: var(--color-text-muted); font-size: .75rem; text-transform: uppercase; }.compact-facts dd, .summary-stats dd { margin: .25rem 0 0; overflow-wrap: anywhere; }
.summary-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .75rem; margin: 0; }.summary-copy { margin: 0; color: var(--color-text-muted); }.detail-link { color: var(--color-primary-strong); font-weight: 700; text-decoration: none; }.safe { color: var(--color-success); }.warning, .error { color: var(--color-danger); }
.recent-events { display: grid; gap: .75rem; margin: 0; padding: 0; list-style: none; }.recent-events li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: .75rem; align-items: start; }.recent-events p { margin: .2rem 0 0; color: var(--color-text-muted); }.recent-events time { color: var(--color-text-muted); font-size: .78rem; }.event-dot { width: .55rem; height: .55rem; margin-top: .35rem; border-radius: 50%; background: var(--color-primary); }
.advanced-list { display: grid; gap: 1rem; margin: 0; }.advanced-list div { padding-bottom: 1rem; border-bottom: 1px solid var(--color-border); }.advanced-list dd { margin: .35rem 0 0; overflow-wrap: anywhere; }
@media (max-width: 760px) { .summary-grid, .compact-facts { grid-template-columns: 1fr; }.summary-card--wide { grid-column: auto; }.summary-header, .card-heading { flex-direction: column; }.summary-actions { justify-items: start; }.summary-tags { justify-content: flex-start; }.recent-events li { grid-template-columns: auto minmax(0, 1fr); }.recent-events time { grid-column: 2; } }
</style>
