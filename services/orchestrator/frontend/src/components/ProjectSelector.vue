<script setup lang="ts">
import { computed } from 'vue'

import type { Project } from '../types/project'

const props = defineProps<{
  projects: Project[]
  currentProjectId?: string | null
  loading?: boolean
}>()

const emit = defineEmits<{
  switchProject: [projectId: string]
}>()

const activeProjects = computed(() =>
  props.projects.filter((project) => project.status === 'ACTIVE'),
)
</script>

<template>
  <div class="project-selector">
    <span class="label">当前项目</span>
    <el-select
      :model-value="currentProjectId ?? ''"
      :loading="loading"
      placeholder="选择当前项目"
      class="selector"
      @change="(value: string) => value && emit('switchProject', value)"
    >
      <el-option
        v-for="project in activeProjects"
        :key="project.projectId"
        :label="project.name"
        :value="project.projectId"
      />
    </el-select>
  </div>
</template>

<style scoped>
.project-selector {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.label {
  font-weight: 700;
  white-space: nowrap;
}

.selector {
  width: 16rem;
}
</style>
