<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AsyncState from '../components/AsyncState.vue'
import TaskWorkspaceHeader from '../components/TaskWorkspaceHeader.vue'
import TimelineDetail from '../components/TimelineDetail.vue'
import { useTaskContext } from '../composables/useTaskContext'
import { useTimeline } from '../composables/useTimeline'
import { projectTaskWorkflow } from '../services/taskWorkflow'

const route = useRoute(), taskId = String(route.params.taskId || '')
const context = useTaskContext(), timeline = useTimeline()
const workflow = computed(() => context.task.value ? projectTaskWorkflow(context.task.value, context.approval.value?.status) : null)
function load(){return Promise.all([context.load(taskId),timeline.load(taskId)])}
onMounted(load)
</script>
<template><section class="page-stack"><AsyncState :loading="context.loading.value || timeline.loading.value" :error="context.errorMessage.value || timeline.errorMessage.value" :empty="!context.loading.value && !context.task.value" empty-text="Task 不存在" @retry="load"><template v-if="context.task.value && workflow"><TaskWorkspaceHeader :task="context.task.value" :approval="context.approval.value" :workflow="workflow" /><el-card shadow="never"><TimelineDetail :timeline="timeline.timeline.value" :loading="timeline.loading.value" /></el-card></template></AsyncState></section></template>
