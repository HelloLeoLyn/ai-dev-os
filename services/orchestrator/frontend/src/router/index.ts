import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

import AgentsView from '../views/AgentsView.vue'
import DashboardView from '../views/DashboardView.vue'
import ExecutionRecordDetailView from '../views/ExecutionRecordDetailView.vue'
import ExecutionRecordsView from '../views/ExecutionRecordsView.vue'
import JobDetailView from '../views/JobDetailView.vue'
import JobsView from '../views/JobsView.vue'
import SchedulesView from '../views/SchedulesView.vue'
import TasksView from '../views/TasksView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView, meta: { title: 'Dashboard' } },
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
