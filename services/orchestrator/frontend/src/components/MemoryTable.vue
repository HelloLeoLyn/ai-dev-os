<script setup lang="ts">
import type { MemoryRecord, MemoryType } from '../types/memory'

defineProps<{
  memories: MemoryRecord[]
  loading?: boolean
}>()

const emit = defineEmits<{
  delete: [record: MemoryRecord]
}>()

const typeLabels: Record<MemoryType, string> = {
  PROJECT_RULE: '项目规则',
  HISTORY_TASK: '历史任务',
  BUG_RECORD: 'Bug 记录',
  AGENT_EXPERIENCE: 'Agent 经验',
}

function typeTone(type: MemoryType): 'primary' | 'success' | 'danger' | 'warning' {
  switch (type) {
    case 'PROJECT_RULE':
      return 'primary'
    case 'HISTORY_TASK':
      return 'success'
    case 'BUG_RECORD':
      return 'danger'
    case 'AGENT_EXPERIENCE':
      return 'warning'
  }
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <el-table :data="memories" v-loading="loading" stripe empty-text="暂无 Memory">
    <el-table-column prop="key" label="Key" min-width="140">
      <template #default="{ row }: { row: MemoryRecord }">
        <code class="memory-key">{{ row.key }}</code>
      </template>
    </el-table-column>
    <el-table-column label="类型" min-width="120">
      <template #default="{ row }: { row: MemoryRecord }">
        <el-tag :type="typeTone(row.type)" effect="dark" size="small">
          {{ typeLabels[row.type] || row.type }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="projectId" label="项目" min-width="110" />
    <el-table-column label="内容" min-width="260">
      <template #default="{ row }: { row: MemoryRecord }">
        <span class="memory-content">{{ row.content }}</span>
      </template>
    </el-table-column>
    <el-table-column label="创建时间" min-width="160">
      <template #default="{ row }: { row: MemoryRecord }">
        {{ formatDate(row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="90" fixed="right">
      <template #default="{ row }: { row: MemoryRecord }">
        <el-button
          type="danger"
          link
          size="small"
          @click="emit('delete', row)"
        >
          删除
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.memory-key {
  color: var(--color-primary-strong);
  font-weight: 600;
}

.memory-content {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
