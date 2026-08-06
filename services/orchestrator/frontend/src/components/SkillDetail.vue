<script setup lang="ts">
import type { Skill } from '../types/skill'

defineProps<{
  skill: Skill | null
  boundAgents: string[]
}>()

const emit = defineEmits<{
  enable: [skill: Skill]
  disable: [skill: Skill]
}>()

const typeLabels: Record<string, string> = {
  CODING: '编码',
  TESTING: '测试',
  BROWSER: '浏览器',
  ANALYSIS: '分析',
  DEPLOYMENT: '部署',
}
</script>

<template>
  <el-card v-if="skill" shadow="never" class="skill-detail">
    <template #header>
      <div class="detail-header">
        <span class="card-title">{{ skill.name }}</span>
        <el-tag :type="skill.enabled ? 'success' : 'info'" effect="dark" size="small">
          {{ skill.enabled ? '已启用' : '已禁用' }}
        </el-tag>
      </div>
    </template>

    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="Skill ID">
        <code>{{ skill.skillId }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="类型">
        <el-tag size="small">{{ typeLabels[skill.type] ?? skill.type }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="版本">
        <code>{{ skill.version || '—' }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="描述">
        {{ skill.description || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="绑定 Agent">
        <template v-if="boundAgents.length > 0">
          <el-tag
            v-for="agent in boundAgents"
            :key="agent"
            type="info"
            size="small"
            class="agent-tag"
          >
            {{ agent }}
          </el-tag>
        </template>
        <span v-else class="muted">—</span>
      </el-descriptions-item>
    </el-descriptions>

    <div class="section">
      <p class="section-title">Tools（{{ skill.tools.length }}）</p>
      <div v-if="skill.tools.length > 0" class="tool-list">
        <el-tag v-for="tool in skill.tools" :key="tool" size="small" effect="plain">
          <code>{{ tool }}</code>
        </el-tag>
      </div>
      <p v-else class="muted">无工具</p>
    </div>

    <div class="section">
      <p class="section-title">Instructions</p>
      <pre class="instructions">{{ skill.instructions || '—' }}</pre>
    </div>

    <div class="detail-actions">
      <el-button v-if="!skill.enabled" type="primary" size="small" @click="emit('enable', skill)">
        启用
      </el-button>
      <el-button v-else type="warning" size="small" @click="emit('disable', skill)">
        禁用
      </el-button>
    </div>
  </el-card>

  <el-empty v-else description="选择左侧 Skill 查看详情" />
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

.agent-tag {
  margin: 0.25rem 0.375rem 0.25rem 0;
}

.section {
  margin-top: 1rem;
}

.section-title {
  margin: 0 0 0.5rem;
  font-weight: 700;
}

.tool-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.instructions {
  margin: 0;
  padding: 0.75rem;
  background: var(--color-surface-muted);
  border-radius: 0.5rem;
  white-space: pre-wrap;
  font-size: 0.8125rem;
  line-height: 1.6;
}

.muted {
  color: var(--color-text-muted);
}

.detail-actions {
  margin-top: 1rem;
}
</style>
