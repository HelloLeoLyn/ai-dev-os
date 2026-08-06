<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createMemory, deleteMemory, getMemories } from '../api/memory'
import MemoryTable from '../components/MemoryTable.vue'
import type { CreateMemoryRequest, MemoryRecord, MemoryType } from '../types/memory'

const memories = ref<MemoryRecord[]>([])
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const selectedType = ref<MemoryType | ''>('')

const typeOptions: Array<{ label: string; value: MemoryType }> = [
  { label: '项目规则', value: 'PROJECT_RULE' },
  { label: '历史任务', value: 'HISTORY_TASK' },
  { label: 'Bug 记录', value: 'BUG_RECORD' },
  { label: 'Agent 经验', value: 'AGENT_EXPERIENCE' },
]

const form = reactive<CreateMemoryRequest>({
  projectId: '',
  type: 'PROJECT_RULE',
  key: '',
  content: '',
})

async function loadMemories(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    memories.value = await getMemories(
      undefined,
      selectedType.value === '' ? undefined : selectedType.value,
    )
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load memories.'
  } finally {
    loading.value = false
  }
}

async function handleCreate(): Promise<void> {
  if (!form.key.trim() || !form.content.trim()) {
    ElMessage.warning('Key 与内容为必填项。')
    return
  }

  submitting.value = true
  try {
    await createMemory({
      projectId: form.projectId.trim() || 'default',
      type: form.type,
      key: form.key.trim(),
      content: form.content.trim(),
    })
    form.key = ''
    form.content = ''
    ElMessage.success('Memory 已保存。')
    await loadMemories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败。')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(record: MemoryRecord): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除 Memory「${record.key}」？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await deleteMemory(record.id)
    ElMessage.success('Memory 已删除。')
    await loadMemories()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败。')
  }
}

onMounted(loadMemories)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Long-term Memory</p>
        <h1>Memory</h1>
        <p class="page-description">
          长期项目记忆：项目规则、历史任务、Bug 记录与 Agent 经验。
        </p>
      </div>
      <el-tag type="info" effect="dark">{{ memories.length }} records</el-tag>
    </header>

    <el-card shadow="never" class="create-card">
      <template #header>
        <span class="card-title">新增 Memory</span>
      </template>
      <el-form label-position="top" @submit.prevent="handleCreate">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="项目 ID">
              <el-input v-model="form.projectId" placeholder="默认 default" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="类型" required>
              <el-select v-model="form.type" class="full-width">
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
            <el-form-item label="Key" required>
              <el-input v-model="form.key" placeholder="例如 rule-1 / bug-1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            placeholder="记忆内容，Agent 后续可读取"
          />
        </el-form-item>
        <el-button type="primary" :loading="submitting" native-type="submit">
          保存 Memory
        </el-button>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="list-header">
          <span class="card-title">Memory 列表</span>
          <el-select
            v-model="selectedType"
            class="type-filter"
            placeholder="全部类型"
            clearable
            @change="loadMemories"
          >
            <el-option
              v-for="option in typeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
      </template>

      <p v-if="errorMessage" class="page-state page-state--error">{{ errorMessage }}</p>
      <MemoryTable v-else :memories="memories" :loading="loading" @delete="handleDelete" />
    </el-card>
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

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.type-filter {
  width: 12rem;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
