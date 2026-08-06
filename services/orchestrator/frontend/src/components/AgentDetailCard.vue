<script setup lang="ts">
import { computed } from 'vue'
import type { AgentDetailDTO, AgentRuntimeStatus } from '../types/agent'

const props = defineProps<{
  agent: AgentDetailDTO | null
  loading?: boolean
}>()

const configurationEntries = computed(() => {
  if (!props.agent) {
    return []
  }
  return Object.entries(props.agent.configuration)
})

function statusType(status: AgentRuntimeStatus): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'ONLINE':
      return 'success'
    case 'RUNNING':
      return 'info'
    case 'IDLE':
      return 'warning'
    case 'ERROR':
    case 'DISABLED':
      return 'danger'
    default:
      return 'info'
  }
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
  <el-card v-loading="loading" shadow="never">
    <template #header>
      <div class="detail-header">
        <div>
          <p class="page-eyebrow">Agent Detail</p>
          <h2 class="card-title">{{ agent?.name || 'Agent' }}</h2>
          <code v-if="agent" class="agent-id">{{ agent.agentId }}</code>
        </div>
        <el-tag v-if="agent" :type="statusType(agent.status)" effect="dark" size="large">
          {{ agent.status }}
        </el-tag>
      </div>
    </template>

    <el-empty v-if="!agent && !loading" description="Agent 不存在" />

    <template v-else-if="agent">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="类型">
          {{ agent.type || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="最近活动">
          {{ formatDate(agent.lastActivity) }}
        </el-descriptions-item>
        <el-descriptions-item label="能力" :span="2">
          <div class="capability-list">
            <el-tag
              v-for="capability in agent.capabilities"
              :key="capability"
              type="info"
              effect="plain"
              size="small"
            >
              {{ capability }}
            </el-tag>
            <span v-if="agent.capabilities.length === 0">—</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="配置" :span="2">
          <dl class="config-list">
            <template v-if="configurationEntries.length > 0">
              <div v-for="[key, value] in configurationEntries" :key="key">
                <dt>{{ key }}</dt>
                <dd>{{ value === null ? 'null' : String(value) }}</dd>
              </div>
            </template>
            <p v-else class="muted">无配置</p>
          </dl>
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </el-card>
</template>

<style scoped>
.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.card-title {
  margin: 0;
}

.agent-id {
  display: inline-block;
  margin-top: 0.25rem;
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.capability-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.config-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 0.5rem 1.5rem;
  margin: 0;
}

.config-list dt {
  color: var(--color-text-muted);
  font-size: 0.78rem;
}

.config-list dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.muted {
  margin: 0;
  color: var(--color-text-muted);
}
</style>
