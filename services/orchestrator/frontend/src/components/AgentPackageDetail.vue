<script setup lang="ts">
import type { AgentPackage } from '../types/agentPackage'

defineProps<{
  agentPackage: AgentPackage | null
}>()

const emit = defineEmits<{
  install: [agentPackage: AgentPackage]
  uninstall: [agentPackage: AgentPackage]
}>()
</script>

<template>
  <el-card v-if="agentPackage" shadow="never" class="package-detail">
    <template #header>
      <div class="detail-header">
        <span class="card-title">{{ agentPackage.name }}</span>
        <el-tag :type="agentPackage.installed ? 'success' : 'info'" effect="dark" size="small">
          {{ agentPackage.installed ? '已安装' : '未安装' }}
        </el-tag>
      </div>
    </template>

    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="Agent ID">
        <code>{{ agentPackage.agentId }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="版本">
        <code>{{ agentPackage.version || '—' }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="作者">
        {{ agentPackage.author || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="执行器">
        <code>{{ agentPackage.executor || '—' }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="描述">
        {{ agentPackage.description || '—' }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="section">
      <p class="section-title">Capabilities</p>
      <div v-if="agentPackage.capabilities.length > 0" class="tag-list">
        <el-tag
          v-for="capability in agentPackage.capabilities"
          :key="capability"
          size="small"
          effect="plain"
        >
          {{ capability }}
        </el-tag>
      </div>
      <p v-else class="muted">—</p>
    </div>

    <div class="section">
      <p class="section-title">Skills</p>
      <div v-if="agentPackage.skills.length > 0" class="tag-list">
        <el-tag
          v-for="skill in agentPackage.skills"
          :key="skill"
          size="small"
          type="success"
          effect="plain"
        >
          <code>{{ skill }}</code>
        </el-tag>
      </div>
      <p v-else class="muted">—</p>
    </div>

    <div class="section">
      <p class="section-title">MCP Plugins</p>
      <div v-if="agentPackage.plugins.length > 0" class="tag-list">
        <el-tag
          v-for="plugin in agentPackage.plugins"
          :key="plugin"
          size="small"
          type="warning"
          effect="plain"
        >
          <code>{{ plugin }}</code>
        </el-tag>
      </div>
      <p v-else class="muted">—</p>
    </div>

    <div class="detail-actions">
      <el-button
        v-if="!agentPackage.installed"
        type="primary"
        size="small"
        @click="emit('install', agentPackage)"
      >
        安装
      </el-button>
      <el-button
        v-else
        type="danger"
        size="small"
        @click="emit('uninstall', agentPackage)"
      >
        卸载
      </el-button>
    </div>
  </el-card>

  <el-empty v-else description="选择左侧 Agent 包查看详情" />
</template>

<style scoped>
.card-title {
  font-weight: 700;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.section {
  margin-top: 1rem;
}

.section-title {
  margin: 0 0 0.5rem;
  font-weight: 700;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.muted {
  color: var(--color-text-muted);
}

.detail-actions {
  margin-top: 1rem;
}
</style>
