<script setup lang="ts">
import type { McpPlugin, McpPluginTool } from '../types/mcpPlugin'

defineProps<{
  plugin: McpPlugin | null
}>()

const emit = defineEmits<{
  enable: [plugin: McpPlugin]
  disable: [plugin: McpPlugin]
}>()

function accessLabel(tool: McpPluginTool): string {
  return tool.access === 'WORKSPACE_WRITE' ? '写' : '读'
}
</script>

<template>
  <el-card v-if="plugin" shadow="never" class="plugin-detail">
    <template #header>
      <div class="detail-header">
        <span class="card-title">{{ plugin.name }}</span>
        <el-tag :type="plugin.enabled ? 'success' : 'info'" effect="dark" size="small">
          {{ plugin.enabled ? '已启用' : '已禁用' }}
        </el-tag>
      </div>
    </template>

    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="Plugin ID">
        <code>{{ plugin.pluginId }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="类型">
        {{ plugin.type }}
      </el-descriptions-item>
      <el-descriptions-item label="权限">
        <el-tag
          :type="plugin.permissionLevel === 'workspace-write' ? 'warning' : 'info'"
          size="small"
        >
          {{ plugin.permissionLevel === 'workspace-write' ? '需确认（默认只读）' : '只读' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="描述">
        {{ plugin.description || '—' }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="tool-section">
      <p class="tool-title">Tools（{{ plugin.tools.length }}）</p>
      <el-table :data="plugin.tools" stripe empty-text="无工具">
        <el-table-column prop="name" label="名称" min-width="140">
          <template #default="{ row }: { row: McpPluginTool }">
            <code>{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column label="访问" min-width="80">
          <template #default="{ row }: { row: McpPluginTool }">
            <el-tag :type="row.access === 'WORKSPACE_WRITE' ? 'warning' : 'info'" size="small">
              {{ accessLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="危险" min-width="80">
          <template #default="{ row }: { row: McpPluginTool }">
            <el-tag v-if="row.dangerous" type="danger" size="small">需确认</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
          <template #default="{ row }: { row: McpPluginTool }">
            {{ row.description || '—' }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="detail-actions">
      <el-button v-if="!plugin.enabled" type="primary" size="small" @click="emit('enable', plugin)">
        启用
      </el-button>
      <el-button v-else type="warning" size="small" @click="emit('disable', plugin)">
        禁用
      </el-button>
    </div>
  </el-card>

  <el-empty v-else description="选择左侧插件查看详情" />
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

.tool-section {
  margin-top: 1rem;
}

.tool-title {
  margin: 0 0 0.5rem;
  font-weight: 700;
}

.muted {
  color: var(--color-text-muted);
}

.detail-actions {
  margin-top: 1rem;
}
</style>
