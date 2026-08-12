<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'

import { createTask, getTasks } from '../api/tasks'
import { getProjects, getProjectWorkspaces } from '../api/projects'
import TaskDetail from '../components/TaskDetail.vue'
import TaskTable from '../components/TaskTable.vue'
import type { CreateTaskRequest, TaskRecord } from '../types/task'
import type { Project } from '../types/project'
import type { Workspace } from '../types/workspace'
import type { PlanApprovalRequest } from '../types/planApproval'
import { approveTask, getPlanApproval, rejectTask } from '../api/planApprovals'

const tasks = ref<TaskRecord[]>([])
const selectedTask = ref<TaskRecord | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const submitError = ref<string | null>(null)
const projects = ref<Project[]>([])
const workspaces = ref<Workspace[]>([])
const loadingWorkspaces = ref(false)
const approval = ref<PlanApprovalRequest | null>(null)
const approvalLoading = ref(false)
const decisionBusy = ref(false)

const form = reactive<CreateTaskRequest>({
  name: '',
  description: '',
  goal: '',
  plannerName: 'hermes',
  projectId: '',
  workspaceId: '',
  executionMode: 'READ_ONLY',
})

const plannerOptions = ['hermes', 'fake']

async function loadTasks(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    tasks.value = await getTasks()
    if (!selectedTask.value && tasks.value.length > 0) {
      selectedTask.value = tasks.value[0]
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load tasks.'
  } finally {
    loading.value = false
  }
}

async function loadProjects(): Promise<void> {
  projects.value = (await getProjects()).filter((project) => project.status === 'ACTIVE')
}

watch(() => form.projectId, async (projectId) => {
  form.workspaceId = ''
  workspaces.value = []
  if (!projectId) return
  loadingWorkspaces.value = true
  try {
    workspaces.value = await getProjectWorkspaces(projectId)
    if (workspaces.value.length === 1) {
      form.workspaceId = workspaces.value[0].workspaceId
    }
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : '无法加载 Workspace。'
  } finally {
    loadingWorkspaces.value = false
  }
})

watch(() => selectedTask.value?.approvalId, async (approvalId) => {
  approval.value = null
  if (!approvalId) return
  approvalLoading.value = true
  try {
    approval.value = await getPlanApproval(approvalId)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载 Plan Approval。'
  } finally {
    approvalLoading.value = false
  }
}, { immediate: true })

async function decide(action: 'approve' | 'reject', approver: string, reason = ''): Promise<void> {
  const task = selectedTask.value
  if (!task || decisionBusy.value) return
  decisionBusy.value = true
  errorMessage.value = null
  try {
    const updated = action === 'approve'
      ? await approveTask(task.taskId, approver)
      : await rejectTask(task.taskId, approver, reason)
    selectedTask.value = updated
    if (updated.approvalId) approval.value = await getPlanApproval(updated.approvalId)
    await loadTasks()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '审批操作失败。'
  } finally {
    decisionBusy.value = false
  }
}

async function handleCreate(): Promise<void> {
  if (!form.name.trim() || !form.goal.trim() || !form.projectId || !form.workspaceId) {
		submitError.value = '任务名称、目标、Project 与 Workspace 为必填项。'
    return
  }

  submitting.value = true
  submitError.value = null

  try {
    const task = await createTask({
      name: form.name.trim(),
      description: form.description.trim(),
      goal: form.goal.trim(),
      plannerName: form.plannerName,
      projectId: form.projectId,
      workspaceId: form.workspaceId,
      executionMode: form.executionMode,
    })
    form.name = ''
    form.description = ''
    form.goal = ''
    selectedTask.value = task
    await loadTasks()
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : 'Unable to create task.'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => Promise.all([loadTasks(), loadProjects()]))
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Task Center</p>
        <h1>Tasks</h1>
        <p class="page-description">
          User Request → Task → Plan → Approval → Execution → Result
        </p>
      </div>
    </header>

    <el-card shadow="never" class="create-card">
      <template #header>
        <span class="card-title">创建任务</span>
      </template>
      <el-form label-position="top" @submit.prevent="handleCreate">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="任务名称" required>
              <el-input v-model="form.name" placeholder="例如 Implement login" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="Planner">
              <el-select v-model="form.plannerName">
                <el-option
                  v-for="planner in plannerOptions"
                  :key="planner"
                  :label="planner"
                  :value="planner"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="Project" required>
              <el-select v-model="form.projectId" placeholder="选择项目" style="width: 100%">
                <el-option v-for="item in projects" :key="item.projectId"
                  :label="`${item.name} (${item.projectId})`" :value="item.projectId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="Workspace" required>
              <el-select v-model="form.workspaceId" :loading="loadingWorkspaces"
                :disabled="!form.projectId || workspaces.length === 0"
                :placeholder="workspaces.length === 0 ? '该项目暂无 Workspace' : '选择 Workspace'"
                style="width: 100%">
                <el-option v-for="item in workspaces" :key="item.workspaceId"
                  :label="`${item.path} (${item.branch || 'unknown'})`" :value="item.workspaceId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="执行模式" required>
              <el-select v-model="form.executionMode" style="width: 100%">
                <el-option label="READ_ONLY（只读）" value="READ_ONLY" />
                <el-option label="READ_WRITE（可写）" value="READ_WRITE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="任务描述（可选）" />
        </el-form-item>
        <el-form-item label="目标" required>
          <el-input
            v-model="form.goal"
            type="textarea"
            :rows="3"
            placeholder="例如 Implement a login flow with tests"
          />
        </el-form-item>
        <p v-if="submitError" class="form-error">{{ submitError }}</p>
        <el-button type="primary" :loading="submitting" native-type="submit"
          :disabled="!form.projectId || !form.workspaceId">
          创建并规划
        </el-button>
      </el-form>
    </el-card>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <div v-else class="tasks-layout">
      <section class="tasks-layout__list" aria-label="Task list">
        <el-card shadow="never">
          <TaskTable
            :tasks="tasks"
            :loading="loading"
            :selected-task-id="selectedTask?.taskId ?? null"
            @select="selectedTask = $event"
          />
        </el-card>
      </section>
      <section class="tasks-layout__detail" aria-label="Task detail">
        <TaskDetail :task="selectedTask" :approval="approval"
          :approval-loading="approvalLoading" :decision-busy="decisionBusy"
          @approve="(approver) => decide('approve', approver)"
          @reject="(approver, reason) => decide('reject', approver, reason)" />
      </section>
    </div>
  </section>
</template>

<style scoped>
.create-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 700;
}

.form-error {
  margin: 0 0 1rem;
  color: var(--color-danger);
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}

.tasks-layout {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(380px, 0.8fr) minmax(600px, 1.3fr);
  gap: clamp(16px, 1.5vw, 28px);
  align-items: start;
}

.tasks-layout__list,
.tasks-layout__detail {
  min-width: 0;
}

@media (min-width: 1600px) {
  .tasks-layout {
    grid-template-columns: minmax(380px, 0.8fr) minmax(600px, 1.7fr);
  }
}

@media (min-width: 2200px) {
  .tasks-layout {
    grid-template-columns: minmax(380px, 0.7fr) minmax(600px, 1.8fr);
  }
}

@media (max-width: 1199px) {
  .tasks-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
