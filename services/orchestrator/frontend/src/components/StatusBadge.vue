<script setup lang="ts">
import { computed } from 'vue'
import { statusTone, type StatusTone } from '../services/status'

const props = withDefaults(
  defineProps<{
    status?: string | null
    tone?: StatusTone
    size?: 'small' | 'default'
  }>(),
  {
    tone: 'neutral',
  },
)

const resolvedTone = computed(() => props.status ? statusTone(props.status) : props.tone)
</script>

<template>
  <span class="status-badge" :class="{ 'status-badge--small': size === 'small' }" :data-tone="resolvedTone">
    <slot>{{ status || '—' }}</slot>
  </span>
</template>
