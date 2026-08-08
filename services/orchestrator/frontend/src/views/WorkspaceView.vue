<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import {
  createWorkspace,
  getWorkspaceGitDiff,
  getWorkspaceGitStatus,
  getWorkspaces,
} from '../api/workspaces'
import { getProjects } from '../api/projects'
import { useRegistryList } from '../composables/useRegistryList'
import WorkspaceTable, { type WorkspaceRow } from '../components/WorkspaceTable.vue'
import type { GitDiff, GitStatus } from '../types/workspace'
import type { Project } from '../types/project'

const submitting = ref(false)
const projects = ref<Project[]>([])
const gitStatus = ref<GitStatus | null>(null)
const gitDiff = ref<GitDiff | null>(null)
const gitLoading = ref(false)
const gitError = ref<string | null>(null)

const {
  items: workspaces,
  selected: selectedWorkspace,
  loading,
  errorMessage,
  reload,
} = useRegistryList<WorkspaceRow>({
  fetch: async () => {
    const [loadedWorkspaces, loadedProjects] = await Promise.all([
      getWorkspaces(),
      getProjects(),
    ])
    projects.value = loadedProjects
    return Promise.all(
      loadedWorkspaces.map(async (workspace) => {
        try {
          return { ...workspace, gitStatus: await getWorkspaceGitStatus(workspace.workspaceId) }
        } catch {
          return workspace
        }
      }),
    )
  },
  idOf: (workspace) => workspace.workspaceId,
  errorText: '无法加载 Workspaces。',
})

const form = reactive({
  projectId: '',
  path: '',
})

function projectName(projectId: string): string {
  return projects.value.find((project) => project.projectId === projectId)?.name ?? projectId
}

async function handleCreate(): Promise<void> {
  if (!form.projectId.trim() || !form.path.trim()) {
    ElMessage.warning('Project 和 Path 为必填项。')
    return
  }
  submitting.value = true
  try {
    await createWorkspace({
      projectId: form.projectId.trim(),
      path: form.path.trim(),
    })
    form.path = ''
    ElMessage.success('Workspace 创建成功。')
    await reload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建 Workspace 失败。')
  } finally {
    submitting.value = false
  }
}

watch(selectedWorkspace, async (workspace) => {
  if (!workspace) {
    gitStatus.value = null
    gitDiff.value = null
    gitError.value = null
    return
  }
  gitLoading.value = true
  gitError.value = null
  try {
    gitStatus.value = await getWorkspaceGitStatus(workspace.workspaceId)
    gitDiff.value = await getWorkspaceGitDiff(workspace.workspaceId)
  } catch (error) {
    gitError.value = error instanceof Error ? error.message : '加载 Git 信息失败。'
  } finally {
    gitLoading.value = false
  }
})

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    READY: '就绪',
    LOCKED: '锁定',
    CLEANUP: '清理中',
    FAILED: '失败',
  }
  return labels[status] ?? status
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Git Workspace</p>
        <h1>Workspaces</h1>
        <p class="page-description">
          AI Agent 操作代码的工作区抽象：注册本地目录并查看 Git 状态，为开发任务提供标准 Workspace。
        </p>
      </div>
    </header>

    <el-card shadow="never" class="create-card">
      <template #header>
        <span class="card-title">注册 Workspace</span>
      </template>
      <el-form label-position="top" @submit.prevent="handleCreate">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="Project" required>
              <el-select v-model="form.projectId" placeholder="选择项目" style="width: 100%">
                <el-option
                  v-for="project in projects"
                  :key="project.projectId"
                  :label="`${project.name} (${project.projectId})`"
                  :value="project.projectId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="本地路径" required>
              <el-input v-model="form.path" placeholder="例如 /workspace/ai-dev-os" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-button type="primary" :loading="submitting" native-type="submit" class="create-button">
              创建 Workspace
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Workspace 列表</span>
          </template>
          <WorkspaceTable
            :workspaces="workspaces"
            :loading="loading"
            :selected-workspace-id="selectedWorkspace?.workspaceId ?? null"
            @select="selectedWorkspace = $event"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Workspace 详情</span>
          </template>
          <el-descriptions v-if="selectedWorkspace" :column="1" border size="small">
            <el-descriptions-item label="Workspace ID">
              <code>{{ selectedWorkspace.workspaceId }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="Project">
              {{ projectName(selectedWorkspace.projectId) }}
            </el-descriptions-item>
            <el-descriptions-item label="Path">
              <code>{{ selectedWorkspace.path }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="Branch">
              <code>{{ selectedWorkspace.gitStatus?.branch || '—' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="Status">
              {{ statusLabel(selectedWorkspace.status) }}
            </el-descriptions-item>
            <el-descriptions-item label="Created">
              {{ new Date(selectedWorkspace.createdAt).toLocaleString() }}
            </el-descriptions-item>
          </el-descriptions>
          <p v-else class="page-state">选择一个 Workspace 查看详情</p>

          <template v-if="selectedWorkspace">
            <el-divider>Git 状态</el-divider>
            <div v-loading="gitLoading" class="git-summary">
              <template v-if="gitError">
                <p class="page-state page-state--error">{{ gitError }}</p>
              </template>
              <template v-else-if="gitStatus">
                <div class="git-metric">
                  <span class="git-metric__value">{{ gitStatus.modified }}</span>
                  <span class="git-metric__label">Modified</span>
                </div>
                <div class="git-metric">
                  <span class="git-metric__value git-metric__value--added">{{ gitStatus.added }}</span>
                  <span class="git-metric__label">Added</span>
                </div>
                <div class="git-metric">
                  <span class="git-metric__value git-metric__value--deleted">{{ gitStatus.deleted }}</span>
                  <span class="git-metric__label">Deleted</span>
                </div>
              </template>
            </div>

            <el-divider>Git Diff</el-divider>
            <div v-if="gitDiff && gitDiff.filesChanged > 0" class="diff-summary">
              <p>
                {{ gitDiff.filesChanged }} files changed,
                {{ gitDiff.insertions }} insertions(+),
                {{ gitDiff.deletions }} deletions(-)
              </p>
              <pre class="diff-stat">{{ gitDiff.stat }}</pre>
            </div>
            <p v-else class="page-state">工作区无未提交变更</p>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.create-card {
  margin-bottom: 1rem;
}

.create-button {
  margin-top: 4px;
}

.card-title {
  font-weight: 700;
}

.content-row {
  margin-top: 0;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}

.git-summary {
  display: flex;
  gap: 1rem;
  min-height: 3rem;
}

.git-metric {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  padding: 0.75rem;
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: 8px;
}

.git-metric__value {
  font-size: 1.5rem;
  font-weight: 700;
}

.git-metric__value--added {
  color: var(--color-success, #67c23a);
}

.git-metric__value--deleted {
  color: var(--color-danger, #f56c6c);
}

.git-metric__label {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.diff-stat {
  max-height: 12rem;
  overflow: auto;
  padding: 0.75rem;
  background: var(--color-bg-muted, #f5f7fa);
  border-radius: 6px;
  font-size: 0.8rem;
  white-space: pre-wrap;
}
</style>
