import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

import AgentDetailView from '../views/AgentDetailView.vue'
import AgentFlowView from '../views/AgentFlowView.vue'
import AgentsView from '../views/AgentsView.vue'
import AuditConsoleView from '../views/AuditConsoleView.vue'
import DashboardView from '../views/DashboardView.vue'
import ExecutionMonitoringView from '../views/ExecutionMonitoringView.vue'
import ExecutionTimelineView from '../views/ExecutionTimelineView.vue'
import ExecutionRecordDetailView from '../views/ExecutionRecordDetailView.vue'
import ExecutionRecordsView from '../views/ExecutionRecordsView.vue'
import JobDetailView from '../views/JobDetailView.vue'
import JobTimelineView from '../views/JobTimelineView.vue'
import JobsView from '../views/JobsView.vue'
import MemoryView from '../views/MemoryView.vue'
import ModelsView from '../views/ModelsView.vue'
import SchedulesView from '../views/SchedulesView.vue'
import TasksView from '../views/TasksView.vue'
import PlanRunTimelineView from '../views/PlanRunTimelineView.vue'
import TimelineConsoleView from '../views/TimelineConsoleView.vue'
import TestsView from '../views/TestsView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView, meta: { title: 'Dashboard' } },
  { path: '/audit', component: AuditConsoleView, meta: { title: 'Audit Console' } },
  {
    path: '/audit/plan-runs/:id',
    component: PlanRunTimelineView,
    meta: { title: 'PlanRun Timeline' },
  },
  {
    path: '/audit/executions/:id',
    component: ExecutionTimelineView,
    meta: { title: 'Execution Timeline' },
  },
  {
    path: '/audit/jobs/:id',
    component: JobTimelineView,
    meta: { title: 'Job Timeline' },
  },
  { path: '/jobs', component: JobsView, meta: { title: 'Jobs' } },
  { path: '/jobs/:id', component: JobDetailView, meta: { title: 'Job Details' } },
  {
    path: '/executions',
    component: ExecutionMonitoringView,
    meta: { title: 'Executions' },
  },
  {
    path: '/timeline',
    component: TimelineConsoleView,
    meta: { title: 'Timeline' },
  },
  {
    path: '/execution-records',
    component: ExecutionRecordsView,
    meta: { title: 'Execution Records' },
  },
  {
    path: '/execution-records/:id',
    component: ExecutionRecordDetailView,
    meta: { title: 'Execution Record Details' },
  },
  { path: '/tasks', component: TasksView, meta: { title: 'Tasks' } },
  { path: '/schedules', component: SchedulesView, meta: { title: 'Schedules' } },
  { path: '/agents', component: AgentsView, meta: { title: 'Agents' } },
  { path: '/agent-flow', component: AgentFlowView, meta: { title: 'Agent Flow' } },
  {
    path: '/agents/:id',
    component: AgentDetailView,
    meta: { title: 'Agent Details' },
  },
  { path: '/models', component: ModelsView, meta: { title: 'Models' } },
  { path: '/memory', component: MemoryView, meta: { title: 'Memory' } },
  { path: '/tests', component: TestsView, meta: { title: 'Tests' } },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.afterEach((route) => {
  const title = typeof route.meta.title === 'string' ? route.meta.title : 'Dashboard'
  document.title = `${title} · AI Dev OS`
})

export default router
