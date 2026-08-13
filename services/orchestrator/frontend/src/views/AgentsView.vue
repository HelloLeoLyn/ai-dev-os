<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getAgentRegistry } from '../api/agents'
import AgentTable from '../components/AgentTable.vue'
import AgentSubnav from '../components/AgentSubnav.vue'
import AsyncState from '../components/AsyncState.vue'
import ConsoleCard from '../components/ConsoleCard.vue'
import type { AgentStatusDTO } from '../types/agent'

const agents = ref<AgentStatusDTO[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

async function loadAgents(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    agents.value = await getAgentRegistry()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load agents.'
  } finally {
    loading.value = false
  }
}

onMounted(loadAgents)
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Dashboard</p>
        <h1>Agent Registry</h1>
        <p class="page-description">
          Hermes、Codex、OpenClaw、MCP Agent 的注册状态与最近活动。
        </p>
      </div>
      <el-tag type="info" effect="dark">{{ agents.length }} registered</el-tag>
    </header>
    <AgentSubnav />
    <AsyncState :loading="loading" :error="errorMessage" :empty="!loading && agents.length === 0" empty-text="暂无 Agent" @retry="loadAgents">
      <ConsoleCard title="Agent Registry"><AgentTable :agents="agents" /></ConsoleCard>
    </AsyncState>
  </section>
</template>
