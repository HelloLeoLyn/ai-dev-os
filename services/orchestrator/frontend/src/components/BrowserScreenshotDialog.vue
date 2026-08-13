<script setup lang="ts">
import { validationArtifactContentUrl } from '../api/validations'
defineProps<{ artifactId: string | null; scenario?: string; step?: string; url?: string }>()
const emit=defineEmits<{close:[]}>()
</script>
<template>
  <el-dialog :model-value="Boolean(artifactId)" title="Browser Screenshot" width="min(900px, 92vw)" @close="emit('close')">
    <dl class="details"><dt>Scenario</dt><dd>{{scenario||'—'}}</dd><dt>Step</dt><dd>{{step||'—'}}</dd><dt>URL</dt><dd>{{url||'—'}}</dd></dl>
    <img v-if="artifactId" class="screenshot" :src="validationArtifactContentUrl(artifactId)" alt="Browser acceptance screenshot" />
  </el-dialog>
</template>
<style scoped>.details{display:grid;grid-template-columns:6rem 1fr;gap:.35rem 1rem}.details dt{color:var(--color-text-muted)}.details dd{margin:0;overflow-wrap:anywhere}.screenshot{display:block;max-width:100%;max-height:65vh;margin:1rem auto;border:1px solid var(--color-border);border-radius:var(--radius-md)}</style>
