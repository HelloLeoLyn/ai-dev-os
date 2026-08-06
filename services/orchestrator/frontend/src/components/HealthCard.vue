<script setup lang="ts">
import { computed } from 'vue'
import type { HealthSummary } from '../types/dashboard'

const props = defineProps<{
  health: HealthSummary | null
}>()

const statusType = computed(() => {
  if (!props.health) {
    return 'info'
  }
  return props.health.ready ? 'success' : 'warning'
})

const statusLabel = computed(() => {
  if (!props.health) {
    return '—'
  }
  return props.health.ready ? 'READY' : 'NOT_READY'
})
</script>

<template>
  <el-card shadow="never" class="dashboard-card">
    <template #header>
      <span class="card-title">系统健康</span>
    </template>
    <div class="card-body">
      <p class="card-value">{{ health?.status ?? '—' }}</p>
      <el-tag :type="statusType" size="large" effect="dark">
        {{ statusLabel }}
      </el-tag>
    </div>
  </el-card>
</template>

<style scoped>
.card-title {
  font-weight: 700;
}

.card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.card-value {
  margin: 0;
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.03em;
}
</style>
