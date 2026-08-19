<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getDefaultModel, getModelRoutes, modelRegistryApi } from '../api/models'
import ModelRouteTable from '../components/ModelRouteTable.vue'
import ModelTable from '../components/ModelTable.vue'
import ProviderTable from '../components/ProviderTable.vue'
import type {
  ModelDefinition,
  ModelRoute,
  ProviderDefinition,
  ProviderStatus,
} from '../types/model'

const providers = ref<ProviderDefinition[]>([])
const models = ref<ModelDefinition[]>([])
const routes = ref<ModelRoute[]>([])
const providerStatuses = ref<Record<string, ProviderStatus>>({})
const defaultModelId = ref<string | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const enabledCount = computed(
  () => providers.value.filter((provider) => provider.enabled).length,
)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    const [providerList, modelList, routeList, defaultModel] = await Promise.all([
      modelRegistryApi.listProviders(),
      modelRegistryApi.listModels(),
      getModelRoutes(),
      getDefaultModel(),
    ])
    providers.value = providerList
    models.value = modelList
    routes.value = routeList
    defaultModelId.value = defaultModel.modelId
    const statuses: Record<string, ProviderStatus> = {}
    await Promise.all(
      providerList.map(async (provider) => {
        try {
          const status = await modelRegistryApi.providerStatus(provider.providerId)
          statuses[provider.providerId] = status
        } catch {
          statuses[provider.providerId] = {
            providerId: provider.providerId,
            credentialRef: provider.credentialRef,
            credentialConfigured: false,
          }
        }
      }),
    )
    providerStatuses.value = statuses
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load models.'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">AI Routing</p>
        <h1>Models</h1>
        <p class="page-description">
          配置 Model / Provider / Executor 绑定。Credential Ref 只保存环境变量 / Secret 引用，不保存 Secret 本身。
        </p>
      </div>
      <el-tag type="info" effect="dark">
        {{ enabledCount }} / {{ providers.length }} providers enabled
      </el-tag>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
      <el-button @click="load">Retry</el-button>
    </el-card>

    <template v-else>
      <el-card shadow="never" class="models-card">
        <template #header>
          <span class="card-title">Provider</span>
          <small class="card-hint">Base URL 与 Credential Ref 由服务端解析；前端只提交引用名称。</small>
        </template>
        <ProviderTable
          :providers="providers"
          :statuses="providerStatuses"
          :loading="loading"
          @reload="load"
        />
      </el-card>

      <el-card shadow="never" class="models-card">
        <template #header>
          <span class="card-title">Model</span>
        </template>
        <ModelTable
          :models="models"
          :providers="providers"
          :default-model-id="defaultModelId"
          :loading="loading"
          @reload="load"
        />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <span class="card-title">路由规则（只读，来自 models.yaml）</span>
        </template>
        <ModelRouteTable :routes="routes" :loading="loading" />
      </el-card>
    </template>
  </section>
</template>

<style scoped>
.models-card {
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 700;
  margin-right: 0.75rem;
}

.card-hint {
  color: var(--color-text-muted);
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
