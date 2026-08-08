<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { getAgentMetrics, getAgentMetricsDetail, getTaskExecutionMetrics } from '../api/agentMetrics'
import { useRegistryList } from '../composables/useRegistryList'
import type {
  AgentExecutionMetric,
  AgentMetrics,
  AgentMetricsDetail,
  TaskExecutionMetrics,
} from '../types/agentMetrics'

const {
  items: agents,
  selected: selectedAgent,
  loading,
  errorMessage,
  select,
} = useRegistryList<AgentMetrics>({
  fetch: getAgentMetrics,
  idOf: (agent) => agent.agentId,
  errorText: '无法加载 Agent Metrics。',
})

const detail = ref<AgentMetricsDetail | null>(null)
const detailLoading = ref(false)
const taskIdInput = ref('')
const taskMetrics = ref<TaskExecutionMetrics | null>(null)
const taskLoading = ref(false)
const taskError = ref<string | null>(null)

async function loadDetail(agent: AgentMetrics | null): Promise<void> {
  if (!agent) {
    detail.value = null
    return
  }
  detailLoading.value = true
  try {
    detail.value = await getAgentMetricsDetail(agent.agentId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 Agent 详情失败。')
  } finally {
    detailLoading.value = false
  }
}

watch(selectedAgent, (agent) => {
  loadDetail(agent)
})

async function queryTask(): Promise<void> {
  const taskId = taskIdInput.value.trim()
  if (!taskId) {
    ElMessage.warning('请输入 Task ID。')
    return
  }
  taskLoading.value = true
  taskError.value = null
  try {
    taskMetrics.value = await getTaskExecutionMetrics(taskId)
  } catch (error) {
    taskMetrics.value = null
    taskError.value = error instanceof Error ? error.message : '查询任务统计失败。'
  } finally {
    taskLoading.value = false
  }
}

function successRate(agent: AgentMetrics): number {
  if (agent.taskCount === 0) {
    return 0
  }
  return (agent.successCount / agent.taskCount) * 100
}

function formatDuration(millis: number): string {
  if (!millis) {
    return '—'
  }
  if (millis < 1000) {
    return `${millis} ms`
  }
  return `${(millis / 1000).toFixed(1)} s`
}

function formatTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function statusLabel(status: string): string {
  return status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : status ?? '—'
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Agent Observability</p>
        <h1>Agent Metrics</h1>
        <p class="page-description">
          Agent 执行可观测性：执行次数、成功率、失败次数、平均耗时与 Repair 次数，全部来自现有 ExecutionRecord / Audit / Repair / Change 数据。
        </p>
      </div>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Agent 排名</span>
          </template>
          <el-table
            :data="agents"
            v-loading="loading"
            stripe
            highlight-current-row
            row-key="agentId"
            empty-text="暂无 Agent 执行数据"
            :current-row-key="selectedAgent?.agentId ?? null"
            @current-change="(row: AgentMetrics | null) => select(row)"
          >
            <el-table-column label="Agent" min-width="120">
              <template #default="{ row }: { row: AgentMetrics }">
                <code>{{ row.agentId }}</code>
              </template>
            </el-table-column>
            <el-table-column label="执行次数" min-width="80" prop="taskCount" />
            <el-table-column label="成功率" min-width="90">
              <template #default="{ row }: { row: AgentMetrics }">
                <span :class="successRate(row) >= 60 ? 'rate rate--ok' : 'rate rate--bad'">
                  {{ successRate(row).toFixed(1) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column label="失败次数" min-width="80" prop="failedCount" />
            <el-table-column label="平均耗时" min-width="100">
              <template #default="{ row }: { row: AgentMetrics }">
                {{ formatDuration(row.averageDuration) }}
              </template>
            </el-table-column>
            <el-table-column label="Repair次数" min-width="90" prop="repairCount" />
            <el-table-column label="ChangeSet" min-width="90" prop="changeCount" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Agent 详情</span>
          </template>
          <div v-loading="detailLoading">
            <template v-if="detail">
              <div class="metric-grid">
                <div class="metric-item">
                  <span class="metric-item__label">任务数</span>
                  <span class="metric-item__value">{{ detail.metrics.taskCount }}</span>
                </div>
                <div class="metric-item">
                  <span class="metric-item__label">成功</span>
                  <span class="metric-item__value metric-item__value--ok">
                    {{ detail.metrics.successCount }}
                  </span>
                </div>
                <div class="metric-item">
                  <span class="metric-item__label">失败</span>
                  <span class="metric-item__value metric-item__value--bad">
                    {{ detail.metrics.failedCount }}
                  </span>
                </div>
                <div class="metric-item">
                  <span class="metric-item__label">Retry</span>
                  <span class="metric-item__value">{{ detail.metrics.retryCount }}</span>
                </div>
                <div class="metric-item">
                  <span class="metric-item__label">Repair</span>
                  <span class="metric-item__value">{{ detail.metrics.repairCount }}</span>
                </div>
                <div class="metric-item">
                  <span class="metric-item__label">平均耗时</span>
                  <span class="metric-item__value">
                    {{ formatDuration(detail.metrics.averageDuration) }}
                  </span>
                </div>
              </div>
              <p class="metric-last">
                最近执行：{{ formatTime(detail.metrics.lastExecutedAt) }}
              </p>
              <el-divider>执行记录</el-divider>
              <el-table
                :data="detail.executions"
                size="small"
                empty-text="暂无执行记录"
                max-height="320"
              >
                <el-table-column label="Task" min-width="110">
                  <template #default="{ row }: { row: AgentExecutionMetric }">
                    <code>{{ row.taskId }}</code>
                  </template>
                </el-table-column>
                <el-table-column label="状态" min-width="70">
                  <template #default="{ row }: { row: AgentExecutionMetric }">
                    {{ statusLabel(row.status) }}
                  </template>
                </el-table-column>
                <el-table-column label="耗时" min-width="80">
                  <template #default="{ row }: { row: AgentExecutionMetric }">
                    {{ formatDuration(row.durationMillis) }}
                  </template>
                </el-table-column>
              </el-table>
            </template>
            <p v-else class="page-state">选择一个 Agent 查看执行详情</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="task-card">
      <template #header>
        <span class="card-title">任务执行统计</span>
      </template>
      <el-form inline @submit.prevent="queryTask">
        <el-form-item label="Task ID">
          <el-input
            v-model="taskIdInput"
            placeholder="例如 task-xxx"
            style="width: 260px"
            @keyup.enter="queryTask"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="taskLoading" @click="queryTask">查询</el-button>
        </el-form-item>
      </el-form>
      <p v-if="taskError" class="page-state page-state--error">{{ taskError }}</p>
      <template v-else-if="taskMetrics">
        <div class="metric-grid">
          <div class="metric-item">
            <span class="metric-item__label">状态</span>
            <span class="metric-item__value">{{ taskMetrics.taskStatus }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">执行次数</span>
            <span class="metric-item__value">{{ taskMetrics.executionCount }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">成功</span>
            <span class="metric-item__value metric-item__value--ok">
              {{ taskMetrics.successCount }}
            </span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">失败</span>
            <span class="metric-item__value metric-item__value--bad">
              {{ taskMetrics.failedCount }}
            </span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">平均耗时</span>
            <span class="metric-item__value">
              {{ formatDuration(taskMetrics.averageDurationMillis) }}
            </span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">Repair</span>
            <span class="metric-item__value">{{ taskMetrics.repairCount }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">ChangeSet</span>
            <span class="metric-item__value">{{ taskMetrics.changeCount }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">Review 通过率</span>
            <span class="metric-item__value">
              {{ (taskMetrics.reviewPassRate * 100).toFixed(1) }}%
            </span>
          </div>
        </div>
        <el-table
          :data="taskMetrics.executions"
          size="small"
          class="task-executions"
          empty-text="暂无执行记录"
        >
          <el-table-column label="Agent" min-width="100">
            <template #default="{ row }: { row: AgentExecutionMetric }">
              <code>{{ row.agentId }}</code>
            </template>
          </el-table-column>
          <el-table-column label="Execution" min-width="130">
            <template #default="{ row }: { row: AgentExecutionMetric }">
              <code>{{ row.executionId }}</code>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="70">
            <template #default="{ row }: { row: AgentExecutionMetric }">
              {{ statusLabel(row.status) }}
            </template>
          </el-table-column>
          <el-table-column label="耗时" min-width="80">
            <template #default="{ row }: { row: AgentExecutionMetric }">
              {{ formatDuration(row.durationMillis) }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>
  </section>
</template>

<style scoped>
.content-row {
  margin-bottom: 16px;
}

.rate {
  font-weight: 600;
}

.rate--ok {
  color: #51d6a3;
}

.rate--bad {
  color: #ff7b8b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-item__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-item__value {
  font-size: 18px;
  font-weight: 700;
}

.metric-item__value--ok {
  color: #51d6a3;
}

.metric-item__value--bad {
  color: #ff7b8b;
}

.metric-last {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.task-card {
  margin-top: 16px;
}

.task-executions {
  margin-top: 12px;
}
</style>
