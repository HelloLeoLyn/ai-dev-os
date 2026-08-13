<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getDashboardSummary } from '../api/dashboard'
import { getExecutionRecord, getExecutionRecords } from '../api/executions'
import { getPlanApproval } from '../api/planApprovals'
import { getProjects } from '../api/projects'
import { getTasks } from '../api/tasks'
import { getTimeline } from '../api/timeline'
import StatusBadge from '../components/StatusBadge.vue'
import { baseDashboardModel, executionResultSummary, latestExecution, type DashboardConsoleModel } from '../services/dashboardConsole'
import { planApprovalRisk } from '../components/planApprovalView'
import type { DashboardSummaryDTO } from '../types/dashboard'
import type { ExecutionRecordDetail } from '../types/execution'
import type { TaskRecord } from '../types/task'

const model = ref<DashboardConsoleModel | null>(null)
const systemSummary = ref<DashboardSummaryDTO | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const partialErrors = ref<string[]>([])

const stats = computed(() => model.value ? [
  { label: 'Running Tasks', value: model.value.counts.running, tone: 'info' },
  { label: 'Pending Approvals', value: model.value.counts.pending, tone: 'warning' },
  { label: 'Failed Tasks', value: model.value.counts.failed, tone: 'danger' },
  { label: 'Successful Tasks', value: model.value.counts.successful, tone: 'success' },
  { label: 'Active Projects', value: model.value.counts.activeProjects, tone: 'neutral' },
] : [])

function formatDate(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function shorten(value: string | null | undefined, length = 180): string {
  if (!value) return 'No result captured.'
  return value.length > length ? `${value.slice(0, length)}…` : value
}

async function executionFor(task: TaskRecord): Promise<ExecutionRecordDetail | null> {
  const summaries = await getExecutionRecords({ taskId: task.taskId })
  const records = await Promise.all(summaries.map((item) => getExecutionRecord(item.id)))
  return latestExecution(records)
}

async function enrichDashboard(base: DashboardConsoleModel): Promise<void> {
  const pendingResults = await Promise.allSettled(base.pending.map(async (item) => ({
    ...item, approval: item.task.approvalId ? await getPlanApproval(item.task.approvalId) : null,
  })))
  base.pending = pendingResults.flatMap((result) => result.status === 'fulfilled' ? [result.value] : [])
  partialErrors.value.push(...pendingResults.flatMap((result) => result.status === 'rejected'
    ? [result.reason instanceof Error ? result.reason.message : String(result.reason)] : []))

  for (const key of ['failures', 'successes'] as const) {
    const results = await Promise.allSettled(base[key].map(async (item) => ({
      ...item, execution: await executionFor(item.task),
    })))
    base[key] = results.map((result, index) => result.status === 'fulfilled'
      ? result.value : { ...base[key][index], execution: null })
    partialErrors.value.push(...results.flatMap((result) => result.status === 'rejected'
      ? [result.reason instanceof Error ? result.reason.message : String(result.reason)] : []))
  }

  const recentTasks = [...base.running, ...base.failures.map((item) => item.task), ...base.successes.map((item) => item.task)].slice(0, 3)
  const timelines = await Promise.allSettled(recentTasks.map(async (task) => ({ task, timeline: await getTimeline(task.taskId) })))
  base.activity = timelines.flatMap((result) => result.status === 'fulfilled'
    ? result.value.timeline.events.map((event) => ({ ...event, taskId: result.value.task.taskId, taskName: result.value.task.name || result.value.task.taskId })) : [])
    .sort((a, b) => (b.timestamp || '').localeCompare(a.timestamp || '')).slice(0, 6)
}

async function loadDashboard(): Promise<void> {
  loading.value = true
  errorMessage.value = null
  partialErrors.value = []
  try {
    const [tasks, projects, summaryResult] = await Promise.all([
      getTasks(), getProjects(), getDashboardSummary().catch((error) => {
        partialErrors.value.push(error instanceof Error ? error.message : String(error)); return null
      }),
    ])
    model.value = baseDashboardModel(tasks, projects)
    systemSummary.value = summaryResult
    await enrichDashboard(model.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load dashboard data.'
  } finally { loading.value = false }
}

function riskLabel(approval: DashboardConsoleModel['pending'][number]['approval']): string {
  if (!approval) return 'UNKNOWN'
  const risk = planApprovalRisk(approval)
  return risk.readOnly && !risk.hasWriteAgent && !risk.hasWriteTool && !risk.hasDangerousTool && !risk.hasWorkspaceWritePermission ? 'SAFE' : 'REVIEW'
}

onMounted(loadDashboard)
</script>

<template><section class="page-stack dashboard-console">
  <header class="page-header"><div><p class="page-eyebrow">Operations Console</p><h1>Dashboard</h1><p class="page-description">AI Dev OS 现在正在做什么？</p></div><el-button :loading="loading" @click="loadDashboard">Refresh</el-button></header>
  <el-card v-if="loading" shadow="never"><el-skeleton :rows="6" animated /></el-card>
  <el-alert v-else-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
  <template v-else-if="model">
    <el-alert v-if="partialErrors.length" title="部分运行详情暂时不可用" :description="partialErrors[0]" type="warning" show-icon />
    <section class="overview-grid" aria-label="Task overview"><article v-for="stat in stats" :key="stat.label" :data-tone="stat.tone"><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong></article></section>

    <div class="dashboard-grid">
      <el-card shadow="never" class="dashboard-panel dashboard-panel--wide"><template #header><div class="panel-heading"><div><p class="page-eyebrow">Live Work</p><h2>Running Now</h2></div><span>{{ model.running.length }}</span></div></template>
        <div v-if="model.running.length" class="task-rows"><article v-for="task in model.running" :key="task.taskId"><div><strong>{{ task.name || task.taskId }}</strong><small>{{ task.projectId }} · {{ formatDate(task.createdAt) }}</small></div><StatusBadge :status="task.executionMode" size="small" /><StatusBadge :status="task.status" size="small" /><div class="row-actions"><RouterLink :to="`/tasks/${encodeURIComponent(task.taskId)}`">Task</RouterLink><RouterLink :to="`/tasks/${encodeURIComponent(task.taskId)}/execution`">Execution</RouterLink></div></article></div>
        <el-empty v-else description="当前没有正在运行的 Task" :image-size="64" />
      </el-card>

      <el-card shadow="never" class="dashboard-panel"><template #header><div class="panel-heading"><div><p class="page-eyebrow">Human Decision</p><h2>Pending Approval</h2></div><span>{{ model.pending.length }}</span></div></template>
        <div v-if="model.pending.length" class="compact-list"><article v-for="item in model.pending" :key="item.task.taskId"><div><strong>{{ item.task.name || item.task.taskId }}</strong><small>{{ item.task.projectId }} · {{ formatDate(item.task.createdAt) }}</small></div><div class="inline-tags"><StatusBadge :status="item.task.executionMode" size="small" /><StatusBadge :status="riskLabel(item.approval)" :tone="riskLabel(item.approval) === 'SAFE' ? 'safe' : 'danger'" size="small" /></div><RouterLink :to="`/tasks/${encodeURIComponent(item.task.taskId)}/plan`">Review Plan →</RouterLink></article></div><el-empty v-else description="没有等待审批的 Plan" :image-size="56" />
      </el-card>

      <el-card shadow="never" class="dashboard-panel"><template #header><div class="panel-heading"><div><p class="page-eyebrow">Attention</p><h2>Recent Failures</h2></div><span>{{ model.failures.length }}</span></div></template>
        <div v-if="model.failures.length" class="compact-list"><article v-for="item in model.failures" :key="item.task.taskId"><div><strong>{{ item.task.name || item.task.taskId }}</strong><small>{{ formatDate(item.execution?.completedAt || item.task.updatedAt) }}</small></div><p class="failure-copy">{{ shorten(executionResultSummary(item.execution) || item.task.errorMessage) }}</p><div class="row-actions"><RouterLink :to="`/tasks/${encodeURIComponent(item.task.taskId)}/execution`">Execution</RouterLink><RouterLink :to="`/timeline?id=${encodeURIComponent(item.task.taskId)}`">Timeline</RouterLink></div></article></div><el-empty v-else description="近期没有失败 Task" :image-size="56" />
      </el-card>

      <el-card shadow="never" class="dashboard-panel"><template #header><div class="panel-heading"><div><p class="page-eyebrow">Completed</p><h2>Recent Success</h2></div><span>{{ model.successes.length }}</span></div></template>
        <div v-if="model.successes.length" class="compact-list"><article v-for="item in model.successes" :key="item.task.taskId"><div><strong>{{ item.task.name || item.task.taskId }}</strong><small>{{ formatDate(item.execution?.completedAt || item.task.updatedAt) }} · {{ item.execution?.artifacts.length ?? 0 }} artifacts</small></div><p>{{ shorten(executionResultSummary(item.execution)) }}</p><RouterLink :to="`/tasks/${encodeURIComponent(item.task.taskId)}/execution`">查看结果 →</RouterLink></article></div><el-empty v-else description="暂无成功 Task" :image-size="56" />
      </el-card>

      <el-card shadow="never" class="dashboard-panel"><template #header><div class="panel-heading"><div><p class="page-eyebrow">System</p><h2>Recent Activity</h2></div><RouterLink to="/timeline">Full Timeline →</RouterLink></div></template>
        <ol v-if="model.activity.length" class="activity-list"><li v-for="event in model.activity" :key="`${event.taskId}-${event.eventId}`"><span></span><div><strong>{{ event.eventType }}</strong><p>{{ event.taskName }} · {{ event.message || event.status || '—' }}</p></div><time>{{ formatDate(event.timestamp) }}</time></li></ol><el-empty v-else description="暂无关键活动" :image-size="56" />
      </el-card>
    </div>

    <section v-if="systemSummary" class="system-strip"><span>System {{ systemSummary.health.status }}</span><span>Jobs running {{ systemSummary.jobs.running }}</span><span>Executions {{ systemSummary.executions.total }}</span><span>Agents enabled {{ systemSummary.agents.enabled }}/{{ systemSummary.agents.total }}</span></section>
  </template>
</section></template>

<style scoped>
.dashboard-console { gap: 1rem; }.overview-grid { display: grid; grid-template-columns: repeat(5,minmax(0,1fr)); gap: .75rem; }.overview-grid article { display: grid; gap: .45rem; padding: 1rem; border: 1px solid var(--color-border); border-top: 3px solid var(--color-text-muted); border-radius: var(--radius-small); background: var(--color-surface); }.overview-grid article[data-tone="info"]{border-top-color:var(--color-info)}.overview-grid article[data-tone="warning"]{border-top-color:var(--color-warning)}.overview-grid article[data-tone="danger"]{border-top-color:var(--color-danger)}.overview-grid article[data-tone="success"]{border-top-color:var(--color-success)}.overview-grid span{color:var(--color-text-muted);font-size:.75rem;text-transform:uppercase}.overview-grid strong{font-size:1.8rem}.dashboard-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:1rem}.dashboard-panel{min-width:0}.dashboard-panel--wide{grid-column:1/-1}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:1rem}.panel-heading h2{margin:0}.panel-heading>span{color:var(--color-text-muted);font-weight:800}.task-rows,.compact-list{display:grid;gap:.65rem}.task-rows article{display:grid;grid-template-columns:minmax(0,1fr) auto auto auto;gap:.75rem;align-items:center;padding:.75rem;border-bottom:1px solid var(--color-border)}.task-rows small,.compact-list small{display:block;margin-top:.25rem;color:var(--color-text-muted)}.compact-list article{display:grid;gap:.55rem;padding:.75rem;border-bottom:1px solid var(--color-border)}.compact-list p{margin:0;color:var(--color-text-muted);white-space:pre-wrap;overflow-wrap:anywhere}.failure-copy{color:var(--color-danger)!important}.inline-tags,.row-actions{display:flex;flex-wrap:wrap;gap:.5rem}.row-actions a,.compact-list a,.panel-heading a{color:var(--color-primary-strong);font-size:.8rem;font-weight:700;text-decoration:none}.activity-list{display:grid;gap:.7rem;margin:0;padding:0;list-style:none}.activity-list li{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:.65rem}.activity-list li>span{width:.5rem;height:.5rem;margin-top:.35rem;border-radius:50%;background:var(--color-info)}.activity-list p{margin:.2rem 0 0;color:var(--color-text-muted)}.activity-list time{color:var(--color-text-muted);font-size:.72rem}.system-strip{display:flex;flex-wrap:wrap;gap:1.5rem;padding:.75rem 1rem;border:1px solid var(--color-border);border-radius:var(--radius-small);color:var(--color-text-muted);font-size:.78rem}.system-strip span:first-child{color:var(--color-success)}
@media(max-width:1100px){.overview-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.dashboard-grid{grid-template-columns:1fr}.dashboard-panel--wide{grid-column:auto}}@media(max-width:720px){.overview-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.task-rows article{grid-template-columns:minmax(0,1fr) auto}.row-actions{grid-column:1/-1}.activity-list li{grid-template-columns:auto minmax(0,1fr)}.activity-list time{grid-column:2}}
</style>
