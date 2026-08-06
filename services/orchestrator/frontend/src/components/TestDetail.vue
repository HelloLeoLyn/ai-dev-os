<script setup lang="ts">
import { ref, watch } from 'vue'

import { getTestReport, getTestScreenshotUrl } from '../api/tests'
import type { TestPlan, TestReport, TestStatus, TestType } from '../types/test'

const props = defineProps<{
  test: TestPlan | null
}>()

const report = ref<TestReport | null>(null)
const reportLoading = ref(false)
const reportError = ref<string | null>(null)

const typeLabels: Record<TestType, string> = {
  UNIT_TEST: '单元测试',
  API_TEST: 'API 测试',
  UI_TEST: 'UI 测试',
  BUILD_VERIFY: '构建验证',
}

function statusTone(status: TestStatus): 'success' | 'danger' | 'info' | 'warning' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    default:
      return 'info'
  }
}

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function formatDuration(millis: number): string {
  if (millis < 1000) {
    return `${millis} ms`
  }
  const seconds = millis / 1000
  return `${seconds.toFixed(1)} s`
}

async function loadReport(): Promise<void> {
  const test = props.test
  report.value = null
  reportError.value = null
  if (!test) {
    return
  }
  reportLoading.value = true
  try {
    report.value = await getTestReport(test.testId)
  } catch (error) {
    reportError.value = error instanceof Error ? error.message : '无法加载测试报告。'
  } finally {
    reportLoading.value = false
  }
}

watch(() => props.test?.testId, loadReport, { immediate: true })
</script>

<template>
  <el-card v-if="test" shadow="never" class="test-detail">
    <template #header>
      <div class="detail-header">
        <span class="card-title">{{ test.testId }}</span>
        <el-tag :type="statusTone(test.status)" effect="dark" size="small">
          {{ test.status }}
        </el-tag>
      </div>
    </template>

    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="类型">
        {{ typeLabels[test.testType] || test.testType }}
      </el-descriptions-item>
      <el-descriptions-item label="命令">
        <code>{{ test.command }}</code>
      </el-descriptions-item>
      <el-descriptions-item label="Task ID">
        {{ test.taskId || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="Execution ID">
        {{ test.executionId || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="项目">
        {{ test.projectId }}
      </el-descriptions-item>
      <el-descriptions-item label="结果">
        <span :class="{ 'error-text': test.status === 'FAILED' }">
          {{ test.result || test.errorMessage || '—' }}
        </span>
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">
        {{ formatDate(test.createdAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="完成时间">
        {{ formatDate(test.completedAt) }}
      </el-descriptions-item>
    </el-descriptions>

    <div v-if="test.errorMessage" class="log-section">
      <p class="log-title error-text">错误信息</p>
      <pre class="log-block log-block--error">{{ test.errorMessage }}</pre>
    </div>

    <div v-if="test.screenshotPath" class="log-section">
      <p class="log-title">截图</p>
      <el-image
        :src="getTestScreenshotUrl(test.testId)"
        :preview-src-list="[getTestScreenshotUrl(test.testId)]"
        fit="contain"
        class="screenshot"
        preview-teleported
      >
        <template #error>
          <div class="screenshot-placeholder">截图不可用</div>
        </template>
      </el-image>
    </div>

    <div class="log-section">
      <p class="log-title">测试报告</p>
      <div v-if="reportLoading" class="report-muted">加载中…</div>
      <div v-else-if="reportError" class="report-muted">{{ reportError }}</div>
      <div v-else-if="report" class="report-card">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="摘要">
            {{ report.summary || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="时长">
            {{ formatDuration(report.duration) }}
          </el-descriptions-item>
          <el-descriptions-item label="通过">
            <span class="report-passed">{{ report.passed }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="失败">
            <span :class="{ 'error-text': report.failed > 0 }">{{ report.failed }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="report.artifacts.length > 0" class="report-artifacts">
          <p class="log-title">Artifacts</p>
          <ul class="artifact-list">
            <li v-for="artifact in report.artifacts" :key="artifact">
              <code>{{ artifact }}</code>
            </li>
          </ul>
        </div>
      </div>
      <div v-else class="report-muted">暂无报告</div>
    </div>

    <div v-if="test.logs" class="log-section">
      <p class="log-title">执行日志</p>
      <pre class="log-block">{{ test.logs }}</pre>
    </div>
  </el-card>

  <el-empty v-else description="选择左侧测试任务查看详情" />
</template>

<style scoped>
.card-title {
  font-weight: 700;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.error-text {
  color: var(--color-danger);
}

.log-section {
  margin-top: 1rem;
}

.log-title {
  margin: 0 0 0.5rem;
  font-weight: 700;
}

.log-block {
  max-height: 24rem;
  overflow: auto;
  margin: 0;
  padding: 0.75rem;
  border-radius: 0.375rem;
  background: var(--color-surface-muted, #f5f7fa);
  font-size: 0.8rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.log-block--error {
  color: var(--color-danger);
}

.screenshot {
  width: 100%;
  max-height: 28rem;
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: 0.375rem;
}

.screenshot-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 8rem;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.report-muted {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.report-card {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.report-passed {
  color: var(--color-success, #67c23a);
  font-weight: 600;
}

.artifact-list {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8rem;
}

.artifact-list code {
  overflow-wrap: anywhere;
}
</style>
