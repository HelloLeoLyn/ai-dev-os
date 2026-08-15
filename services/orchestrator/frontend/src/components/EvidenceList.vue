<script setup lang="ts">
import type { EvidenceRef } from '../types/analysis'
import StatusBadge from './StatusBadge.vue'

defineProps<{ evidence: EvidenceRef[] }>()
</script>
<template>
  <details v-if="evidence.length" class="evidence-list">
    <summary>Evidence ({{ evidence.length }})</summary>
    <ul><li v-for="item in evidence" :key="`${item.type}:${item.ref}`"><div><StatusBadge :status="item.type" size="small" /><strong>{{ item.label || item.ref }}</strong></div><dl><div><dt>Reference</dt><dd>{{ item.ref }}</dd></div><div v-if="item.uri"><dt>URI</dt><dd><code>{{ item.uri }}</code></dd></div><div v-if="item.line"><dt>Line</dt><dd>{{ item.line }}</dd></div><div v-if="item.artifactType"><dt>Artifact type</dt><dd>{{ item.artifactType }}</dd></div><div v-if="item.contentHash"><dt>Hash</dt><dd><code>{{ item.contentHash }}</code></dd></div></dl></li></ul>
  </details>
</template>
<style scoped>.evidence-list{padding:.65rem .75rem;border:1px solid var(--color-border);border-radius:var(--radius-small);color:var(--color-text-muted)}summary{cursor:pointer;font-weight:700;color:var(--color-text)}ul{display:grid;gap:.75rem;margin:.75rem 0 0;padding:0;list-style:none}li{padding:.7rem;background:rgb(255 255 255 / 2%)}li>div{display:flex;align-items:center;gap:.5rem}dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.45rem;margin:.6rem 0 0;font-size:.78rem}dt{color:var(--color-text-muted);text-transform:uppercase}dd{margin:.15rem 0 0;overflow-wrap:anywhere}@media(max-width:560px){dl{grid-template-columns:1fr}}</style>
