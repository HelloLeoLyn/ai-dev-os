<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { isNavigationActive } from '../navigation'
const route = useRoute()
const items = [{ to: '/agents', label: 'Registry' }, { to: '/agent-market', label: 'Market' },
  { to: '/agent-metrics', label: 'Metrics' }, { to: '/agent-flow', label: 'Flow' }]
</script>
<template><nav class="agent-subnav" aria-label="Agent capabilities"><RouterLink v-for="item in items" :key="item.to" :to="item.to" :class="{ active: isNavigationActive(route.path,item.to) }">{{ item.label }}</RouterLink></nav></template>
<style scoped>.agent-subnav{display:flex;max-width:100%;gap:.35rem;overflow-x:auto;padding:.25rem;border:1px solid var(--color-border);border-radius:var(--radius-small);background:var(--color-surface)}.agent-subnav a{flex:0 0 auto;padding:.45rem .7rem;border-radius:.4rem;color:var(--color-text-muted);font-size:.8rem;text-decoration:none}.agent-subnav a.active,.agent-subnav a:hover{color:var(--color-text);background:var(--color-surface-raised)}</style>
