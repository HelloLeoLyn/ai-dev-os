<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { getAgents } from '../api/agents'
import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import type { AgentDefinition } from '../types/agent'

const agents = ref<AgentDefinition[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

async function loadAgents(): Promise<void> {
  try {
    agents.value = await getAgents()
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
        <p class="page-eyebrow">Execution workforce</p>
        <h1>Agents</h1>
      </div>
      <StatusBadge tone="info">{{ agents.length }} registered</StatusBadge>
    </header>

    <BaseCard>
      <p v-if="loading" class="table-state">Loading agents…</p>
      <p v-else-if="errorMessage" class="table-state table-state--error">
        {{ errorMessage }}
      </p>

      <div v-else class="table-scroll">
        <table class="agents-table">
          <thead>
            <tr>
              <th>Agent name</th>
              <th>Executor</th>
              <th>Capabilities</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="agents.length === 0">
              <td colspan="4" class="table-state">No agents registered.</td>
            </tr>
            <tr v-for="(agent, index) in agents" :key="agent.name || index">
              <td class="agent-name">{{ agent.name || '—' }}</td>
              <td><code>{{ agent.executor || '—' }}</code></td>
              <td>
                <div class="capability-list">
                  <span
                    v-for="capability in agent.capabilities || []"
                    :key="capability"
                    class="capability"
                  >
                    {{ capability }}
                  </span>
                  <span v-if="!agent.capabilities?.length">—</span>
                </div>
              </td>
              <td>
                <StatusBadge v-if="agent.type">{{ agent.type }}</StatusBadge>
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </BaseCard>
  </section>
</template>

<style scoped>
.table-scroll {
  overflow-x: auto;
}

.agents-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
  text-align: left;
}

.agents-table th,
.agents-table td {
  padding: 1rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
  vertical-align: top;
}

.agents-table th {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.agents-table tbody tr:last-child td {
  border-bottom: 0;
}

.agent-name {
  color: var(--color-primary-strong);
  font-weight: 700;
}

.capability-list {
  display: flex;
  min-width: 12rem;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.capability {
  padding: 0.2rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-info);
  font-size: 0.75rem;
}

.table-state {
  color: var(--color-text-muted);
  text-align: center;
}

.table-state--error {
  color: var(--color-danger);
}
</style>
