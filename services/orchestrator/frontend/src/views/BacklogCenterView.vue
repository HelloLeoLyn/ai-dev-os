<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AsyncState from '../components/AsyncState.vue'
import ConsoleCard from '../components/ConsoleCard.vue'
import SectionHeader from '../components/SectionHeader.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { changeBacklogStatus, convertBacklog, createBacklog, getBacklog, updateBacklog } from '../api/backlog'
import { getProjects, getProjectWorkspaces } from '../api/projects'
import { canBlockBacklog, canUnblockBacklog } from '../services/backlogActions'
import type { BacklogDraft, BacklogItem, BacklogPriority, BacklogSourceType, BacklogStatus, ConvertBacklogRequest } from '../types/backlog'
import type { Project } from '../types/project'
import type { Workspace } from '../types/workspace'

const router = useRouter()
const route = useRoute()
const items = ref<BacklogItem[]>([])
const projects = ref<Project[]>([])
const workspaces = ref<Workspace[]>([])
const selected = ref<BacklogItem | null>(null)
const detailOpen = ref(false)
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const editorOpen = ref(false)
const convertOpen = ref(false)
const editingId = ref<string | null>(null)
const filter = reactive<{ status?: BacklogStatus; priority?: BacklogPriority; sourceType?: BacklogSourceType }>({})
const statuses: BacklogStatus[] = ['IDEA', 'PLANNED', 'READY', 'BLOCKED', 'CONVERTED', 'DONE', 'CANCELLED']
const priorities: BacklogPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const sources: BacklogSourceType[] = ['MANUAL', 'LESSON', 'ROADMAP', 'TASK', 'SYSTEM']
const draft = reactive<BacklogDraft>(emptyDraft())
const conversion = reactive<ConvertBacklogRequest>({ goal: '', plannerName: 'hermes', projectId: '', workspaceId: '', executionMode: 'READ_ONLY' })
const summary = computed(() => Object.fromEntries(statuses.map(status => [status, items.value.filter(item => item.status === status).length])))
const dependencyOptions = computed(() => items.value.filter(item => item.backlogItemId !== editingId.value))
const transitions: Partial<Record<BacklogStatus, BacklogStatus[]>> = { IDEA: ['PLANNED'], PLANNED: ['READY'], READY: ['PLANNED'], BLOCKED: ['PLANNED', 'READY'] }
function nextStatuses(item: BacklogItem): BacklogStatus[] { return transitions[item.status] ?? [] }
function changeStatus(item: BacklogItem, status: BacklogStatus): void { void setStatus(item, status) }

function emptyDraft(): BacklogDraft {
  return { title: '', description: '', status: 'IDEA', priority: 'MEDIUM', projectId: '', workspaceId: '', sourceType: 'MANUAL', sourceReference: '', blockedReason: '', dependsOn: [], tags: [] }
}
async function load(): Promise<void> {
  loading.value = true; error.value = null
  try {
    items.value = await getBacklog(filter)
    const requested = typeof route.query.item === 'string' ? route.query.item : null
    const match = requested ? items.value.find(item => item.backlogItemId === requested) : null
    if (match) openDetail(match)
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'Unable to load backlog.' }
  finally { loading.value = false }
}
async function loadWorkspaces(projectId: string): Promise<void> {
  workspaces.value = projectId ? await getProjectWorkspaces(projectId) : []
}
watch(() => draft.projectId, async id => { draft.workspaceId = ''; await loadWorkspaces(id) })
watch(() => conversion.projectId, async id => { conversion.workspaceId = ''; await loadWorkspaces(id) })
function openCreate(): void { editingId.value = null; Object.assign(draft, emptyDraft()); editorOpen.value = true }
function openDetail(item: BacklogItem): void { selected.value = item; detailOpen.value = true }
async function openEdit(item: BacklogItem): Promise<void> {
  editingId.value = item.backlogItemId
  Object.assign(draft, { title: item.title, description: item.description ?? '', status: item.status, priority: item.priority, projectId: item.projectId ?? '', workspaceId: item.workspaceId ?? '', sourceType: item.sourceType, sourceReference: item.sourceReference ?? '', blockedReason: item.blockedReason ?? '', dependsOn: [...item.dependsOn], tags: [...item.tags] })
  if (item.projectId) { await loadWorkspaces(item.projectId); draft.workspaceId = item.workspaceId ?? '' }
  editorOpen.value = true
}
async function save(): Promise<void> {
  if (!draft.title.trim()) return void ElMessage.error('Title is required.')
  if (draft.status === 'BLOCKED' && !draft.blockedReason.trim()) return void ElMessage.error('Blocked reason is required.')
  saving.value = true
  try {
    if (editingId.value) {
      const { status: _status, blockedReason: _reason, ...request } = draft
      await updateBacklog(editingId.value, request)
      const current = items.value.find(item => item.backlogItemId === editingId.value)
      if (current?.status === 'BLOCKED' && current.blockedReason !== draft.blockedReason.trim()) await changeBacklogStatus(editingId.value, 'BLOCKED', draft.blockedReason.trim())
    } else await createBacklog(draft)
    editorOpen.value = false; await load(); ElMessage.success('Backlog item saved.')
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : 'Unable to save backlog item.') }
  finally { saving.value = false }
}
async function setStatus(item: BacklogItem, status: BacklogStatus, blockedReason?: string): Promise<void> {
  try { await changeBacklogStatus(item.backlogItemId, status, blockedReason); await load(); ElMessage.success(`Status changed to ${status}.`) }
  catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : 'Unable to change status.') }
}
async function block(item: BacklogItem): Promise<void> {
  try { const result = await ElMessageBox.prompt('Why is this item blocked?', 'Block backlog item', { inputPattern: /\S+/, inputErrorMessage: 'Blocked reason is required.' }); await setStatus(item, 'BLOCKED', result.value) }
  catch { /* cancelled */ }
}
function openConvert(item: BacklogItem): void {
  selected.value = item
  Object.assign(conversion, { goal: '', plannerName: 'hermes', projectId: item.projectId ?? '', workspaceId: item.workspaceId ?? '', executionMode: 'READ_ONLY' })
  if (item.projectId) void loadWorkspaces(item.projectId).then(() => { conversion.workspaceId = item.workspaceId ?? '' })
  convertOpen.value = true
}
async function submitConvert(): Promise<void> {
  if (!selected.value || !conversion.goal.trim() || !conversion.projectId || !conversion.workspaceId) return void ElMessage.error('Goal, Project and Workspace are required.')
  saving.value = true
  try {
    const result = await convertBacklog(selected.value.backlogItemId, conversion)
    convertOpen.value = false; await load(); ElMessage.success('Task created; approval and execution were not started automatically.')
    await router.push(`/tasks/${encodeURIComponent(result.task.taskId)}`)
  } catch (cause) { ElMessage.error(cause instanceof Error ? cause.message : 'Unable to convert backlog item.') }
  finally { saving.value = false }
}
function format(value: string | null): string { return value ? new Date(value).toLocaleString() : '—' }
onMounted(async () => { try { projects.value = (await getProjects()).filter(project => project.status === 'ACTIVE') } finally { await load() } })
</script>

<template>
  <section class="page-stack">
    <SectionHeader eyebrow="Planning" title="Backlog Center" description="未来工作在这里规划；执行始终进入正式 Task Center。">
      <el-button @click="load">Refresh</el-button><el-button type="primary" @click="openCreate">Create</el-button>
    </SectionHeader>
    <div class="summary-grid">
      <ConsoleCard v-for="status in ['IDEA','PLANNED','READY','BLOCKED','CONVERTED','DONE']" :key="status" :eyebrow="status" :title="String(summary[status] ?? 0)" />
    </div>
    <ConsoleCard title="Backlog items">
      <div class="filters">
        <el-select v-model="filter.status" clearable placeholder="Status"><el-option v-for="value in statuses" :key="value" :value="value" :label="value" /></el-select>
        <el-select v-model="filter.priority" clearable placeholder="Priority"><el-option v-for="value in priorities" :key="value" :value="value" :label="value" /></el-select>
        <el-select v-model="filter.sourceType" clearable placeholder="Source"><el-option v-for="value in sources" :key="value" :value="value" :label="value" /></el-select>
        <el-button @click="load">Apply</el-button>
      </div>
      <AsyncState :loading="loading" :error="error" :empty="!loading && !error && items.length === 0" empty-text="No backlog items" @retry="load">
        <el-table :data="items" @row-click="openDetail">
          <el-table-column prop="title" label="Title" min-width="220" />
          <el-table-column label="Priority"><template #default="{ row }"><StatusBadge :status="row.priority" /></template></el-table-column>
          <el-table-column label="Status"><template #default="{ row }"><StatusBadge :status="row.status" /></template></el-table-column>
          <el-table-column prop="projectId" label="Project" min-width="150" />
          <el-table-column prop="sourceType" label="Source" min-width="110" class-name="nowrap-column" label-class-name="nowrap-column" />
          <el-table-column label="Dependencies" min-width="130" class-name="nowrap-column" label-class-name="nowrap-column"><template #default="{ row }">{{ row.dependsOn.length }}</template></el-table-column>
          <el-table-column label="Updated" min-width="170"><template #default="{ row }">{{ format(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="Actions" width="330" fixed="right"><template #default="{ row }">
            <el-button size="small" :disabled="['CONVERTED','DONE','CANCELLED'].includes(row.status)" @click.stop="openEdit(row)">Edit</el-button>
            <el-button v-if="canBlockBacklog(row.status)" size="small" @click.stop="block(row)">Block</el-button>
            <el-button v-if="canUnblockBacklog(row.status)" size="small" @click.stop="setStatus(row, 'PLANNED')">Unblock</el-button>
            <el-dropdown v-if="nextStatuses(row).length" trigger="click" @click.stop @command="changeStatus(row, $event as BacklogStatus)">
              <el-button size="small">Change Status</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="next in nextStatuses(row)" :key="next" :command="next">{{ next }}</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
            <el-button v-if="row.status === 'READY'" size="small" type="primary" @click.stop="openConvert(row)">Convert</el-button>
            <el-button v-if="!['CONVERTED','DONE','CANCELLED'].includes(row.status)" size="small" type="danger" plain @click.stop="setStatus(row, 'CANCELLED')">Cancel</el-button>
          </template></el-table-column>
        </el-table>
      </AsyncState>
    </ConsoleCard>

    <el-drawer v-model="detailOpen" title="Backlog Detail" size="480px">
      <template v-if="selected">
        <h3>{{ selected.title }}</h3><StatusBadge :status="selected.status" />
        <el-descriptions :column="1" border class="detail-grid">
          <el-descriptions-item label="Description">{{ selected.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Priority">{{ selected.priority }}</el-descriptions-item>
          <el-descriptions-item label="Project">{{ selected.projectId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Workspace">{{ selected.workspaceId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Source">{{ selected.sourceType }} · {{ selected.sourceReference || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Dependencies">{{ selected.dependsOn.join(', ') || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Blocked Reason">{{ selected.blockedReason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Created">{{ format(selected.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="Updated">{{ format(selected.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-button v-if="selected.convertedTaskId" type="primary" @click="router.push(`/tasks/${encodeURIComponent(selected!.convertedTaskId!)}`)">Open Task Detail</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="editorOpen" :title="editingId ? 'Edit Backlog Item' : 'Create Backlog Item'" width="680px">
      <el-form label-position="top">
        <el-form-item label="Title" required><el-input v-model="draft.title" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="draft.description" type="textarea" /></el-form-item>
        <el-row :gutter="12"><el-col :span="8"><el-form-item label="Status"><el-select v-model="draft.status" :disabled="Boolean(editingId)"><el-option v-for="value in ['IDEA','PLANNED','READY','BLOCKED']" :key="value" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="Priority"><el-select v-model="draft.priority"><el-option v-for="value in priorities" :key="value" :value="value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="Source"><el-select v-model="draft.sourceType"><el-option v-for="value in sources" :key="value" :value="value" /></el-select></el-form-item></el-col></el-row>
        <el-form-item v-if="draft.status === 'BLOCKED'" label="Blocked Reason" required><el-input v-model="draft.blockedReason" /></el-form-item>
        <el-form-item label="Source Reference"><el-input v-model="draft.sourceReference" placeholder="docs/roadmap/README.md#stable-anchor" /></el-form-item>
        <el-row :gutter="12"><el-col :span="12"><el-form-item label="Project"><el-select v-model="draft.projectId" clearable><el-option v-for="project in projects" :key="project.projectId" :label="project.name" :value="project.projectId" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="Workspace"><el-select v-model="draft.workspaceId" clearable :disabled="!draft.projectId"><el-option v-for="workspace in workspaces" :key="workspace.workspaceId" :label="workspace.path" :value="workspace.workspaceId" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="Dependencies"><el-select v-model="draft.dependsOn" multiple><el-option v-for="item in dependencyOptions" :key="item.backlogItemId" :label="item.title" :value="item.backlogItemId" /></el-select></el-form-item>
        <el-form-item label="Tags"><el-select v-model="draft.tags" multiple filterable allow-create default-first-option /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorOpen = false">Cancel</el-button><el-button type="primary" :loading="saving" @click="save">Save</el-button></template>
    </el-dialog>

    <el-dialog v-model="convertOpen" title="Convert to Task" width="620px">
      <el-alert type="info" :closable="false" title="Creates a Task and Plan/Approval; it never approves or executes the Task." />
      <el-form label-position="top">
        <el-form-item label="Goal" required><el-input v-model="conversion.goal" type="textarea" /></el-form-item>
        <el-form-item label="Planner"><el-input v-model="conversion.plannerName" /></el-form-item>
        <el-row :gutter="12"><el-col :span="12"><el-form-item label="Project" required><el-select v-model="conversion.projectId"><el-option v-for="project in projects" :key="project.projectId" :label="project.name" :value="project.projectId" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="Workspace" required><el-select v-model="conversion.workspaceId"><el-option v-for="workspace in workspaces" :key="workspace.workspaceId" :label="workspace.path" :value="workspace.workspaceId" /></el-select></el-form-item></el-col></el-row>
        <el-form-item label="Execution Mode"><el-select v-model="conversion.executionMode"><el-option label="READ_ONLY" value="READ_ONLY" /><el-option label="READ_WRITE" value="READ_WRITE" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="convertOpen = false">Cancel</el-button><el-button type="primary" :loading="saving" @click="submitConvert">Create Task</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.summary-grid{display:grid;grid-template-columns:repeat(6,minmax(120px,1fr));gap:12px}.filters{display:flex;gap:10px;margin-bottom:16px;flex-wrap:wrap}.detail-grid{margin:16px 0}:deep(.nowrap-column .cell){white-space:nowrap}@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,1fr)}}
</style>
