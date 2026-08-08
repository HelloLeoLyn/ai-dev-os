<script setup lang="ts">
import type { GitStatus, Workspace } from '../types/workspace'

export interface WorkspaceRow extends Workspace {
  gitStatus?: GitStatus
}

defineProps<{
  workspaces: WorkspaceRow[]
  loading?: boolean
  selectedWorkspaceId?: string | null
}>()

const emit = defineEmits<{
  select: [workspace: WorkspaceRow]
}>()

const statusLabels: Record<string, string> = {
  READY: '就绪',
  LOCKED: '锁定',
  CLEANUP: '清理中',
  FAILED: '失败',
}

const statusTypes: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  READY: 'success',
  LOCKED: 'warning',
  CLEANUP: 'info',
  FAILED: 'danger',
}
</script>

<template>
  <el-table
    :data="workspaces"
    v-loading="loading"
    stripe
    empty-text="暂无 Workspace"
    highlight-current-row
    :current-row-key="selectedWorkspaceId"
    row-key="workspaceId"
    @current-change="(row: WorkspaceRow | null) => row && emit('select', row)"
  >
    <el-table-column label="Workspace ID" min-width="160">
      <template #default="{ row }: { row: WorkspaceRow }">
        <code class="workspace-id">{{ row.workspaceId }}</code>
      </template>
    </el-table-column>
    <el-table-column label="Project" min-width="120">
      <template #default="{ row }: { row: WorkspaceRow }">
        <code>{{ row.projectId }}</code>
      </template>
    </el-table-column>
    <el-table-column label="Path" min-width="200">
      <template #default="{ row }: { row: WorkspaceRow }">
        <code>{{ row.path }}</code>
      </template>
    </el-table-column>
    <el-table-column label="Branch" min-width="90">
      <template #default="{ row }: { row: WorkspaceRow }">
        <code>{{ row.branch || '—' }}</code>
      </template>
    </el-table-column>
    <el-table-column label="Status" min-width="90">
      <template #default="{ row }: { row: WorkspaceRow }">
        <el-tag :type="statusTypes[row.status] ?? 'info'" effect="dark" size="small">
          {{ statusLabels[row.status] ?? row.status }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="Changes" min-width="130">
      <template #default="{ row }: { row: WorkspaceRow }">
        <span class="change-count">{{ row.gitStatus?.modified ?? 0 }}</span> M
        <span class="change-count change-count--added">{{ row.gitStatus?.added ?? 0 }}</span> A
        <span class="change-count change-count--deleted">{{ row.gitStatus?.deleted ?? 0 }}</span> D
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.workspace-id {
  color: var(--color-primary-strong);
  font-weight: 600;
}

.change-count {
  font-weight: 600;
}

.change-count--added {
  color: var(--color-success, #67c23a);
}

.change-count--deleted {
  color: var(--color-danger, #f56c6c);
}
</style>
