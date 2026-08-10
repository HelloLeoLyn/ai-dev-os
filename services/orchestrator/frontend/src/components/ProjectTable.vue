<script setup lang="ts">
import { RouterLink } from 'vue-router'

import type { Project, ProjectStatus } from '../types/project'

defineProps<{
  projects: Project[]
  loading?: boolean
  currentProjectId?: string | null
}>()

const emit = defineEmits<{
  select: [project: Project]
}>()

function statusTone(status: ProjectStatus): 'success' | 'danger' | 'info' {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'ARCHIVED':
      return 'danger'
    default:
      return 'info'
  }
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <el-table
    :data="projects"
    v-loading="loading"
    stripe
    empty-text="暂无项目"
    highlight-current-row
    @current-change="(row: Project | null) => row && emit('select', row)"
  >
    <el-table-column label="名称" min-width="140">
      <template #default="{ row }: { row: Project }">
        <span class="project-name">{{ row.name }}</span>
        <el-tag v-if="row.projectId === currentProjectId" type="primary" size="small" class="current-tag">
          当前
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="Project ID" min-width="170">
      <template #default="{ row }: { row: Project }">
        <code>{{ row.projectId }}</code>
      </template>
    </el-table-column>
    <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip />
    <el-table-column label="仓库地址" min-width="200" show-overflow-tooltip>
      <template #default="{ row }: { row: Project }">
        <code>{{ row.repositoryUrl || '—' }}</code>
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="90">
      <template #default="{ row }: { row: Project }">
        <el-tag :type="statusTone(row.status)" effect="dark" size="small">
          {{ row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" min-width="150">
      <template #default="{ row }: { row: Project }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" min-width="90" fixed="right">
      <template #default="{ row }: { row: Project }">
        <RouterLink :to="`/projects/${row.projectId}`">详情</RouterLink>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.project-name {
  font-weight: 600;
}

.current-tag {
  margin-left: 0.5rem;
}
</style>
