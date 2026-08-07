<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { archiveProject, createProject, getProjects, setProjectActive } from '../api/projects'
import { useRegistryList } from '../composables/useRegistryList'
import ProjectSelector from '../components/ProjectSelector.vue'
import ProjectTable from '../components/ProjectTable.vue'
import type { Project } from '../types/project'

const submitting = ref(false)
const {
  items: projects,
  selected: selectedProject,
  loading,
  errorMessage,
  reload,
} = useRegistryList<Project>({
  fetch: getProjects,
  idOf: (project) => project.projectId,
  errorText: '无法加载项目。',
  reselectOnReload: false,
})

const form = reactive({
  name: '',
  path: '',
  description: '',
})

async function handleCreate(): Promise<void> {
  if (!form.name.trim() || !form.path.trim()) {
    ElMessage.warning('项目名称和路径为必填项。')
    return
  }
  submitting.value = true
  try {
    await createProject({
      name: form.name.trim(),
      path: form.path.trim(),
      description: form.description.trim() || undefined,
    })
    form.name = ''
    form.path = ''
    form.description = ''
    ElMessage.success('项目创建成功。')
    await reload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建项目失败。')
  } finally {
    submitting.value = false
  }
}

async function handleSwitch(projectId: string): Promise<void> {
  try {
    const project = await setProjectActive(projectId)
    ElMessage.success(`已切换到项目「${project.name}」。`)
    await reload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '切换项目失败。')
  }
}

async function handleArchive(project: Project): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认归档项目「${project.name}」？归档后不再作为当前项目。`,
      '归档确认',
      { confirmButtonText: '归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await archiveProject(project.projectId)
    ElMessage.success(`项目「${project.name}」已归档。`)
    await reload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '归档项目失败。')
  }
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header projects-header">
      <div>
        <p class="page-eyebrow">Multi-Project</p>
        <h1>Projects</h1>
        <p class="page-description">
          多项目管理：Task / Memory / Agent 执行以项目为隔离边界。
        </p>
      </div>
      <ProjectSelector
        :projects="projects"
        :current-project-id="projects.find((project) => project.status === 'ACTIVE')?.projectId ?? null"
        :loading="loading"
        @switch-project="handleSwitch"
      />
    </header>

    <el-card shadow="never" class="create-card">
      <template #header>
        <span class="card-title">创建项目</span>
      </template>
      <el-form label-position="top" @submit.prevent="handleCreate">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="项目名称" required>
              <el-input v-model="form.name" placeholder="例如 AI Dev OS" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="路径" required>
              <el-input v-model="form.path" placeholder="例如 /workspace/ai-dev-os" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="描述（可选）">
              <el-input v-model="form.description" placeholder="项目描述" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-button type="primary" :loading="submitting" native-type="submit">
          创建项目
        </el-button>
      </el-form>
    </el-card>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-card v-else shadow="never">
      <template #header>
        <div class="list-header">
          <span class="card-title">项目列表</span>
          <el-button
            v-if="selectedProject && selectedProject.status !== 'ARCHIVED'"
            type="warning"
            size="small"
            @click="handleArchive(selectedProject)"
          >
            归档
          </el-button>
        </div>
      </template>
      <ProjectTable
        :projects="projects"
        :loading="loading"
        :current-project-id="projects.find((project) => project.status === 'ACTIVE')?.projectId ?? null"
        @select="selectedProject = $event"
      />
    </el-card>
  </section>
</template>

<style scoped>
.projects-header {
  align-items: flex-start;
}

.create-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 700;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
