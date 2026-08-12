<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import TaskNotificationCenter from '../components/TaskNotificationCenter.vue'
import { useTaskNotifications } from '../composables/useTaskNotifications'

const taskNotifications = useTaskNotifications()
onMounted(taskNotifications.start)
onBeforeUnmount(taskNotifications.stop)

const navigation = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/jobs', label: 'Jobs' },
  { to: '/executions', label: 'Executions' },
  { to: '/timeline', label: 'Timeline' },
  { to: '/audit', label: 'Audit' },
  { to: '/tasks', label: 'Tasks' },
    { to: '/projects', label: 'Projects' },
  { to: '/workspaces', label: 'Workspaces' },
  { to: '/schedules', label: 'Schedules' },
  { to: '/skills', label: 'Skills' },
  { to: '/agents', label: 'Agents' },
  { to: '/agent-market', label: 'Agent Market' },
  { to: '/agent-metrics', label: 'Agent Metrics' },
  { to: '/agent-flow', label: 'Agent Flow' },
  { to: '/models', label: 'Models' },
  { to: '/memory', label: 'Memory' },
  { to: '/mcp/plugins', label: 'MCP Plugins' },
  { to: '/tests', label: 'Tests' },
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

    <div class="app-content">
      <header class="app-topbar">
        <span class="app-topbar__label">Task activity</span>
        <TaskNotificationCenter />
      </header>
      <main class="app-main"><RouterView /></main>
    </div>
  </div>
</template>
