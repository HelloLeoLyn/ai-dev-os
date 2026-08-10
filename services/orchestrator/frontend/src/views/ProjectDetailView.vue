<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { apiClient } from '../api/client'
import {
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

const route = useRoute()
const project = ref<Project | null>(null)
const workspaces = ref<Workspace[]>([])
const tasks = ref<TaskRecord[]>([])
const metrics = ref<AgentMetrics[]>([])
const memories = ref<MemoryRecord[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

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
        <el-table :data="workspaces" empty-text="该项目暂无 Workspace">
          <el-table-column prop="workspaceId" label="Workspace ID" min-width="180" />
          <el-table-column prop="path" label="路径" min-width="220" />
          <el-table-column prop="branch" label="分支" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>
      </BaseCard>

      <BaseCard title="Tasks">
        <el-table :data="tasks" empty-text="该项目暂无 Task">
          <el-table-column prop="taskId" label="Task ID" min-width="180" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="workspaceId" label="Workspace" min-width="180" />
        </el-table>
      </BaseCard>

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
