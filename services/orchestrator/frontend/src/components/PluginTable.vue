<script setup lang="ts">
import type { McpPlugin } from '../types/mcpPlugin'

defineProps<{
  plugins: McpPlugin[]
  loading?: boolean
  selectedPluginId?: string | null
}>()

const emit = defineEmits<{
  select: [plugin: McpPlugin]
}>()

function permissionLabel(plugin: McpPlugin): string {
  return plugin.permissionLevel === 'workspace-write' ? '需确认' : '只读'
}
</script>

<template>
  <el-table
    :data="plugins"
    v-loading="loading"
    stripe
    empty-text="暂无 MCP 插件"
    highlight-current-row
    :current-row-key="selectedPluginId"
    row-key="pluginId"
    @current-change="(row: McpPlugin | null) => row && emit('select', row)"
  >
    <el-table-column label="Plugin ID" min-width="130">
      <template #default="{ row }: { row: McpPlugin }">
        <code class="plugin-id">{{ row.pluginId }}</code>
      </template>
    </el-table-column>
    <el-table-column prop="name" label="名称" min-width="120" />
    <el-table-column prop="type" label="类型" min-width="100" />
    <el-table-column label="状态" min-width="90">
      <template #default="{ row }: { row: McpPlugin }">
        <el-tag :type="row.enabled ? 'success' : 'info'" effect="dark" size="small">
          {{ row.enabled ? '启用' : '禁用' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="权限" min-width="90">
      <template #default="{ row }: { row: McpPlugin }">
        <el-tag :type="row.permissionLevel === 'workspace-write' ? 'warning' : 'info'" size="small">
          {{ permissionLabel(row) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="Tools" min-width="80">
      <template #default="{ row }: { row: McpPlugin }">
        {{ row.tools.length }}
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.plugin-id {
  color: var(--color-primary-strong);
  font-weight: 600;
}
</style>
