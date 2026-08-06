<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

import { createAgentPlan, getAgentPlan } from '../api/agentPlans'
import AgentFlowGraph from '../components/AgentFlowGraph.vue'
import type { AgentExecutionPlan } from '../types/agentPlan'

const taskTypeOptions = [
  { label: '任务分析 (TASK_ANALYSIS)', value: 'TASK_ANALYSIS' },
  { label: '代码生成 (CODE_GENERATION)', value: 'CODE_GENERATION' },
  { label: '浏览器测试 (BROWSER_TEST)', value: 'BROWSER_TEST' },
  { label: '测试验证 (TEST_VERIFY)', value: 'TEST_VERIFY' },
  { label: '通用 (GENERAL)', value: 'GENERAL' },
]

const taskId = ref('')
const taskType = ref('TASK_ANALYSIS')
const steps = ref<AgentExecutionPlan[] | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)

async function runFlow(): Promise<void> {
  if (!taskId.value.trim()) {
    ElMessage.warning('请输入 Task ID。')
    return
  }
  loading.value = true
  errorMessage.value = null
  try {
    steps.value = await createAgentPlan(taskId.value.trim(), taskType.value)
    ElMessage.success('Agent 协作流程执行完成。')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法创建协作计划。'
  } finally {
    loading.value = false
  }
}

async function refresh(): Promise<void> {
  if (!taskId.value.trim()) {
    return
  }
  loading.value = true
  errorMessage.value = null
  try {
    steps.value = await getAgentPlan(taskId.value.trim())
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '无法加载协作计划。'
  } finally {
    loading.value = false
  }
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Agent Collaboration</p>
        <h1>Agent Flow</h1>
        <p class="page-description">
          多 Agent 自主协作：Hermes 规划 → Codex 编码 → OpenClaw 测试 → TestAgent 验证。
        </p>
      </div>
      <el-tag type="info" effect="dark">{{ steps?.length ?? 0 }} steps</el-tag>
    </header>

    <el-card shadow="never" class="run-card">
      <template #header>
        <span class="card-title">运行协作流程</span>
      </template>
      <el-form label-position="top" @submit.prevent="runFlow">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="10">
            <el-form-item label="Task ID" required>
              <el-input
                v-model="taskId"
                placeholder="例如 task-xxx"
                :disabled="loading"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="10">
            <el-form-item label="任务类型">
              <el-select v-model="taskType" class="full-width" :disabled="loading">
                <el-option
                  v-for="option in taskTypeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="4" class="actions">
            <el-button type="primary" :loading="loading" native-type="submit">
              运行
            </el-button>
            <el-button :loading="loading" @click="refresh">刷新</el-button>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">Agent 执行流程图</span>
          </template>
          <AgentFlowGraph :steps="steps" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">协作计划步骤</span>
          </template>
          <el-table v-if="steps && steps.length > 0" :data="steps" stripe>
            <el-table-column prop="step" label="Step" min-width="70" />
            <el-table-column prop="agentId" label="Agent" min-width="120">
              <template #default="{ row }: { row: AgentExecutionPlan }">
                <code>{{ row.agentId }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="110">
              <template #default="{ row }: { row: AgentExecutionPlan }">
                <el-tag
                  :type="
                    row.status === 'SUCCESS'
                      ? 'success'
                      : row.status === 'FAILED'
                        ? 'danger'
                        : row.status === 'RUNNING'
                          ? 'warning'
                          : 'info'
                  "
                  effect="dark"
                  size="small"
                >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="result" label="结果" min-width="180" show-overflow-tooltip>
              <template #default="{ row }: { row: AgentExecutionPlan }">
                {{ row.result || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="开始时间" min-width="150">
              <template #default="{ row }: { row: AgentExecutionPlan }">
                {{ formatDate(row.startedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="完成时间" min-width="150">
              <template #default="{ row }: { row: AgentExecutionPlan }">
                {{ formatDate(row.completedAt) }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无协作计划，输入 Task ID 并点击运行。" />
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.run-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 700;
}

.full-width {
  width: 100%;
}

.actions {
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  padding-bottom: 0.25rem;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
