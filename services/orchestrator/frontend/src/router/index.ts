import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

import AgentsView from '../views/AgentsView.vue'
import AuditConsoleView from '../views/AuditConsoleView.vue'
import DashboardView from '../views/DashboardView.vue'
import ExecutionTimelineView from '../views/ExecutionTimelineView.vue'
import ExecutionRecordDetailView from '../views/ExecutionRecordDetailView.vue'
import ExecutionRecordsView from '../views/ExecutionRecordsView.vue'
import JobDetailView from '../views/JobDetailView.vue'
import JobTimelineView from '../views/JobTimelineView.vue'
import JobsView from '../views/JobsView.vue'
import SchedulesView from '../views/SchedulesView.vue'
import TasksView from '../views/TasksView.vue'
import PlanRunTimelineView from '../views/PlanRunTimelineView.vue'

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
