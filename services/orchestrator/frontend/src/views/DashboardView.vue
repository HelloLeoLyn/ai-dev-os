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

import { getDashboard } from '../api/dashboard'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { DashboardSummary } from '../types/dashboard'
import type { JobStatus } from '../types/job'

echarts.use([
  PieChart,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
])

const summary = ref<DashboardSummary | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const chartElement = ref<HTMLDivElement | null>(null)

let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const statistics = computed(() => {
  const jobs = summary.value?.jobs

  return [
    { label: 'Tasks', value: summary.value?.tasks.total ?? 0 },
    { label: 'Jobs', value: jobs?.total ?? 0 },
    { label: 'Running Jobs', value: jobs?.running ?? 0 },
    {
      label: 'Success Rate',
      value: `${((jobs?.successRate ?? 0) * 100).toFixed(1)}%`,
    },
    { label: 'Failed Jobs', value: jobs?.failed ?? 0 },
  ]
})

function statusTone(status: JobStatus): 'neutral' | 'info' | 'success' | 'danger' {
  switch (status) {
    case 'RUNNING':
      return 'info'
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'neutral'
  }
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function renderChart(data: DashboardSummary): void {
  if (!chartElement.value) {
    return
  }

  chart = echarts.init(chartElement.value)
  chart.setOption({
    color: ['#9aa8c2', '#66c7ff', '#51d6a3', '#ff7b8b'],
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
          { name: 'SUCCEEDED', value: data.jobs.succeeded },
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
    const data = await getDashboard()
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
      <StatusBadge tone="info">Live snapshot</StatusBadge>
    </header>

    <BaseCard v-if="loading">
      <p class="dashboard-state muted">Loading dashboard…</p>
    </BaseCard>

    <BaseCard v-else-if="errorMessage">
      <p class="dashboard-state dashboard-state--error">{{ errorMessage }}</p>
    </BaseCard>

    <template v-else-if="summary">
      <div class="statistics-grid">
        <BaseCard v-for="statistic in statistics" :key="statistic.label">
          <p class="statistic-label">{{ statistic.label }}</p>
          <strong class="statistic-value">{{ statistic.value }}</strong>
        </BaseCard>
      </div>

      <BaseCard>
        <div class="section-heading">
          <div>
            <p class="page-eyebrow">Distribution</p>
            <h2>Job status</h2>
          </div>
          <span class="muted">{{ summary.jobs.total }} total</span>
        </div>
        <div ref="chartElement" class="job-chart" aria-label="Job status chart" />
      </BaseCard>

      <BaseCard>
        <div class="section-heading">
          <div>
            <p class="page-eyebrow">Latest activity</p>
            <h2>Recent jobs</h2>
          </div>
          <span class="muted">Generated {{ formatDate(summary.generatedAt) }}</span>
        </div>

        <div class="table-scroll">
          <table class="jobs-table">
            <thead>
              <tr>
                <th>Task</th>
                <th>Status</th>
                <th>Created</th>
                <th>Completed</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="summary.recentJobs.length === 0">
                <td colspan="5" class="empty-cell">No recent jobs.</td>
              </tr>
              <tr v-for="job in summary.recentJobs" :key="job.id">
                <td class="task-id">{{ job.taskId }}</td>
                <td>
                  <StatusBadge :tone="statusTone(job.status)">
                    {{ job.status }}
                  </StatusBadge>
                </td>
                <td>{{ formatDate(job.createdAt) }}</td>
                <td>{{ formatDate(job.completedAt) }}</td>
                <td class="result-summary">{{ job.resultSummary || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </BaseCard>
    </template>
  </section>
</template>

<style scoped>
.statistics-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 1rem;
}

.statistic-label {
  margin: 0 0 0.75rem;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.statistic-value {
  font-size: clamp(1.8rem, 4vw, 2.75rem);
  letter-spacing: -0.04em;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.section-heading h2 {
  margin: 0;
}

.job-chart {
  width: 100%;
  min-height: 22rem;
}

.table-scroll {
  overflow-x: auto;
}

.jobs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
  text-align: left;
}

.jobs-table th,
.jobs-table td {
  padding: 0.9rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  vertical-align: top;
}

.jobs-table th {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.jobs-table tbody tr:last-child td {
  border-bottom: 0;
}

.task-id {
  color: var(--color-primary-strong);
  font-weight: 700;
}

.result-summary {
  max-width: 28rem;
  overflow-wrap: anywhere;
}

.empty-cell,
.dashboard-state {
  color: var(--color-text-muted);
  text-align: center;
}

.dashboard-state--error {
  color: var(--color-danger);
}

@media (max-width: 1100px) {
  .statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .statistics-grid {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
