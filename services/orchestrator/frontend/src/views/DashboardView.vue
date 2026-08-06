<script setup lang="ts">
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import {
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import { getDashboardSummary } from '../api/dashboard'
import ExecutionSummaryCard from '../components/ExecutionSummaryCard.vue'
import HealthCard from '../components/HealthCard.vue'
import JobSummaryCard from '../components/JobSummaryCard.vue'
import RecoveryCard from '../components/RecoveryCard.vue'
import type { DashboardSummaryDTO } from '../types/dashboard'

echarts.use([
  PieChart,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
])

const summary = ref<DashboardSummaryDTO | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const chartElement = ref<HTMLDivElement | null>(null)

let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const agentReadyRate = computed(() => {
  const agents = summary.value?.agents
  if (!agents || agents.total === 0) {
    return 0
  }
  return Math.round((agents.enabled / agents.total) * 100)
})

function renderChart(data: DashboardSummaryDTO): void {
  if (!chartElement.value) {
    return
  }

  chart = echarts.init(chartElement.value)
  chart.setOption({
    color: ['#66c7ff', '#51d6a3', '#ff7b8b', '#9aa8c2'],
    tooltip: { trigger: 'item' },
    legend: {
      bottom: 0,
      textStyle: { color: '#9aa8c2' },
    },
    series: [
      {
        name: 'Jobs',
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '43%'],
        label: { color: '#edf2ff', formatter: '{b}: {c}' },
        data: [
          { name: 'QUEUED', value: data.jobs.queued },
          { name: 'RUNNING', value: data.jobs.running },
          { name: 'SUCCESS', value: data.jobs.succeeded },
          { name: 'FAILED', value: data.jobs.failed },
        ],
      },
    ],
  })

  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartElement.value)
}

async function loadDashboard(): Promise<void> {
  try {
    const data = await getDashboardSummary()
    summary.value = data
    await nextTick()
    renderChart(data)
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Unable to load dashboard data.'
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">AI Dev OS</p>
        <h1>Dashboard</h1>
      </div>
      <el-tag type="info" effect="dark">Live snapshot</el-tag>
    </header>

    <el-card v-if="loading" shadow="never">
      <p class="dashboard-state muted">Loading dashboard…</p>
    </el-card>

    <el-card v-else-if="errorMessage" shadow="never">
      <p class="dashboard-state dashboard-state--error">{{ errorMessage }}</p>
    </el-card>

    <template v-else-if="summary">
      <el-row :gutter="16" class="cards-row">
        <el-col :xs="24" :sm="12" :lg="6">
          <HealthCard :health="summary.health" />
        </el-col>
        <el-col :xs="24" :sm="12" :lg="6">
          <JobSummaryCard :jobs="summary.jobs" />
        </el-col>
        <el-col :xs="24" :sm="12" :lg="6">
          <ExecutionSummaryCard :executions="summary.executions" />
        </el-col>
        <el-col :xs="24" :sm="12" :lg="6">
          <RecoveryCard :recovery="summary.recovery" />
        </el-col>
      </el-row>

      <el-row :gutter="16" class="cards-row">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" class="dashboard-card">
            <template #header>
              <span class="card-title">Job 分布</span>
            </template>
            <div ref="chartElement" class="job-chart" />
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" class="dashboard-card">
            <template #header>
              <span class="card-title">Agent 概览</span>
            </template>
            <div class="agent-summary">
              <div class="stat">
                <span class="stat-label">Agent 总数</span>
                <span class="stat-value">{{ summary.agents.total }}</span>
              </div>
              <div class="stat">
                <span class="stat-label">已启用</span>
                <span class="stat-value stat-value--success">
                  {{ summary.agents.enabled }}
                </span>
              </div>
              <el-progress
                :percentage="agentReadyRate"
                :stroke-width="10"
                color="#66c7ff"
              />
              <p class="progress-caption">启用率 {{ agentReadyRate }}%</p>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </section>
</template>

<style scoped>
.cards-row {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 700;
}

.job-chart {
  width: 100%;
  min-height: 22rem;
}

.agent-summary {
  display: flex;
  min-height: 22rem;
  flex-direction: column;
  gap: 0.75rem;
}

.stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.stat-label {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
}

.stat-value--success {
  color: var(--color-success);
}

.progress-caption {
  margin: 0.25rem 0 0;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  text-align: right;
}

.dashboard-state {
  color: var(--color-text-muted);
  text-align: center;
}

.dashboard-state--error {
  color: var(--color-danger);
}
</style>
