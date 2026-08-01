<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'neutral' | 'info' | 'success' | 'danger'
  }>(),
  {
    tone: 'neutral',
  },
)
</script>

<template>
  <span class="status-badge" :data-tone="tone">
    <slot />
  </span>
</template>
