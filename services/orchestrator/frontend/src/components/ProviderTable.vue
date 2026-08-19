<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { modelRegistryApi } from '../api/models'
import type { ProviderDefinition, ProviderStatus } from '../types/model'

const props = defineProps<{
  providers: ProviderDefinition[]
  statuses?: Record<string, ProviderStatus>
  loading?: boolean
}>()

const statusOf = (row: ProviderDefinition): ProviderStatus | undefined =>
  props.statuses?.[row.providerId]

const emit = defineEmits<{ (e: 'reload'): void }>()

const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const errorMessage = ref('')

const emptyForm = (): ProviderDefinition => ({
  providerId: '',
  displayName: '',
  baseUrl: '',
  credentialRef: '',
  enabled: true,
})
const form = reactive<ProviderDefinition>(emptyForm())

function openCreate(): void {
  editing.value = false
  Object.assign(form, emptyForm())
  errorMessage.value = ''
  dialogVisible.value = true
}

function openEdit(row: ProviderDefinition): void {
  editing.value = true
  Object.assign(form, {
    providerId: row.providerId,
    displayName: row.displayName,
    baseUrl: row.baseUrl ?? '',
    credentialRef: row.credentialRef ?? '',
    enabled: row.enabled,
  })
  errorMessage.value = ''
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!form.providerId.trim() || !form.displayName.trim()) {
    errorMessage.value = 'Provider ID 与 Name 为必填项。'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    const baseUrl = (form.baseUrl ?? '').trim()
    const credentialRef = (form.credentialRef ?? '').trim()
    const payload: ProviderDefinition = {
      providerId: form.providerId.trim(),
      displayName: form.displayName.trim(),
      baseUrl: baseUrl || null,
      credentialRef: credentialRef || null,
      enabled: form.enabled,
    }
    if (editing.value) {
      await modelRegistryApi.updateProvider(payload.providerId, payload)
    } else {
      await modelRegistryApi.createProvider(payload)
    }
    ElMessage.success(editing.value ? 'Provider 已更新' : 'Provider 已创建')
    dialogVisible.value = false
    emit('reload')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggle(row: ProviderDefinition, enabled: boolean): Promise<void> {
  try {
    await modelRegistryApi.setProviderEnabled(row.providerId, enabled)
    emit('reload')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新状态失败')
  }
}
</script>

<template>
  <div>
    <div class="table-toolbar">
      <el-button type="primary" @click="openCreate">New Provider</el-button>
    </div>
    <el-table :data="providers" v-loading="loading" stripe empty-text="暂无 Provider 配置">
      <el-table-column label="Provider ID" min-width="140">
        <template #default="{ row }: { row: ProviderDefinition }">
          <code class="id-cell">{{ row.providerId }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="displayName" label="Name" min-width="140" />
      <el-table-column label="Base URL" min-width="220">
        <template #default="{ row }: { row: ProviderDefinition }">
          <code>{{ row.baseUrl || '—' }}</code>
        </template>
      </el-table-column>
      <el-table-column label="Credential" min-width="200">
        <template #default="{ row }: { row: ProviderDefinition }">
          <template v-if="row.credentialRef">
            <el-tag type="warning" effect="plain" size="small">{{ row.credentialRef }}</el-tag>
            <el-tag
              v-if="statusOf(row)?.credentialConfigured"
              type="success"
              effect="plain"
              size="small"
              class="credential-state"
            >Configured</el-tag>
            <el-tag
              v-else
              type="danger"
              effect="plain"
              size="small"
              class="credential-state"
            >Missing</el-tag>
          </template>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="Enabled" width="110">
        <template #default="{ row }: { row: ProviderDefinition }">
          <el-switch :model-value="row.enabled" @change="(value: boolean | string | number) => toggle(row, Boolean(value))" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }: { row: ProviderDefinition }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑 Provider' : '新建 Provider'" width="min(560px, 94vw)" destroy-on-close @closed="errorMessage = ''">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="Provider ID" required>
          <el-input v-model="form.providerId" :disabled="editing" placeholder="例如 deepseek" />
        </el-form-item>
        <el-form-item label="Name" required>
          <el-input v-model="form.displayName" placeholder="例如 DeepSeek" />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
        </el-form-item>
        <el-form-item label="Credential Ref（环境变量 / Secret 引用）">
          <el-input v-model="form.credentialRef" placeholder="例如 DEEPSEEK_API_KEY" />
          <small>仅保存引用名称；服务端运行时从环境变量 / Secret 读取实际值，API 与日志不返回 Secret。</small>
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

.form-error {
  color: var(--color-danger);
}

.credential-state {
  margin-left: 0.375rem;
}

small {
  color: var(--color-text-muted);
}
</style>
