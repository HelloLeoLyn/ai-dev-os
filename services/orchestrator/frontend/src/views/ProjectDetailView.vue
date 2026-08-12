<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { apiClient } from '../api/client'
import {
  createProjectWorkspace,
  createProjectTask,
  getProject,
  getProjectMetrics,
  getProjectTasks,
  getProjectWorkspaces,
} from '../api/projects'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { AgentMetrics } from '../types/agentMetrics'
import type { MemoryRecord } from '../types/memory'
import type { Project } from '../types/project'
import type { TaskRecord } from '../types/task'
import type { Workspace } from '../types/workspace'
import { useTaskNotifications } from '../composables/useTaskNotifications'

const route = useRoute()
const taskNotifications = useTaskNotifications()
const project = ref<Project | null>(null)
const workspaces = ref<Workspace[]>([])
const tasks = ref<TaskRecord[]>([])
const metrics = ref<AgentMetrics[]>([])
const memories = ref<MemoryRecord[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const workspaceDialogVisible = ref(false)
const workspacePath = ref('')
const creatingWorkspace = ref(false)
const workspaceErrorMessage = ref<string | null>(null)
const taskDialogVisible = ref(false)
const creatingTask = ref(false)
const taskErrorMessage = ref<string | null>(null)
const taskForm = reactive({
  name: '', description: '', goal: '', plannerName: 'hermes',
  workspaceId: '', executionMode: 'READ_ONLY' as const,
})

async function loadProject(): Promise<void> {
  const projectId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  if (!projectId) {
    errorMessage.value = 'Project ID is required.'
    loading.value = false
    return
  }
  try {
    const [loadedProject, loadedWorkspaces, loadedTasks, loadedMetrics, loadedMemories] =
      await Promise.all([
        getProject(projectId),
        getProjectWorkspaces(projectId),
        getProjectTasks(projectId),
        getProjectMetrics(projectId),
        apiClient.get<MemoryRecord[]>('/api/memory/search', { projectId }),
      ])
    project.value = loadedProject
    workspacePath.value = loadedProject.path
    workspaces.value = loadedWorkspaces
    tasks.value = loadedTasks
    metrics.value = loadedMetrics
    memories.value = loadedMemories
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载项目详情。'
  } finally {
    loading.value = false
  }
}

function openWorkspaceDialog(): void {
  if (!project.value) return
  workspacePath.value = project.value.path
  workspaceErrorMessage.value = null
  workspaceDialogVisible.value = true
}

async function attachWorkspace(): Promise<void> {
  if (!project.value || creatingWorkspace.value) return
  creatingWorkspace.value = true
  workspaceErrorMessage.value = null
  try {
    const workspace = await createProjectWorkspace(
      project.value.projectId,
      workspacePath.value,
    )
    workspaces.value = [...workspaces.value, workspace]
    workspaceDialogVisible.value = false
  } catch (error) {
    workspaceErrorMessage.value =
      error instanceof Error ? error.message : '无法创建 Workspace。'
  } finally {
    creatingWorkspace.value = false
  }
}

function openTaskDialog(): void {
  taskErrorMessage.value = null
  taskForm.workspaceId = workspaces.value.length === 1 ? workspaces.value[0].workspaceId : ''
  taskDialogVisible.value = true
}

async function createTask(): Promise<void> {
  if (!project.value || !taskForm.name.trim() || !taskForm.goal.trim() || !taskForm.workspaceId) {
    taskErrorMessage.value = '任务名称、目标和 Workspace 为必填项。'
    return
  }
  creatingTask.value = true
  taskErrorMessage.value = null
  try {
    const task = await createProjectTask(project.value.projectId, {
      name: taskForm.name.trim(), description: taskForm.description.trim(),
      goal: taskForm.goal.trim(), plannerName: taskForm.plannerName,
      projectId: project.value.projectId, workspaceId: taskForm.workspaceId,
      executionMode: taskForm.executionMode,
    })
    tasks.value = [task, ...tasks.value]
    taskNotifications.track(task)
    taskDialogVisible.value = false
    taskForm.name = ''; taskForm.description = ''; taskForm.goal = ''
  } catch (error) {
    taskErrorMessage.value = error instanceof Error ? error.message : '创建 Task 失败。'
  } finally {
    creatingTask.value = false
  }
}

onMounted(loadProject)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <RouterLink class="back-link" to="/projects">← 所有项目</RouterLink>
        <h1 v-if="project">{{ project.name }}</h1>
        <p v-if="project && project.description" class="muted">{{ project.description }}</p>
      </div>
      <StatusBadge v-if="project" :status="project.status" />
    </header>

    <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
    <p v-if="loading" class="muted">加载中…</p>

    <template v-if="project">
      <BaseCard title="项目信息">
        <dl class="detail-grid">
          <div><dt>Project ID</dt><dd>{{ project.projectId }}</dd></div>
          <div><dt>路径</dt><dd>{{ project.path }}</dd></div>
          <div><dt>仓库地址</dt><dd>{{ project.repositoryUrl || '—' }}</dd></div>
          <div><dt>默认分支</dt><dd>{{ project.defaultBranch || '—' }}</dd></div>
          <div><dt>状态</dt><dd>{{ project.status }}</dd></div>
        </dl>
      </BaseCard>

      <BaseCard title="Workspaces">
        <div class="workspace-actions">
          <el-button type="primary" @click="openWorkspaceDialog">
            创建 / 接入 Workspace
          </el-button>
        </div>
        <el-table :data="workspaces" empty-text="该项目暂无 Workspace">
          <el-table-column prop="workspaceId" label="Workspace ID" min-width="180" />
          <el-table-column prop="path" label="路径" min-width="220" />
          <el-table-column prop="branch" label="分支" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>
      </BaseCard>

      <el-dialog v-model="workspaceDialogVisible" title="创建 / 接入 Workspace" width="560px">
        <p class="muted">绑定现有本地 Git 目录，不会复制、移动或修改目录内容。</p>
        <el-form label-position="top" @submit.prevent="attachWorkspace">
          <el-form-item label="Workspace 路径">
            <el-input v-model="workspacePath" placeholder="默认使用项目路径" />
          </el-form-item>
          <p v-if="workspaceErrorMessage" class="error-text">{{ workspaceErrorMessage }}</p>
        </el-form>
        <template #footer>
          <el-button @click="workspaceDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creatingWorkspace" @click="attachWorkspace">
            创建并绑定
          </el-button>
        </template>
      </el-dialog>

      <BaseCard title="Tasks">
        <div class="workspace-actions">
          <el-button type="primary" :disabled="workspaces.length === 0" @click="openTaskDialog">
            创建任务
          </el-button>
        </div>
        <el-table :data="tasks" empty-text="该项目暂无 Task">
          <el-table-column prop="taskId" label="Task ID" min-width="180" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="workspaceId" label="Workspace" min-width="180" />
          <el-table-column prop="executionMode" label="执行模式" width="130" />
        </el-table>
      </BaseCard>

      <el-dialog v-model="taskDialogVisible" title="创建项目任务" width="640px">
        <p class="muted">Project：<code>{{ project.projectId }}</code></p>
        <el-form label-position="top" @submit.prevent="createTask">
          <el-form-item label="任务名称" required><el-input v-model="taskForm.name" /></el-form-item>
          <el-form-item label="Workspace" required>
            <el-select v-model="taskForm.workspaceId" style="width: 100%">
              <el-option v-for="item in workspaces" :key="item.workspaceId"
                :label="`${item.path} (${item.branch || 'unknown'})`" :value="item.workspaceId" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行模式" required>
            <el-select v-model="taskForm.executionMode" style="width: 100%">
              <el-option label="READ_ONLY（只读）" value="READ_ONLY" />
              <el-option label="READ_WRITE（可写）" value="READ_WRITE" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述"><el-input v-model="taskForm.description" /></el-form-item>
          <el-form-item label="目标" required>
            <el-input v-model="taskForm.goal" type="textarea" :rows="4" />
          </el-form-item>
          <p v-if="taskErrorMessage" class="error-text">{{ taskErrorMessage }}</p>
        </el-form>
        <template #footer>
          <el-button @click="taskDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="creatingTask" @click="createTask">创建并规划</el-button>
        </template>
      </el-dialog>

      <BaseCard title="Agent Metrics">
        <el-table :data="metrics" empty-text="该项目暂无执行指标">
          <el-table-column prop="agentName" label="Agent" min-width="120" />
          <el-table-column prop="taskCount" label="任务数" width="90" />
          <el-table-column prop="successCount" label="成功" width="90" />
          <el-table-column prop="failedCount" label="失败" width="90" />
          <el-table-column prop="averageDuration" label="平均耗时(ms)" width="120" />
          <el-table-column prop="repairCount" label="Repair" width="90" />
        </el-table>
      </BaseCard>

      <BaseCard title="Memory">
        <el-table :data="memories" empty-text="该项目暂无记忆">
          <el-table-column prop="type" label="类型" width="140" />
          <el-table-column prop="key" label="Key" min-width="160" />
          <el-table-column prop="content" label="内容" min-width="240" />
        </el-table>
      </BaseCard>
    </template>
  </section>
</template>

<style scoped>
.workspace-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
</style>
