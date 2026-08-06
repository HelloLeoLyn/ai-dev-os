<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getTimeline } from '../api/timeline'
import TimelineDetail from '../components/TimelineDetail.vue'
import type { UnifiedTimeline } from '../types/timeline'

const route = useRoute()
const timelineId = ref(
  typeof route.query.id === 'string' ? route.query.id : '',
)
const timeline = ref<UnifiedTimeline | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)

async function loadTimeline(): Promise<void> {
  const id = timelineId.value.trim()
  if (!id) {
    errorMessage.value = '请输入 Timeline ID。'
    return
  }

  loading.value = true
  errorMessage.value = null

  try {
    timeline.value = await getTimeline(id)
  } catch (error) {
    timeline.value = null
    errorMessage.value = error instanceof Error ? error.message : 'Unable to load timeline.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (timelineId.value.trim()) {
    void loadTimeline()
  }
})
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">Execution Chain</p>
        <h1>Timeline</h1>
        <p class="page-description">
          输入 Task、PlanRun、StepRun、Job 或 Execution ID，查看统一执行链路：
          Task → PlanRun → StepRun → Job → Execution → Audit。
        </p>
      </div>
    </header>

    <el-card shadow="never">
      <form class="timeline-form" @submit.prevent="loadTimeline">
        <el-input
          v-model="timelineId"
          class="timeline-input"
          placeholder="例如 task-xxx / job-xxx / exec-xxx / plan-run-xxx"
          clearable
        />
        <el-button type="primary" :loading="loading" native-type="submit">
          查询
        </el-button>
      </form>

      <p v-if="errorMessage" class="timeline-error">{{ errorMessage }}</p>
      <TimelineDetail :timeline="timeline" :loading="loading" />
    </el-card>
  </section>
</template>

<style scoped>
.timeline-form {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}

.timeline-input {
  max-width: 32rem;
}

.timeline-error {
  margin: 0 0 1rem;
  color: var(--color-danger);
}
</style>
