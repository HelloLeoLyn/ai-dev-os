<script setup lang="ts">
import type { ModelProvider } from '../types/model'

defineProps<{
  models: ModelProvider[]
  loading?: boolean
}>()
</script>

<template>
  <el-table :data="models" v-loading="loading" stripe empty-text="暂无模型配置">
    <el-table-column label="Provider ID" min-width="120">
      <template #default="{ row }: { row: ModelProvider }">
        <code class="provider-id">{{ row.providerId }}</code>
      </template>
    </el-table-column>
    <el-table-column prop="name" label="名称" min-width="140" />
    <el-table-column label="类型" min-width="110">
      <template #default="{ row }: { row: ModelProvider }">
        <el-tag type="info" effect="plain" size="small">{{ row.type || '—' }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="模型" min-width="140">
      <template #default="{ row }: { row: ModelProvider }">
        <code>{{ row.model || '—' }}</code>
      </template>
    </el-table-column>
    <el-table-column label="状态" min-width="110">
      <template #default="{ row }: { row: ModelProvider }">
        <el-tag :type="row.enabled ? 'success' : 'danger'" effect="dark" size="small">
          {{ row.enabled ? 'enabled' : 'disabled' }}
        </el-tag>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.provider-id {
  color: var(--color-primary-strong);
  font-weight: 600;
}
</style>
