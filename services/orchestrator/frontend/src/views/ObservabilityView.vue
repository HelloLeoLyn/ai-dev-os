<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getAgentMetrics } from '../api/agentMetrics'
import {
  getAgentObservability,
  getProjectObservability,
  getTaskObservability,
  getToolMetrics,
} from '../api/observability'
import type { AgentMetrics } from '../types/agentMetrics'
import type {
  AgentObservability,
  ProjectObservability,
  TaskObservability,
  ToolMetrics,
  TraceRecord,
} from '../types/observability'

const taskIdInput = ref('')
const task = ref<TaskObservability | null>(null)
const taskLoading = ref(false)
const taskError = ref<string | null>(null)

const agents = ref<AgentMetrics[]>([])
const agentsLoading = ref(false)
const agentsError = ref<string | null>(null)

const tools = ref<ToolMetrics[]>([])
const toolsLoading = ref(false)
const toolsError = ref<string | null>(null)

const projectIdInput = ref('')
const project = ref<ProjectObservability | null>(null)
const projectLoading = ref(false)
const projectError = ref<string | null>(null)

const agentTypeInput = ref('CODEX')
const agentDetail = ref<AgentObservability | null>(null)
const agentDetailLoading = ref(false)

async function queryTask(): Promise<void> {
  const taskId = taskIdInput.value.trim()
  if (!taskId) {
    ElMessage.warning('请输入 Task ID。')
    return
  }
  taskLoading.value = true
  taskError.value = null
  try {
    task.value = await getTaskObservability(taskId)
  } catch (error) {
    task.value = null
    taskError.value = error instanceof Error ? error.message : '查询任务观测失败。'
  } finally {
    taskLoading.value = false
  }
}

async function loadAgents(): Promise<void> {
  agentsLoading.value = true
  agentsError.value = null
  try {
    agents.value = await getAgentMetrics()
  } catch (error) {
    agentsError.value = error instanceof Error ? error.message : '加载 Agent 排名失败。'
  } finally {
    agentsLoading.value = false
  }
}

async function loadTools(): Promise<void> {
  toolsLoading.value = true
  toolsError.value = null
  try {
    tools.value = await getToolMetrics()
  } catch (error) {
    toolsError.value = error instanceof Error ? error.message : '加载 Tool 统计失败。'
  } finally {
    toolsLoading.value = false
  }
}

async function queryProject(): Promise<void> {
  const projectId = projectIdInput.value.trim()
  if (!projectId) {
    ElMessage.warning('请输入 Project ID。')
    return
  }
  projectLoading.value = true
  projectError.value = null
  try {
    project.value = await getProjectObservability(projectId)
  } catch (error) {
    project.value = null
    projectError.value = error instanceof Error ? error.message : '查询项目观测失败。'
  } finally {
    projectLoading.value = false
  }
}

async function queryAgentDetail(): Promise<void> {
  const agentType = agentTypeInput.value.trim()
  if (!agentType) {
    ElMessage.warning('请输入 Agent 类型。')
    return
  }
  agentDetailLoading.value = true
  try {
    agentDetail.value = await getAgentObservability(agentType)
  } catch (error) {
    agentDetail.value = null
    ElMessage.error(error instanceof Error ? error.message : '查询 Agent 详情失败。')
  } finally {
    agentDetailLoading.value = false
  }
}

onMounted(() => {
  loadAgents()
  loadTools()
})

function successRate(task: TaskObservability): number {
  const agent = task.agent
  if (!agent.executionCount) {
    return 0
  }
  return (agent.successCount / agent.executionCount) * 100
}

function traceStatusClass(status: string): string {
  return status === 'SUCCESS' ? 'rate rate--ok' : status === 'FAILED' ? 'rate rate--bad' : 'rate'
}

function statusLabel(status: string | null | undefined): string {
  if (!status) {
    return '—'
  }
  return status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : status
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

function formatCost(cost: number): string {
  if (!cost) {
    return '$0.000'
  }
  return `$${cost.toFixed(4)}`
}

function formatTokens(tokens: number): string {
  return tokens ? tokens.toLocaleString() : '0'
}

function formatRate(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`
}

function formatTime(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function traceTitle(trace: TraceRecord): string {
  if (trace.toolId) {
    return `Tool: ${trace.toolId}`
  }
  if (trace.nodeId) {
    return `Node: ${trace.nodeId}`
  }
  return `Trace: ${trace.traceId}`
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Observability Platform</p>
        <h1>Observability</h1>
        <p class="page-description">
          AI Dev OS 内部可观测平台：Task Trace、Agent 排名、Tool 统计与 Cost 看板，全部来自现有
          Trace / Audit / ExecutionRecord / Usage 数据。
        </p>
      </div>
    </header>

    <el-card shadow="never">
      <template #header>
        <span class="card-title">Task Trace</span>
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
      <template v-else-if="task">
        <div class="metric-grid">
          <div class="metric-item">
            <span class="metric-item__label">任务状态</span>
            <span class="metric-item__value">{{ statusLabel(task.taskStatus) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">成功率</span>
            <span class="metric-item__value">{{ successRate(task).toFixed(1) }}%</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">Token</span>
            <span class="metric-item__value">{{ formatTokens(task.usage.totalTokens) }}</span>
          </div>
          <div class="metric-item">
            <span class="metric-item__label">Cost</span>
            <span class="metric-item__value">{{ formatCost(task.usage.estimatedCost) }}</span>
          </div>
        </div>

        <el-divider>Trace 链路</el-divider>
        <el-table
          :data="task.traces"
          v-loading="taskLoading"
          size="small"
          empty-text="暂无 Trace"
        >
          <el-table-column label="阶段" min-width="180">
            <template #default="{ row }: { row: TraceRecord }">
              <code>{{ traceTitle(row) }}</code>
            </template>
          </el-table-column>
          <el-table-column label="Agent" min-width="110" prop="agentType" />
          <el-table-column label="状态" min-width="90">
            <template #default="{ row }: { row: TraceRecord }">
              <span :class="traceStatusClass(row.status)">{{ statusLabel(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" min-width="90">
            <template #default="{ row }: { row: TraceRecord }">
              {{ formatDuration(row.duration) }}
            </template>
          </el-table-column>
          <el-table-column label="错误" min-width="140">
            <template #default="{ row }: { row: TraceRecord }">
              <span class="trace-error">{{ row.errorMessage ?? '—' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-divider>Timeline</el-divider>
        <el-timeline v-if="task.timeline.events.length">
          <el-timeline-item
            v-for="event in task.timeline.events"
            :key="event.eventId"
            :timestamp="formatTime(event.timestamp)"
          >
            <code class="timeline-type">{{ event.eventType }}</code>
            <span class="timeline-message">{{ event.message }}</span>
          </el-timeline-item>
        </el-timeline>
        <p v-else class="page-state">暂无 Timeline 事件</p>
      </template>
      <p v-else class="page-state">输入 Task ID 查看完整执行链路</p>
    </el-card>

    <el-row :gutter="16" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Agent 排名</span>
          </template>
          <p v-if="agentsError" class="page-state page-state--error">{{ agentsError }}</p>
          <el-table
            v-else
            :data="agents"
            v-loading="agentsLoading"
            stripe
            row-key="agentId"
            empty-text="暂无 Agent 执行数据"
          >
            <el-table-column label="Agent" min-width="110">
              <template #default="{ row }: { row: AgentMetrics }">
                <code>{{ row.agentId }}</code>
              </template>
            </el-table-column>
            <el-table-column label="执行" min-width="70" prop="taskCount" />
            <el-table-column label="成功率" min-width="90">
              <template #default="{ row }: { row: AgentMetrics }">
                {{
                  row.taskCount
                    ? `${((row.successCount / row.taskCount) * 100).toFixed(1)}%`
                    : '—'
                }}
              </template>
            </el-table-column>
            <el-table-column label="平均耗时" min-width="100">
              <template #default="{ row }: { row: AgentMetrics }">
                {{ formatDuration(row.averageDuration) }}
              </template>
            </el-table-column>
            <el-table-column label="Repair" min-width="80" prop="repairCount" />
            <el-table-column label="Token" min-width="100">
              <template #default="{ row }: { row: AgentMetrics }">
                {{ formatTokens(row.tokenCount) }}
              </template>
            </el-table-column>
            <el-table-column label="Cost" min-width="90">
              <template #default="{ row }: { row: AgentMetrics }">
                {{ formatCost(row.estimatedCost) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Tool 统计</span>
          </template>
          <p v-if="toolsError" class="page-state page-state--error">{{ toolsError }}</p>
          <el-table
            v-else
            :data="tools"
            v-loading="toolsLoading"
            stripe
            row-key="toolId"
            empty-text="暂无 Tool 调用数据"
          >
            <el-table-column label="Tool" min-width="110">
              <template #default="{ row }: { row: ToolMetrics }">
                <code>{{ row.toolId }}</code>
              </template>
            </el-table-column>
            <el-table-column label="调用" min-width="70" prop="executeCount" />
            <el-table-column label="成功" min-width="70" prop="successCount" />
            <el-table-column label="失败" min-width="70" prop="failedCount" />
            <el-table-column label="拒绝" min-width="70" prop="deniedCount" />
            <el-table-column label="平均耗时" min-width="100">
              <template #default="{ row }: { row: ToolMetrics }">
                {{ formatDuration(row.averageDurationMillis) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Cost 看板 · Project</span>
          </template>
          <el-form inline @submit.prevent="queryProject">
            <el-form-item label="Project ID">
              <el-input
                v-model="projectIdInput"
                placeholder="例如 project-xxx"
                style="width: 220px"
                @keyup.enter="queryProject"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="projectLoading" @click="queryProject">
                查询
              </el-button>
            </el-form-item>
          </el-form>
          <p v-if="projectError" class="page-state page-state--error">{{ projectError }}</p>
          <template v-else-if="project">
            <div class="metric-grid">
              <div class="metric-item">
                <span class="metric-item__label">任务数</span>
                <span class="metric-item__value">{{ project.taskCount }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">成功率</span>
                <span class="metric-item__value">{{ formatRate(project.successRate) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">失败率</span>
                <span class="metric-item__value">{{ formatRate(project.failureRate) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">平均耗时</span>
                <span class="metric-item__value">
                  {{ formatDuration(project.averageDurationMillis) }}
                </span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">Token</span>
                <span class="metric-item__value">{{ formatTokens(project.totalTokens) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">Cost</span>
                <span class="metric-item__value">{{ formatCost(project.estimatedCost) }}</span>
              </div>
            </div>
          </template>
          <p v-else class="page-state">输入 Project ID 查看项目成本</p>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Agent 详情</span>
          </template>
          <el-form inline @submit.prevent="queryAgentDetail">
            <el-form-item label="Agent">
              <el-input
                v-model="agentTypeInput"
                placeholder="例如 CODEX"
                style="width: 160px"
                @keyup.enter="queryAgentDetail"
              />
            </el-form-item>
            <el-form-item>
              <el-button :loading="agentDetailLoading" @click="queryAgentDetail">
                查询
              </el-button>
            </el-form-item>
          </el-form>
          <template v-if="agentDetail">
            <div class="metric-grid">
              <div class="metric-item">
                <span class="metric-item__label">执行次数</span>
                <span class="metric-item__value">{{ agentDetail.executionCount }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">成功率</span>
                <span class="metric-item__value">{{ formatRate(agentDetail.successRate) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">平均耗时</span>
                <span class="metric-item__value">
                  {{ formatDuration(agentDetail.averageDurationMillis) }}
                </span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">Token</span>
                <span class="metric-item__value">{{ formatTokens(agentDetail.totalTokens) }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-item__label">Cost</span>
                <span class="metric-item__value">{{ formatCost(agentDetail.estimatedCost) }}</span>
              </div>
            </div>
          </template>
          <p v-else class="page-state">输入 Agent 类型查看单 Agent 观测数据</p>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.trace-error {
  color: var(--el-color-danger);
  font-size: 12px;
  word-break: break-all;
}

.timeline-type {
  margin-right: 8px;
}

.timeline-message {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
