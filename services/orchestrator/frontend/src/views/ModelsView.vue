<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getModels, getModelRoutes } from '../api/models'
import ModelRouteTable from '../components/ModelRouteTable.vue'
import ModelTable from '../components/ModelTable.vue'
import type { ModelProvider, ModelRoute } from '../types/model'

const models = ref<ModelProvider[]>([])
const routes = ref<ModelRoute[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

const enabledCount = computed(
  () => models.value.filter((model) => model.enabled).length,
)

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    const [modelList, routeList] = await Promise.all([getModels(), getModelRoutes()])
    models.value = modelList
    routes.value = routeList
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
          多模型路由：根据任务类型选择 OpenAI / DeepSeek / Codex / OpenClaw。
        </p>
      </div>
      <el-tag type="info" effect="dark">
        {{ enabledCount }} / {{ models.length }} enabled
      </el-tag>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <template v-else>
      <el-card shadow="never" class="models-card">
        <template #header>
          <span class="card-title">模型配置</span>
        </template>
        <ModelTable :models="models" :loading="loading" />
      </el-card>

      <el-card shadow="never">
        <template #header>
          <span class="card-title">路由规则</span>
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
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
