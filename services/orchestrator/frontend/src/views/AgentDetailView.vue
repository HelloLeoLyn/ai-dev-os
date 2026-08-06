<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { getAgentDetail, getAgentHistory } from '../api/agents'
import AgentDetailCard from '../components/AgentDetailCard.vue'
import AgentExecutionHistory from '../components/AgentExecutionHistory.vue'
import type { AgentDetailDTO, AgentHistoryDTO } from '../types/agent'

const route = useRoute()
const agent = ref<AgentDetailDTO | null>(null)
const history = ref<AgentHistoryDTO | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)

async function loadAgent(): Promise<void> {
  const agentId = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id

  if (!agentId) {
    errorMessage.value = 'Agent ID is required.'
    loading.value = false
    return
  }

  loading.value = true
  errorMessage.value = null

  try {
    const [detail, historyData] = await Promise.all([
      getAgentDetail(agentId),
      getAgentHistory(agentId),
    ])
    agent.value = detail
    history.value = historyData
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load agent.'
  } finally {
    loading.value = false
  }
}

onMounted(loadAgent)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <RouterLink class="back-link" to="/agents">← All agents</RouterLink>
        <p class="page-eyebrow">Agent Registry</p>
        <h1>{{ agent?.name || 'Agent' }}</h1>
      </div>
    </header>

    <el-card v-if="errorMessage" shadow="never">
      <p class="page-state page-state--error">{{ errorMessage }}</p>
    </el-card>

    <template v-else>
      <AgentDetailCard :agent="agent" :loading="loading" class="detail-card" />
      <AgentExecutionHistory :history="history" :loading="loading" />
    </template>
  </section>
</template>

<style scoped>
.back-link {
  display: inline-block;
  margin-bottom: 1.25rem;
  color: var(--color-text-muted);
  text-decoration: none;
}

.back-link:hover {
  text-decoration: underline;
}

.detail-card {
  margin-bottom: 1rem;
}

.page-state {
  color: var(--color-text-muted);
  text-align: center;
}

.page-state--error {
  color: var(--color-danger);
}
</style>
