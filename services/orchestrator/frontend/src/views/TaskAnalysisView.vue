<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AnalysisInsights from '../components/AnalysisInsights.vue'
import AsyncState from '../components/AsyncState.vue'
import TaskWorkspaceHeader from '../components/TaskWorkspaceHeader.vue'
import { useTaskContext } from '../composables/useTaskContext'
import { projectTaskWorkflow } from '../services/taskWorkflow'

const route = useRoute(), taskId = String(route.params.taskId || '')
const context = useTaskContext()
const workflow = computed(() => context.task.value ? projectTaskWorkflow(context.task.value, context.approval.value?.status) : null)
onMounted(() => context.load(taskId))
</script>
<template><section class="page-stack"><AsyncState :loading="context.loading.value" :error="context.errorMessage.value" :empty="!context.loading.value && !context.task.value" empty-text="Task 不存在" @retry="context.load(taskId)"><template v-if="context.task.value && workflow"><TaskWorkspaceHeader :task="context.task.value" :approval="context.approval.value" :workflow="workflow" /><AnalysisInsights :task="context.task.value" :approval="context.approval.value" /></template></AsyncState></section></template>
