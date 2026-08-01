<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'

const navigation = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/jobs', label: 'Jobs' },
  { to: '/execution-records', label: 'Executions' },
  { to: '/tasks', label: 'Tasks' },
  { to: '/schedules', label: 'Schedules' },
  { to: '/agents', label: 'Agents' },
]
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="app-brand">
        <span class="app-brand__mark">AI</span>
        <div>
          <strong>AI Dev OS</strong>
          <small>Orchestrator</small>
        </div>
      </div>

      <nav class="app-navigation" aria-label="Primary navigation">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to">
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
