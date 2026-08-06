<script setup lang="ts">
import type { ModelRoute } from '../types/model'

defineProps<{
  routes: ModelRoute[]
  loading?: boolean
}>()

const labels: Record<string, string> = {
  TASK_ANALYSIS: '任务分析',
  CODE_GENERATION: '代码生成',
  BROWSER_TEST: '浏览器测试',
  GENERAL: '默认（兜底）',
}
</script>

<template>
  <el-table :data="routes" v-loading="loading" stripe empty-text="暂无路由规则">
    <el-table-column label="任务类型" min-width="180">
      <template #default="{ row }: { row: ModelRoute }">
        <span class="task-type">{{ labels[row.taskType] || row.taskType }}</span>
        <code class="task-type-code">{{ row.taskType }}</code>
      </template>
    </el-table-column>
    <el-table-column label="Provider" min-width="120">
      <template #default="{ row }: { row: ModelRoute }">
        <code>{{ row.providerId }}</code>
      </template>
    </el-table-column>
    <el-table-column label="模型" min-width="140">
      <template #default="{ row }: { row: ModelRoute }">
        <code>{{ row.model || '—' }}</code>
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="110">
      <template #default="{ row }: { row: ModelRoute }">
        <el-tag :type="row.enabled ? 'success' : 'danger'" effect="dark" size="small">
          {{ row.enabled ? 'enabled' : 'disabled' }}
        </el-tag>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.task-type {
  margin-right: 0.5rem;
}

.task-type-code {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}
</style>
