<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { modelRegistryApi } from '../api/models'
import type { ModelDefinition, ProviderDefinition } from '../types/model'

const props = defineProps<{
  models: ModelDefinition[]
  providers: ProviderDefinition[]
  defaultModelId?: string | null
  loading?: boolean
}>()

const emit = defineEmits<{ (e: 'reload'): void }>()

const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const errorMessage = ref('')

const providerName = (providerId: string): string =>
  props.providers.find((provider) => provider.providerId === providerId)?.displayName ?? providerId

const emptyForm = (): ModelDefinition => ({
  modelId: '',
  displayName: '',
  providerId: '',
  executorType: 'codex',
  enabled: true,
  capabilities: [],
})
const form = reactive<ModelDefinition>(emptyForm())

function openCreate(): void {
  editing.value = false
  Object.assign(form, emptyForm())
  errorMessage.value = ''
  dialogVisible.value = true
}

function openEdit(row: ModelDefinition): void {
  editing.value = true
  Object.assign(form, {
    modelId: row.modelId,
    displayName: row.displayName,
    providerId: row.providerId,
    executorType: row.executorType,
    enabled: row.enabled,
    capabilities: [...row.capabilities],
  })
  errorMessage.value = ''
  dialogVisible.value = true
}

const capabilitiesText = computed({
  get: () => form.capabilities.join(', '),
  set: (value: string) => {
    form.capabilities = value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  },
})

async function save(): Promise<void> {
  if (!form.modelId.trim() || !form.displayName.trim() || !form.providerId.trim() || !form.executorType.trim()) {
    errorMessage.value = 'Model ID、Name、Provider 与 Executor 为必填项。'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    const payload: ModelDefinition = {
      modelId: form.modelId.trim(),
      displayName: form.displayName.trim(),
      providerId: form.providerId.trim(),
      executorType: form.executorType.trim(),
      enabled: form.enabled,
      capabilities: form.capabilities,
    }
    if (editing.value) {
      await modelRegistryApi.updateModel(payload.modelId, payload)
    } else {
      await modelRegistryApi.createModel(payload)
    }
    ElMessage.success(editing.value ? 'Model 已更新' : 'Model 已创建')
    dialogVisible.value = false
    emit('reload')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggle(row: ModelDefinition, enabled: boolean): Promise<void> {
  try {
    await modelRegistryApi.setModelEnabled(row.modelId, enabled)
    emit('reload')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新状态失败')
  }
}
</script>

<template>
  <div>
    <div class="table-toolbar">
      <el-button type="primary" @click="openCreate">New Model</el-button>
    </div>
    <el-table :data="models" v-loading="loading" stripe empty-text="暂无 Model 配置">
      <el-table-column label="Model ID" min-width="200">
        <template #default="{ row }: { row: ModelDefinition }">
          <code class="id-cell">{{ row.modelId }}</code>
          <el-tag
            v-if="props.defaultModelId && row.modelId === props.defaultModelId"
            type="primary"
            effect="dark"
            size="small"
            class="default-badge"
          >Default</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="displayName" label="Name" min-width="160" />
      <el-table-column label="Provider" min-width="140">
        <template #default="{ row }: { row: ModelDefinition }">
          <code>{{ row.providerId }}</code>
          <span class="muted"> · {{ providerName(row.providerId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Executor" min-width="110">
        <template #default="{ row }: { row: ModelDefinition }">
          <el-tag type="info" effect="plain" size="small">{{ row.executorType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Capabilities" min-width="180">
        <template #default="{ row }: { row: ModelDefinition }">
          <span v-if="row.capabilities.length" class="capabilities">{{ row.capabilities.join(', ') }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="Enabled" width="110">
        <template #default="{ row }: { row: ModelDefinition }">
          <el-switch :model-value="row.enabled" @change="(value: boolean | string | number) => toggle(row, Boolean(value))" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }: { row: ModelDefinition }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑 Model' : '新建 Model'" width="min(560px, 94vw)" destroy-on-close @closed="errorMessage = ''">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="Model ID" required>
          <el-input v-model="form.modelId" :disabled="editing" placeholder="例如 deepseek-v4-flash" />
        </el-form-item>
        <el-form-item label="Name" required>
          <el-input v-model="form.displayName" placeholder="例如 DeepSeek V4 Flash" />
        </el-form-item>
        <el-form-item label="Provider" required>
          <el-select v-model="form.providerId" style="width: 100%">
            <el-option
              v-for="provider in providers"
              :key="provider.providerId"
              :label="`${provider.displayName} (${provider.providerId})`"
              :value="provider.providerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Executor" required>
          <el-select v-model="form.executorType" style="width: 100%" allow-create filterable>
            <el-option label="codex" value="codex" />
            <el-option label="openclaw" value="openclaw" />
          </el-select>
        </el-form-item>
        <el-form-item label="Capabilities">
          <el-input v-model="capabilitiesText" placeholder="逗号分隔，例如 coding, git" />
        </el-form-item>
        <el-form-item label="Enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.table-toolbar {
  margin-bottom: 0.75rem;
}

.id-cell {
  color: var(--color-primary-strong);
  font-weight: 600;
}

.muted {
  color: var(--color-text-muted);
}

.capabilities {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.form-error {
  color: var(--color-danger);
}

.default-badge {
  margin-left: 0.375rem;
}
</style>
