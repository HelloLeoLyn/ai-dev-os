<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { createTest, getTests } from '../api/tests'
import TestDetail from '../components/TestDetail.vue'
import TestTable from '../components/TestTable.vue'
import type { CreateTestRequest, TestPlan, TestType } from '../types/test'

const tests = ref<TestPlan[]>([])
const selectedTest = ref<TestPlan | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

const typeOptions: Array<{ label: string; value: TestType }> = [
  { label: '单元测试 (mvn test)', value: 'UNIT_TEST' },
  { label: 'API 测试 (mvn test)', value: 'API_TEST' },
  { label: 'UI 测试 (npm run build)', value: 'UI_TEST' },
  { label: '构建验证 (npm run build)', value: 'BUILD_VERIFY' },
]

const form = reactive({
  taskId: '',
  testType: 'UNIT_TEST' as TestType,
  command: '',
  executionId: '',
  projectId: '',
})

async function loadTests(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    tests.value = await getTests()
    if (!selectedTest.value && tests.value.length > 0) {
      selectedTest.value = tests.value[0]
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load tests.'
  } finally {
    loading.value = false
  }
}

async function handleCreate(): Promise<void> {
  submitting.value = true
  try {
    const plan = await createTest({
      taskId: form.taskId.trim() || undefined,
      testType: form.testType,
      command: form.command.trim() || undefined,
      executionId: form.executionId.trim() || undefined,
      projectId: form.projectId.trim() || undefined,
    })
    form.taskId = ''
    form.command = ''
    form.executionId = ''
    form.projectId = ''
    selectedTest.value = plan
    ElMessage.success(plan.status === 'SUCCESS' ? '测试通过。' : '测试完成（未通过）。')
    await loadTests()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建测试失败。')
  } finally {
    submitting.value = false
  }
}

onMounted(loadTests)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Testing Agent</p>
        <h1>Tests</h1>
        <p class="page-description">
          自动生成并执行测试任务：mvn test / npm run build。
        </p>
      </div>
      <el-tag type="info" effect="dark">{{ tests.length }} tests</el-tag>
    </header>

    <el-card shadow="never" class="create-card">
      <template #header>
        <span class="card-title">创建测试任务</span>
      </template>
      <el-form label-position="top" @submit.prevent="handleCreate">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="测试类型" required>
              <el-select v-model="form.testType" class="full-width">
                <el-option
                  v-for="option in typeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="Task ID（可选）">
              <el-input v-model="form.taskId" placeholder="关联 Task Center 任务" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="项目（可选）">
              <el-input v-model="form.projectId" placeholder="默认 default" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="16">
            <el-form-item label="命令（可选）">
              <el-input v-model="form.command" placeholder="留空则按类型生成默认命令" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="Execution ID（可选）">
              <el-input v-model="form.executionId" placeholder="关联 Execution" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-button type="primary" :loading="submitting" native-type="submit">
          创建并执行
        </el-button>
      </el-form>
    </el-card>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <TestTable
            :tests="tests"
            :loading="loading"
            :selected-test-id="selectedTest?.testId ?? null"
            @select="selectedTest = $event"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <TestDetail :test="selectedTest" />
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.create-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 700;
}

.full-width {
  width: 100%;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
