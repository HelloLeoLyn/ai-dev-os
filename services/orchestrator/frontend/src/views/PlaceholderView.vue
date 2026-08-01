<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import BaseCard from '../components/BaseCard.vue'
import StatusBadge from '../components/StatusBadge.vue'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? 'AI Dev OS'))
</script>

<template>
  <section class="page-stack">
    <header class="page-header">
      <div>
        <p class="page-eyebrow">AI Dev OS</p>
        <h1>{{ title }}</h1>
      </div>
      <StatusBadge>Foundation</StatusBadge>
    </header>

    <BaseCard>
      <h2>Frontend infrastructure ready</h2>
      <p class="muted">
        This route is wired and ready for its business view in a later phase.
      </p>
    </BaseCard>
  </section>
</template>
