<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { getExecutionTimeline } from '../api/audit'
import AuditTimeline from '../components/AuditTimeline.vue'

const route = useRoute()
const scopeId = computed(() => {
  const id = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  return id ?? ''
})
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <RouterLink class="back-link" to="/audit">← Audit Console</RouterLink>
        <p class="page-eyebrow">Execution Timeline</p>
        <h1>{{ scopeId }}</h1>
        <p class="page-description">Agent, Tool/MCP, approval, and execution lifecycle events.</p>
      </div>
    </header>
    <AuditTimeline :scope-id="scopeId" :loader="getExecutionTimeline" />
  </section>
</template>

<style scoped>
.back-link { display: inline-block; margin-bottom: 1.25rem; color: var(--color-text-muted); text-decoration: none; }
.back-link:hover { text-decoration: underline; }
.page-description { color: var(--color-text-muted); }
</style>
