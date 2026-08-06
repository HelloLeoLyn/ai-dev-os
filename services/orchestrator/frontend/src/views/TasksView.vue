<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import { createTask, getTasks } from '../api/tasks'
import TaskDetail from '../components/TaskDetail.vue'
import TaskTable from '../components/TaskTable.vue'
import type { CreateTaskRequest, TaskRecord } from '../types/task'

const tasks = ref<TaskRecord[]>([])
const selectedTask = ref<TaskRecord | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const submitError = ref<string | null>(null)

const form = reactive<CreateTaskRequest>({
  name: '',
  description: '',
  goal: '',
  plannerName: 'hermes',
})

const plannerOptions = ['hermes', 'fake']

async function loadTasks(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    tasks.value = await getTasks()
    if (!selectedTask.value && tasks.value.length > 0) {
      selectedTask.value = tasks.value[0]
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load tasks.'
  } finally {
    loading.value = false
  }
}

async function handleCreate(): Promise<void> {
  if (!form.name.trim() || !form.goal.trim()) {
    submitError.value = '任务名称与目标（goal）为必填项。'
    return
  }

  submitting.value = true
  submitError.value = null

  try {
    const task = await createTask({
      name: form.name.trim(),
      description: form.description.trim(),
      goal: form.goal.trim(),
      plannerName: form.plannerName,
    })
    form.name = ''
    form.description = ''
    form.goal = ''
    selectedTask.value = task
    await loadTasks()
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : 'Unable to create task.'
  } finally {
    submitting.value = false
  }
}

onMounted(loadTasks)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Task Center</p>
        <h1>Tasks</h1>
        <p class="page-description">
          User Request → Task → Plan → Approval → Execution → Result
        </p>
      </div>
    </header>

    <el-card shadow="never" class="create-card">
      <template #header>
        <span class="card-title">创建任务</span>
      </template>
      <el-form label-position="top" @submit.prevent="handleCreate">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="任务名称" required>
              <el-input v-model="form.name" placeholder="例如 Implement login" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="Planner">
              <el-select v-model="form.plannerName">
                <el-option
                  v-for="planner in plannerOptions"
                  :key="planner"
                  :label="planner"
                  :value="planner"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="任务描述（可选）" />
        </el-form-item>
        <el-form-item label="目标" required>
          <el-input
            v-model="form.goal"
            type="textarea"
            :rows="3"
            placeholder="例如 Implement a login flow with tests"
          />
        </el-form-item>
        <p v-if="submitError" class="form-error">{{ submitError }}</p>
        <el-button type="primary" :loading="submitting" native-type="submit">
          创建并规划
        </el-button>
      </el-form>
    </el-card>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <el-row v-else :gutter="16" class="content-row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <TaskTable
            :tasks="tasks"
            :loading="loading"
            :selected-task-id="selectedTask?.taskId ?? null"
            @select="selectedTask = $event"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <TaskDetail :task="selectedTask" />
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

.form-error {
  margin: 0 0 1rem;
  color: var(--color-danger);
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
