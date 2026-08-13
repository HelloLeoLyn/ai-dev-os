<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import TaskNotificationCenter from '../components/TaskNotificationCenter.vue'
import { useTaskNotifications } from '../composables/useTaskNotifications'
import { isNavigationActive, navigationGroups } from '../navigation'

const taskNotifications = useTaskNotifications()
const route = useRoute()
onMounted(taskNotifications.start)
onBeforeUnmount(taskNotifications.stop)

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
        <section v-for="group in navigationGroups" :key="group.label" class="navigation-group">
          <p>{{ group.label }}</p>
          <RouterLink v-for="item in group.items" :key="item.to" :to="item.to"
            :class="{ 'is-active': isNavigationActive(route.path, item.to) }">
            {{ item.label }}
          </RouterLink>
        </section>
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
