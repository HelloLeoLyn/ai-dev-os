<script setup lang="ts">
import { computed } from 'vue'
import type { ExecutionStatistics } from '../types/dashboard'

const props = defineProps<{
  executions: ExecutionStatistics | null
}>()

const progress = computed(() => props.executions?.successRate ?? 0)
</script>

<template>
  <el-card shadow="never" class="dashboard-card">
    <template #header>
      <span class="card-title">Execution 统计</span>
    </template>
    <div class="card-body">
      <div class="stat">
        <span class="stat-label">总数</span>
        <span class="stat-value">{{ executions?.total ?? 0 }}</span>
      </div>
      <div class="stat">
        <span class="stat-label">成功</span>
        <span class="stat-value stat-value--success">{{ executions?.successful ?? 0 }}</span>
      </div>
      <div class="stat">
        <span class="stat-label">失败</span>
        <span class="stat-value stat-value--danger">{{ executions?.failed ?? 0 }}</span>
      </div>
      <div class="stat">
        <span class="stat-label">未知</span>
        <span class="stat-value">{{ executions?.unknown ?? 0 }}</span>
      </div>
      <el-progress
        :percentage="progress"
        :stroke-width="10"
        :color="progress >= 90 ? '#51d6a3' : progress >= 60 ? '#66c7ff' : '#ff7b8b'"
      />
      <p class="progress-caption">成功率 {{ progress.toFixed(1) }}%</p>
    </div>
  </el-card>
</template>

<style scoped>
.card-title {
  font-weight: 700;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.stat-label {
  color: var(--color-text-muted, #9aa8c2);
  font-size: 0.85rem;
}

.stat-value {
  font-size: 1.35rem;
  font-weight: 700;
}

.stat-value--success {
  color: #51d6a3;
}

.stat-value--danger {
  color: #ff7b8b;
}

.progress-caption {
  margin: 0.25rem 0 0;
  color: var(--color-text-muted, #9aa8c2);
  font-size: 0.8rem;
  text-align: right;
}
</style>
