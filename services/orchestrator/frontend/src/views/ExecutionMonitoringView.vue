<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getDashboardExecutions } from '../api/dashboard'
import ExecutionTable from '../components/ExecutionTable.vue'
import type { ExecutionSummaryDTO } from '../types/dashboard'

const statusOptions = ['SUCCESS', 'FAILED', 'WAITING_APPROVAL', 'STARTING', 'RUNNING',
  'ABANDONED', 'RECOVERY_REQUIRED']

const executions = ref<ExecutionSummaryDTO[]>([])
const selectedStatus = ref<string>('')
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const filteredExecutions = computed(() => {
  if (!selectedStatus.value) {
    return executions.value
  }
  return executions.value.filter((execution) => execution.status === selectedStatus.value)
})

async function loadExecutions(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    executions.value = await getDashboardExecutions()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Unable to load executions.'
  } finally {
    loading.value = false
  }
}

onMounted(loadExecutions)
</script>

<template>
  <section class="page-stack">
    <header class="page-header executions-header">
      <div>
        <p class="page-eyebrow">Dashboard</p>
        <h1>Execution 监控</h1>
        <p class="page-description">Execution 状态、尝试次数与失败原因。</p>
      </div>

      <el-select
        v-model="selectedStatus"
        class="status-filter"
        placeholder="全部状态"
        clearable
        @change="loadExecutions"
      >
        <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
      </el-select>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-card v-else shadow="never">
      <ExecutionTable :executions="filteredExecutions" :loading="loading" />
    </el-card>
  </section>
</template>

<style scoped>
.executions-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.status-filter {
  width: 14rem;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
