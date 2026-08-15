<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import type { PlanApprovalRequest } from '../types/planApproval'
import type { TaskRecord } from '../types/task'
import type { WorkflowProjection } from '../services/taskWorkflow'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ task: TaskRecord; approval: PlanApprovalRequest | null; workflow: WorkflowProjection }>()
const route = useRoute()
const advancedVisible = ref(false)
const base = computed(() => `/tasks/${encodeURIComponent(props.task.taskId)}`)
const tabs = computed(() => [
  { label: 'Overview', to: base.value, active: route.path === base.value },
  { label: 'Plan', to: `${base.value}/plan`, active: route.path.endsWith('/plan') },
  { label: 'Execution', to: `${base.value}/execution`, active: route.path.endsWith('/execution') },
  { label: 'Analysis', to: `${base.value}/analysis`, active: route.path.endsWith('/analysis') },
  { label: 'Timeline', to: `${base.value}/timeline`, active: route.path.endsWith('/timeline') },
])
</script>

<template>
  <header class="workspace-header">
    <RouterLink class="back-link" to="/tasks">← Task Center</RouterLink>
    <div class="workspace-title">
      <div><p class="page-eyebrow">Task Workspace</p><h1>{{ task.name || 'Untitled Task' }}</h1><p>{{ task.description || '暂无描述' }}</p></div>
      <div class="workspace-tags"><StatusBadge :status="task.status" /><StatusBadge :status="task.executionMode" /><StatusBadge v-if="approval" :status="approval.status" /></div>
    </div>
    <dl class="workspace-context">
      <div><dt>Project</dt><dd>{{ task.projectId }}</dd></div>
      <div><dt>Workspace</dt><dd>{{ task.workspaceId || '—' }}</dd></div>
      <div><dt>Current stage</dt><dd>{{ workflow.label }}</dd></div>
      <div><dt>Next action</dt><dd>{{ workflow.nextAction }}</dd></div>
    </dl>
    <nav class="workspace-tabs" aria-label="Task workspace">
      <RouterLink v-for="tab in tabs" :key="tab.to" :to="tab.to" :class="{ active: tab.active }">{{ tab.label }}</RouterLink>
      <button type="button" @click="advancedVisible = true">Technical Details</button>
    </nav>
    <el-drawer v-model="advancedVisible" title="Technical Details" size="min(520px, 92vw)" append-to-body>
      <dl class="advanced-list"><div><dt>taskId</dt><dd><code>{{ task.taskId }}</code></dd></div><div><dt>projectId</dt><dd><code>{{ task.projectId }}</code></dd></div><div><dt>workspaceId</dt><dd><code>{{ task.workspaceId || '—' }}</code></dd></div><div><dt>approvalId</dt><dd><code>{{ task.approvalId || '—' }}</code></dd></div><div><dt>planRunId</dt><dd><code>{{ task.planRunId || '—' }}</code></dd></div><div><dt>snapshotHash</dt><dd><code>{{ approval?.planSnapshotHash || '—' }}</code></dd></div></dl>
    </el-drawer>
  </header>
</template>

<style scoped>
.workspace-header{display:grid;gap:1rem}.back-link{color:var(--color-primary-strong);text-decoration:none}.workspace-title{display:flex;justify-content:space-between;gap:1rem}.workspace-title h1{margin:0}.workspace-title p:not(.page-eyebrow){margin:.45rem 0 0;color:var(--color-text-muted)}.workspace-tags{display:flex;align-items:flex-start;flex-wrap:wrap;gap:.5rem}.workspace-context{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.75rem;margin:0}.workspace-context div{padding:.8rem;border:1px solid var(--color-border);border-radius:var(--radius-small)}dt{color:var(--color-text-muted);font-size:.72rem;text-transform:uppercase}dd{margin:.3rem 0 0;overflow-wrap:anywhere}.workspace-tabs{display:flex;overflow-x:auto;border-bottom:1px solid var(--color-border)}.workspace-tabs a,.workspace-tabs button{flex:none;padding:.75rem 1rem;border:0;border-bottom:2px solid transparent;color:var(--color-text-muted);background:transparent;text-decoration:none;cursor:pointer}.workspace-tabs a.active{border-color:var(--color-primary);color:var(--color-primary-strong);font-weight:700}.advanced-list{display:grid;gap:1rem;margin:0}.advanced-list div{padding-bottom:1rem;border-bottom:1px solid var(--color-border)}@media(max-width:760px){.workspace-title{flex-direction:column}.workspace-context{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:480px){.workspace-context{grid-template-columns:1fr}}
</style>
