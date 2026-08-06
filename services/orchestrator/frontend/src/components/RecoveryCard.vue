<script setup lang="ts">
import { computed } from 'vue'
import type { RecoverySummary } from '../types/dashboard'

const props = defineProps<{
  recovery: RecoverySummary | null
}>()

const pending = computed(() => props.recovery?.pending ?? 0)
const tone = computed(() => (pending.value > 0 ? 'warning' : 'success'))
</script>

<template>
  <el-card shadow="never" class="dashboard-card">
    <template #header>
      <span class="card-title">Recovery 状态</span>
    </template>
    <div class="card-body">
      <p class="card-value">{{ pending }}</p>
      <el-tag :type="tone" size="large" effect="dark">
        {{ pending > 0 ? '待恢复' : '正常' }}
      </el-tag>
      <p class="card-hint">等待恢复的 Job 数量</p>
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

.card-value {
  margin: 0;
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.card-hint {
  margin: 0;
  color: var(--color-text-muted, #9aa8c2);
  font-size: 0.8rem;
}
</style>
