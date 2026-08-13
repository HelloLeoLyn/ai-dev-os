<script setup lang="ts">withDefaults(defineProps<{ value?: string | null; label?: string }>(), { value: null, label: 'ID' })</script>
<template><span class="technical-id"><small>{{ label }}</small><code :title="value || '—'">{{ value || '—' }}</code></span></template>
<style scoped>.technical-id{display:inline-grid;min-width:0;gap:.15rem;color:var(--color-text-muted)}.technical-id small{font-size:.62rem;letter-spacing:.08em;text-transform:uppercase}.technical-id code{max-width:18rem;overflow:hidden;font-size:.72rem;text-overflow:ellipsis;white-space:nowrap}</style>
