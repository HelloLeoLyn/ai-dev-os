<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { AgentStatusDTO } from '../types/agent'
import StatusBadge from './StatusBadge.vue'
import TechnicalId from './TechnicalId.vue'

const props = defineProps<{
  agents: AgentStatusDTO[]
  loading?: boolean
}>()

const router = useRouter()

function openAgent(row: AgentStatusDTO): void {
  void router.push(`/agents/${encodeURIComponent(row.name || row.agentId)}`)
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <el-table
    :data="agents"
    v-loading="loading"
    stripe
    empty-text="暂无 Agent"
    highlight-current-row
    class="agent-table"
    @row-click="openAgent"
  >
    <el-table-column label="名称" min-width="150">
      <template #default="{ row }: { row: AgentStatusDTO }">
        <span class="agent-name">{{ row.name || '—' }}</span>
        <TechnicalId :value="row.agentId" label="Agent" />
      </template>
    </el-table-column>
    <el-table-column label="类型" min-width="110">
      <template #default="{ row }: { row: AgentStatusDTO }">
        {{ row.type || '—' }}
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="110">
      <template #default="{ row }: { row: AgentStatusDTO }">
        <StatusBadge :status="row.status" size="small" />
      </template>
    </el-table-column>
    <el-table-column label="能力" min-width="220">
      <template #default="{ row }: { row: AgentStatusDTO }">
        <div class="capability-list">
          <el-tag
            v-for="capability in row.capabilities"
            :key="capability"
            type="info"
            effect="plain"
            size="small"
          >
            {{ capability }}
          </el-tag>
          <span v-if="row.capabilities.length === 0">—</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="最近活动" min-width="170">
      <template #default="{ row }: { row: AgentStatusDTO }">
        {{ formatDate(row.lastHeartbeat) }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.agent-table :deep(.el-table__row) {
  cursor: pointer;
}

.agent-name {
  display: block;
  font-weight: 600;
}

.agent-id {
  display: block;
  margin-top: 0.15rem;
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.capability-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
</style>
