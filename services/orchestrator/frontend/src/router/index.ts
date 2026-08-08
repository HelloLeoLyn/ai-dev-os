import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

const AgentDetailView = () => import('../views/AgentDetailView.vue')
const AgentMarketView = () => import('../views/AgentMarketView.vue')
const AgentMetricsView = () => import('../views/AgentMetricsView.vue')
const AgentFlowView = () => import('../views/AgentFlowView.vue')
const AgentsView = () => import('../views/AgentsView.vue')
const AuditConsoleView = () => import('../views/AuditConsoleView.vue')
const DashboardView = () => import('../views/DashboardView.vue')
const ExecutionMonitoringView = () => import('../views/ExecutionMonitoringView.vue')
const ExecutionTimelineView = () => import('../views/ExecutionTimelineView.vue')
const ExecutionRecordDetailView = () => import('../views/ExecutionRecordDetailView.vue')
const ExecutionRecordsView = () => import('../views/ExecutionRecordsView.vue')
const JobDetailView = () => import('../views/JobDetailView.vue')
const JobTimelineView = () => import('../views/JobTimelineView.vue')
const JobsView = () => import('../views/JobsView.vue')
const MemoryView = () => import('../views/MemoryView.vue')
const McpPluginsView = () => import('../views/McpPluginsView.vue')
const ModelsView = () => import('../views/ModelsView.vue')
const ProjectsView = () => import('../views/ProjectsView.vue')
const SchedulesView = () => import('../views/SchedulesView.vue')
const SkillsView = () => import('../views/SkillsView.vue')
const TasksView = () => import('../views/TasksView.vue')
const WorkspacesView = () => import('../views/WorkspaceView.vue')
const PlanRunTimelineView = () => import('../views/PlanRunTimelineView.vue')
const TimelineConsoleView = () => import('../views/TimelineConsoleView.vue')
const TestsView = () => import('../views/TestsView.vue')

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
    { path: '/projects', component: ProjectsView, meta: { title: 'Projects' } },
  { path: '/workspaces', component: WorkspacesView, meta: { title: 'Workspaces' } },
  { path: '/schedules', component: SchedulesView, meta: { title: 'Schedules' } },
  { path: '/skills', component: SkillsView, meta: { title: 'Skills' } },
  { path: '/agents', component: AgentsView, meta: { title: 'Agents' } },
  { path: '/agent-market', component: AgentMarketView, meta: { title: 'Agent Market' } },
  {
    path: '/agent-metrics',
    component: AgentMetricsView,
    meta: { title: 'Agent Metrics' },
  },
  { path: '/agent-flow', component: AgentFlowView, meta: { title: 'Agent Flow' } },
  {
    path: '/agents/:id',
    component: AgentDetailView,
    meta: { title: 'Agent Details' },
  },
  { path: '/models', component: ModelsView, meta: { title: 'Models' } },
  { path: '/memory', component: MemoryView, meta: { title: 'Memory' } },
  { path: '/mcp/plugins', component: McpPluginsView, meta: { title: 'MCP Plugins' } },
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
