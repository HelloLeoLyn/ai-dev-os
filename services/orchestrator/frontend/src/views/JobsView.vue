<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getDashboardJobs } from '../api/dashboard'
import JobTable from '../components/JobTable.vue'
import AsyncState from '../components/AsyncState.vue'
import ConsoleCard from '../components/ConsoleCard.vue'
import type { JobSummaryDTO } from '../types/dashboard'

const statusOptions = ['QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'SUCCESS', 'FAILED',
  'RETRY_WAIT', 'CANCELLED', 'RECOVERY_REQUIRED']

const jobs = ref<JobSummaryDTO[]>([])
const selectedStatus = ref<string>('')
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const filteredJobs = computed(() => {
  if (!selectedStatus.value) {
    return jobs.value
  }
  return jobs.value.filter((job) => job.status === selectedStatus.value)
})

async function loadJobs(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    jobs.value = await getDashboardJobs()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load jobs.'
  } finally {
    loading.value = false
  }
}

onMounted(loadJobs)
</script>

<template>
  <section class="page-stack">
    <header class="page-header jobs-header">
      <div>
        <p class="page-eyebrow">Dashboard</p>
        <h1>Jobs 监控</h1>
        <p class="page-description">Job 运行状态、优先级、Lease 与时间信息。</p>
      </div>

      <el-select
        v-model="selectedStatus"
        class="status-filter"
        placeholder="全部状态"
        clearable
        @change="loadJobs"
      >
        <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
      </el-select>
    </header>

    <AsyncState :loading="loading" :error="errorMessage" :empty="!loading && filteredJobs.length === 0" empty-text="暂无匹配 Job" @retry="loadJobs">
      <ConsoleCard title="Jobs"><JobTable :jobs="filteredJobs" /></ConsoleCard>
    </AsyncState>
  </section>
</template>

<style scoped>
.jobs-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.status-filter {
  width: 14rem;
}

</style>
