<script setup lang="ts">
withDefaults(defineProps<{ loading?: boolean; error?: string | null; empty?: boolean; emptyText?: string }>(), {
  loading: false, error: null, empty: false, emptyText: '暂无数据',
})
defineEmits<{ retry: [] }>()
</script>

<template>
  <el-card v-if="loading" shadow="never" class="async-state"><el-skeleton :rows="4" animated /></el-card>
  <el-alert v-else-if="error" class="async-state" type="error" :title="error" show-icon :closable="false">
    <template #default><el-button size="small" type="danger" plain @click="$emit('retry')">Retry</el-button></template>
  </el-alert>
  <el-empty v-else-if="empty" :description="emptyText" :image-size="64" />
  <slot v-else />
</template>

<style scoped>.async-state{width:100%;min-width:0}</style>
