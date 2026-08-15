<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTask, getTasks } from '../api/tasks'
import { getProjects, getProjectWorkspaces } from '../api/projects'
import TaskTable from '../components/TaskTable.vue'
import AsyncState from '../components/AsyncState.vue'
import { getTaskApproval } from '../composables/useTaskContext'
import { useTaskNotifications } from '../composables/useTaskNotifications'
import { duplicateTaskDraft, rememberTaskCreateMetadata, taskCreateMetadata } from '../services/taskDuplicate'
import type { CreateTaskRequest, TaskRecord } from '../types/task'
import type { Project } from '../types/project'
import type { Workspace } from '../types/workspace'
import type { PlanApprovalStatus } from '../types/planApproval'

const route = useRoute(), router = useRouter(), notifications = useTaskNotifications()
const tasks = ref<TaskRecord[]>([]), loading = ref(true), submitting = ref(false)
const errorMessage = ref<string | null>(null), submitError = ref<string | null>(null)
const projects = ref<Project[]>([]), workspaces = ref<Workspace[]>([]), loadingWorkspaces = ref(false)
const createVisible = ref(false), duplicateNotice = ref<string | null>(null)
const approvalStatuses = ref<Record<string, PlanApprovalStatus | null>>({})
const filters = reactive({ search: '', status: '', projectId: '', executionMode: '' })
const form = reactive<CreateTaskRequest>({ name: '', description: '', goal: '', plannerName: 'hermes', projectId: '', workspaceId: '', executionMode: 'READ_ONLY' })
const plannerOptions = ['hermes', 'fake']
const statuses = computed(() => [...new Set(tasks.value.map(task => task.status))])
const filteredTasks = computed(() => {
  const query = filters.search.trim().toLowerCase()
  return tasks.value.filter(task => (!query || `${task.name ?? ''} ${task.description ?? ''} ${task.taskId}`.toLowerCase().includes(query))
    && (!filters.status || task.status === filters.status)
    && (!filters.projectId || task.projectId === filters.projectId)
    && (!filters.executionMode || task.executionMode === filters.executionMode))
})

async function loadTasks(): Promise<void> {
  loading.value = true; errorMessage.value = null
  try {
    tasks.value = await getTasks()
    tasks.value.filter(task => !['SUCCESS', 'COMPLETED', 'FAILED', 'REJECTED'].includes(task.status)).forEach(task => notifications.track(task))
    const approvals = await Promise.all(tasks.value.map(async task => {
      try { return [task.taskId, task.approvalId ? (await getTaskApproval(task))?.status ?? null : null] as const }
      catch { return [task.taskId, null] as const }
    }))
    approvalStatuses.value = Object.fromEntries(approvals)
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : 'Unable to load tasks.' }
  finally { loading.value = false }
}
async function loadProjects(): Promise<void> { projects.value = (await getProjects()).filter(project => project.status === 'ACTIVE') }
async function loadWorkspaces(projectId?: string): Promise<void> {
  form.workspaceId = ''; workspaces.value = []
  if (!projectId) return
  loadingWorkspaces.value = true
  try { workspaces.value = await getProjectWorkspaces(projectId); if (workspaces.value.length === 1) form.workspaceId = workspaces.value[0].workspaceId }
  catch (error) { submitError.value = error instanceof Error ? error.message : '无法加载 Workspace。' }
  finally { loadingWorkspaces.value = false }
}
watch(() => form.projectId, loadWorkspaces)
watch(filters, value => sessionStorage.setItem('task-center-filters', JSON.stringify(value)), { deep: true })
function selectTask(task: TaskRecord): void { sessionStorage.setItem('task-center-scroll', String(window.scrollY)); void router.push(`/tasks/${encodeURIComponent(task.taskId)}`) }
function resetForm(): void { Object.assign(form, { name: '', description: '', goal: '', plannerName: 'hermes', projectId: '', workspaceId: '', executionMode: 'READ_ONLY' }); duplicateNotice.value = null; submitError.value = null }
async function handleCreate(): Promise<void> {
  if (!form.name.trim() || !form.goal.trim() || !form.projectId || !form.workspaceId) { submitError.value = '任务名称、目标、Project 与 Workspace 为必填项。'; return }
  submitting.value = true; submitError.value = null
  try {
    const request = { ...form, name: form.name.trim(), description: form.description.trim(), goal: form.goal.trim() }
    const task = await createTask(request); rememberTaskCreateMetadata(task.taskId, request); notifications.track(task)
    createVisible.value = false; resetForm(); ElMessage.success('Task 已创建，正在规划...'); await loadTasks(); selectTask(task)
  } catch (error) { submitError.value = error instanceof Error ? error.message : 'Unable to create task.'; ElMessage.error(submitError.value) }
  finally { submitting.value = false }
}
async function prepareDuplicate(taskId: string): Promise<void> {
  const source = tasks.value.find(task => task.taskId === taskId); if (!source) return
  try {
    const duplicate = duplicateTaskDraft(source, await getTaskApproval(source), taskCreateMetadata(source.taskId))
    if (!duplicate.request.goal) throw new Error('历史 Task 没有可用的 Plan Goal，无法完整预填。')
    Object.assign(form, duplicate.request); await loadWorkspaces(source.projectId); form.workspaceId = duplicate.request.workspaceId
    duplicateNotice.value = duplicate.plannerWasRecovered ? '已复制任务配置，请确认后创建。' : '历史任务未保存 Planner，已使用默认 hermes，请确认后创建。'
    createVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '无法复制 Task。') }
}
onBeforeRouteLeave(() => sessionStorage.setItem('task-center-scroll', String(window.scrollY)))
onMounted(async () => {
  try { Object.assign(filters, JSON.parse(sessionStorage.getItem('task-center-filters') || '{}')) } catch { /* ignore invalid local UI state */ }
  await Promise.all([loadTasks(), loadProjects()])
  const duplicate = typeof route.query.duplicate === 'string' ? route.query.duplicate : null
  if (duplicate) { await prepareDuplicate(duplicate); void router.replace('/tasks') }
  requestAnimationFrame(() => window.scrollTo({ top: Number(sessionStorage.getItem('task-center-scroll') || 0) }))
})
</script>

<template><section class="page-stack">
  <header class="page-header"><div><p class="page-eyebrow">Task Center</p><h1>Tasks</h1><p class="page-description">Search and inspect Tasks, then continue in a dedicated Task Workspace.</p></div><div class="header-actions"><el-button :loading="loading" @click="loadTasks">Refresh</el-button><el-button type="primary" @click="createVisible = true">Create Task</el-button></div></header>
  <el-card shadow="never" class="filters"><el-input v-model="filters.search" clearable placeholder="Search title, description or Task ID" /><el-select v-model="filters.status" clearable placeholder="Status"><el-option v-for="status in statuses" :key="status" :label="status" :value="status" /></el-select><el-select v-model="filters.projectId" clearable placeholder="Project"><el-option v-for="project in projects" :key="project.projectId" :label="project.name" :value="project.projectId" /></el-select><el-select v-model="filters.executionMode" clearable placeholder="Execution Mode"><el-option label="READ_ONLY" value="READ_ONLY" /><el-option label="READ_WRITE" value="READ_WRITE" /></el-select></el-card>
  <AsyncState :loading="loading && !tasks.length" :error="errorMessage" :empty="!loading && !filteredTasks.length" empty-text="没有符合条件的 Task" @retry="loadTasks"><el-card shadow="never"><TaskTable :tasks="filteredTasks" :loading="loading" :approval-statuses="approvalStatuses" @select="selectTask" /></el-card></AsyncState>
  <el-dialog v-model="createVisible" title="Create Task" width="min(760px, 94vw)" destroy-on-close @closed="submitError = null">
    <el-form label-position="top" @submit.prevent="handleCreate"><el-row :gutter="16"><el-col :xs="24" :sm="12"><el-form-item label="任务名称" required><el-input v-model="form.name" /></el-form-item></el-col><el-col :xs="24" :sm="12"><el-form-item label="Planner"><el-select v-model="form.plannerName" style="width:100%"><el-option v-for="planner in plannerOptions" :key="planner" :label="planner" :value="planner" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :xs="24" :sm="8"><el-form-item label="Project" required><el-select v-model="form.projectId" style="width:100%"><el-option v-for="item in projects" :key="item.projectId" :label="item.name" :value="item.projectId" /></el-select></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="Workspace" required><el-select v-model="form.workspaceId" :loading="loadingWorkspaces" :disabled="!form.projectId" style="width:100%"><el-option v-for="item in workspaces" :key="item.workspaceId" :label="`${item.path} (${item.branch || 'unknown'})`" :value="item.workspaceId" /></el-select></el-form-item></el-col><el-col :xs="24" :sm="8"><el-form-item label="Execution Mode" required><el-select v-model="form.executionMode" style="width:100%"><el-option label="READ_ONLY" value="READ_ONLY" /><el-option label="READ_WRITE" value="READ_WRITE" /></el-select></el-form-item></el-col></el-row><el-form-item label="Description"><el-input v-model="form.description" /></el-form-item><el-form-item label="Goal" required><el-input v-model="form.goal" type="textarea" :rows="3" /></el-form-item><p v-if="duplicateNotice" class="notice">{{ duplicateNotice }}</p><p v-if="submitError" class="error">{{ submitError }}</p></el-form>
    <template #footer><el-button @click="createVisible = false">Cancel</el-button><el-button type="primary" :loading="submitting" :disabled="!form.projectId || !form.workspaceId" @click="handleCreate">创建并规划</el-button></template>
  </el-dialog>
</section></template>
<style scoped>.header-actions{display:flex;gap:.75rem}.filters :deep(.el-card__body){display:grid;grid-template-columns:minmax(240px,2fr) repeat(3,minmax(150px,1fr));gap:.75rem}.notice{color:var(--color-warning)}.error{color:var(--color-danger)}@media(max-width:900px){.filters :deep(.el-card__body){grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:560px){.filters :deep(.el-card__body){grid-template-columns:1fr}.header-actions{flex-wrap:wrap}}
</style>
