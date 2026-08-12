import { computed, h, reactive } from 'vue'
import { ElNotification } from 'element-plus'

import { getExecutionRecord, getExecutionRecords } from '../api/executions'
import { getTask } from '../api/tasks'
import router from '../router'
import {
  TaskPollingMonitor,
  type MonitorStorage,
  type StoredTaskMonitor,
} from '../services/taskPollingMonitor'
import { taskExecutionPath, taskTimelinePath } from '../services/taskNotificationNavigation'
import type { TaskRecord, TaskStatus } from '../types/task'

const TASKS_KEY = 'ai-dev-os.task-notifications.monitored.v1'
const NOTIFICATIONS_KEY = 'ai-dev-os.task-notifications.items.v1'
const TERMINALS_KEY = 'ai-dev-os.task-notifications.terminals.v1'

export type TaskNotificationType = 'success' | 'failure' | 'rejected'

export interface TaskNotificationItem {
  id: string
  taskId: string
  taskName: string
  type: TaskNotificationType
  status: TaskStatus
  message: string
  resultSummary: string | null
  artifactCount: number | null
  completedAt: string
  createdAt: string
  read: boolean
}

function parseList<T>(key: string): T[] {
  if (typeof window === 'undefined') return []
  try {
    const value = JSON.parse(window.localStorage.getItem(key) || '[]')
    return Array.isArray(value) ? value as T[] : []
  } catch { return [] }
}

function saveList(key: string, value: unknown[]): void {
  if (typeof window !== 'undefined') window.localStorage.setItem(key, JSON.stringify(value))
}

const terminalKeys = new Set(parseList<string>(TERMINALS_KEY))
const storage: MonitorStorage = {
  loadTasks: () => parseList<StoredTaskMonitor>(TASKS_KEY),
  saveTasks: (tasks) => saveList(TASKS_KEY, tasks),
  hasTerminal: (key) => terminalKeys.has(key),
  saveTerminal: (key) => {
    terminalKeys.add(key)
    saveList(TERMINALS_KEY, [...terminalKeys])
  },
}

const state = reactive({
  notifications: parseList<TaskNotificationItem>(NOTIFICATIONS_KEY).slice(0, 50),
  centerVisible: false,
})

function persistNotifications(): void {
  saveList(NOTIFICATIONS_KEY, state.notifications.slice(0, 50))
}

async function executionSummary(task: TaskRecord): Promise<{
  message: string
  resultSummary: string | null
  artifactCount: number | null
  completedAt: string
}> {
  if (task.status === 'REJECTED') {
    return {
      message: task.errorMessage || 'Plan 已拒绝。',
      resultSummary: task.errorMessage,
      artifactCount: null,
      completedAt: task.updatedAt,
    }
  }
  try {
    const summaries = await getExecutionRecords({ taskId: task.taskId })
    const records = await Promise.all(summaries.map((record) => getExecutionRecord(record.id)))
    const latest = [...records].sort((a, b) => (b.completedAt || '').localeCompare(a.completedAt || ''))[0]
    const message = latest?.message || latest?.output || task.errorMessage
    return {
      message: message || (task.status === 'FAILED' ? '执行失败，后端未返回错误详情。' : '任务执行完成。'),
      resultSummary: message ? message.slice(0, 240) : null,
      artifactCount: records.reduce((count, record) => count + record.artifacts.length, 0),
      completedAt: latest?.completedAt || task.updatedAt,
    }
  } catch {
    return {
      message: task.errorMessage || (task.status === 'FAILED' ? '执行失败，后端未返回错误详情。' : '任务执行完成。'),
      resultSummary: task.errorMessage,
      artifactCount: null,
      completedAt: task.updatedAt,
    }
  }
}

function desktopNotify(item: TaskNotificationItem): void {
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return
  const verb = item.type === 'success' ? '执行成功' : item.type === 'failure' ? '执行失败' : '已拒绝'
  const notification = new Notification('AI Dev OS', { body: `任务“${item.taskName}”${verb}` })
  notification.onclick = () => {
    window.focus()
    void router.push(item.type === 'rejected' ? `/tasks/${encodeURIComponent(item.taskId)}` : taskExecutionPath(item.taskId))
  }
}

async function handleTerminal(task: TaskRecord): Promise<void> {
  const details = await executionSummary(task)
  const type: TaskNotificationType = task.status === 'FAILED'
    ? 'failure' : task.status === 'REJECTED' ? 'rejected' : 'success'
  const item: TaskNotificationItem = {
    id: `${task.taskId}:${task.status}`,
    taskId: task.taskId,
    taskName: task.name || task.taskId,
    type,
    status: task.status,
    message: details.message,
    resultSummary: details.resultSummary,
    artifactCount: details.artifactCount,
    completedAt: details.completedAt,
    createdAt: new Date().toISOString(),
    read: false,
  }
  state.notifications.unshift(item)
  state.notifications.splice(50)
  persistNotifications()

  const title = type === 'success' ? '任务执行成功' : type === 'failure' ? '任务执行失败' : '任务已拒绝'
  ElNotification({
    title,
    type: type === 'failure' ? 'error' : type === 'rejected' ? 'warning' : 'success',
    duration: 0,
    message: h('div', { class: 'task-terminal-toast' }, [
      h('strong', item.taskName),
      h('p', item.message),
      h('button', { onClick: () => void router.push(type === 'rejected' ? `/tasks/${encodeURIComponent(item.taskId)}` : taskExecutionPath(item.taskId)) }, type === 'success' ? '查看结果' : type === 'failure' ? '查看 Execution' : '查看 Task'),
      h('button', { onClick: () => void router.push(taskTimelinePath(item.taskId)) }, '查看 Timeline'),
    ]),
  })
  desktopNotify(item)
}

const monitor = new TaskPollingMonitor({ fetchTask: getTask, storage, onTerminal: handleTerminal })

export function useTaskNotifications() {
  const unreadCount = computed(() => state.notifications.filter((item) => !item.read).length)
  function markRead(id: string): void {
    const item = state.notifications.find((entry) => entry.id === id)
    if (item) { item.read = true; persistNotifications() }
  }
  function markAllRead(): void {
    state.notifications.forEach((item) => { item.read = true })
    persistNotifications()
  }
  async function enableDesktopNotifications(): Promise<NotificationPermission | 'unsupported'> {
    if (typeof Notification === 'undefined') return 'unsupported'
    return Notification.requestPermission()
  }
  return {
    notifications: computed(() => state.notifications), unreadCount,
    centerVisible: computed({ get: () => state.centerVisible, set: (value) => { state.centerVisible = value } }),
    start: () => monitor.start(), stop: () => monitor.stop(), track: (task: TaskRecord) => monitor.track(task),
    markRead, markAllRead, enableDesktopNotifications,
    openExecution: (taskId: string) => router.push(taskExecutionPath(taskId)),
    openTimeline: (taskId: string) => router.push(taskTimelinePath(taskId)),
    openTask: (taskId: string) => router.push(`/tasks/${encodeURIComponent(taskId)}`),
  }
}
