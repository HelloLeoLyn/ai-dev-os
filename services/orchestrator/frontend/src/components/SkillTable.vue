<script setup lang="ts">
import type { Skill } from '../types/skill'

defineProps<{
  skills: Skill[]
  loading?: boolean
  selectedSkillId?: string | null
}>()

const emit = defineEmits<{
  select: [skill: Skill]
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
  <el-table
    :data="skills"
    v-loading="loading"
    stripe
    empty-text="暂无 Skill"
    highlight-current-row
    :current-row-key="selectedSkillId"
    row-key="skillId"
    @current-change="(row: Skill | null) => row && emit('select', row)"
  >
    <el-table-column label="Skill ID" min-width="140">
      <template #default="{ row }: { row: Skill }">
        <code class="skill-id">{{ row.skillId }}</code>
      </template>
    </el-table-column>
    <el-table-column prop="name" label="名称" min-width="120" />
    <el-table-column label="类型" min-width="90">
      <template #default="{ row }: { row: Skill }">
        <el-tag size="small">{{ typeLabels[row.type] ?? row.type }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="版本" min-width="90">
      <template #default="{ row }: { row: Skill }">
        <code>{{ row.version || '—' }}</code>
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="90">
      <template #default="{ row }: { row: Skill }">
        <el-tag :type="row.enabled ? 'success' : 'info'" effect="dark" size="small">
          {{ row.enabled ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="Tools" min-width="80">
      <template #default="{ row }: { row: Skill }">
        {{ row.tools.length }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.skill-id {
  color: var(--color-primary-strong);
  font-weight: 600;
}
</style>
