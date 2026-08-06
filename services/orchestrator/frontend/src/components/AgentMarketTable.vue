<script setup lang="ts">
import type { AgentPackage } from '../types/agentPackage'

defineProps<{
  packages: AgentPackage[]
  loading?: boolean
  selectedAgentId?: string | null
}>()

const emit = defineEmits<{
  select: [agentPackage: AgentPackage]
}>()
</script>

<template>
  <el-table
    :data="packages"
    v-loading="loading"
    stripe
    empty-text="暂无 Agent 包"
    highlight-current-row
    :current-row-key="selectedAgentId"
    row-key="agentId"
    @current-change="(row: AgentPackage | null) => row && emit('select', row)"
  >
    <el-table-column label="Agent ID" min-width="140">
      <template #default="{ row }: { row: AgentPackage }">
        <code class="agent-id">{{ row.agentId }}</code>
      </template>
    </el-table-column>
    <el-table-column prop="name" label="名称" min-width="120" />
    <el-table-column label="版本" min-width="90">
      <template #default="{ row }: { row: AgentPackage }">
        <code>{{ row.version || '—' }}</code>
      </template>
    </el-table-column>
    <el-table-column label="能力" min-width="160">
      <template #default="{ row }: { row: AgentPackage }">
        <el-tag
          v-for="capability in row.capabilities"
          :key="capability"
          size="small"
          effect="plain"
          class="capability-tag"
        >
          {{ capability }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="Skills" min-width="80">
      <template #default="{ row }: { row: AgentPackage }">
        {{ row.skills.length }}
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="100">
      <template #default="{ row }: { row: AgentPackage }">
        <el-tag :type="row.installed ? 'success' : 'info'" effect="dark" size="small">
          {{ row.installed ? '已安装' : '未安装' }}
        </el-tag>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.agent-id {
  color: var(--color-primary-strong);
  font-weight: 600;
}

.capability-tag {
  margin: 0.125rem 0.25rem 0.125rem 0;
}
</style>
