<script setup lang="ts">
import { computed, ref } from 'vue'
import type { SecurityFinding, SecurityReport } from '../types/validation'
import StatusBadge from './StatusBadge.vue'
const props=defineProps<{report:SecurityReport|null}>(); defineEmits<{close:[]}>()
const severity=ref('');const scanner=ref('');const category=ref('');const selected=ref<SecurityFinding|null>(null)
const findings=computed(()=>props.report?.findings.filter(f=>(!severity.value||f.severity===severity.value)&&(!scanner.value||f.scanner===scanner.value)&&(!category.value||f.category===category.value))??[])
</script>
<template><el-drawer :model-value="!!report" size="78%" title="Security Report" @close="$emit('close')">
  <template v-if="report"><div class="filters"><el-select v-model="severity" clearable placeholder="Severity"><el-option v-for="v in ['CRITICAL','HIGH','MEDIUM','LOW','INFO']" :key="v" :value="v" /></el-select><el-select v-model="scanner" clearable placeholder="Scanner"><el-option :value="report.scanner" /></el-select><el-select v-model="category" clearable placeholder="Category"><el-option v-for="v in ['SECRET','SAST','DEPENDENCY','CONFIGURATION','IAC','LICENSE','SUPPLY_CHAIN']" :key="v" :value="v" /></el-select></div>
  <el-alert v-if="report.status==='NOT_AVAILABLE'" title="Scanner not available on this machine" type="warning" :closable="false" />
  <el-table :data="findings" @row-click="selected=$event"><el-table-column label="Severity" width="110"><template #default="s"><StatusBadge :status="s.row.severity" size="small" /></template></el-table-column><el-table-column prop="category" label="Category" width="140"/><el-table-column prop="ruleId" label="Rule" min-width="170"/><el-table-column prop="file" label="File" min-width="190"/><el-table-column prop="line" label="Line" width="70"/><el-table-column prop="title" label="Summary" min-width="230"/><el-table-column prop="scanner" label="Scanner" width="110"/></el-table>
  <el-drawer :model-value="!!selected" append-to-body title="Finding detail" @close="selected=null"><template v-if="selected"><StatusBadge :status="selected.severity"/><h3>{{selected.title}}</h3><p>{{selected.message}}</p><dl><dt>Rule</dt><dd>{{selected.ruleId}}</dd><dt>Location</dt><dd>{{selected.file}}:{{selected.line||'—'}}</dd><dt>Recommendation</dt><dd>{{selected.recommendation||'Review finding'}}</dd><dt>Fingerprint</dt><dd><code>{{selected.fingerprint}}</code></dd></dl></template></el-drawer>
  </template></el-drawer></template>
<style scoped>.filters{display:flex;gap:.75rem;margin-bottom:1rem}.filters>*{width:12rem}dl{display:grid;grid-template-columns:7rem 1fr;gap:.5rem}dd{margin:0;overflow-wrap:anywhere}@media(max-width:700px){.filters{flex-direction:column}.filters>*{width:100%}}</style>
