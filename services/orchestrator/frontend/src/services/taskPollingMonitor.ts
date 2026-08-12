import type { TaskRecord, TaskStatus } from '../types/task'

export const TERMINAL_TASK_STATUSES = new Set<TaskStatus>([
  'SUCCESS', 'COMPLETED', 'FAILED', 'REJECTED',
])

export interface StoredTaskMonitor {
  taskId: string
  taskName: string
  lastStatus: TaskStatus
}

export interface MonitorStorage {
  loadTasks(): StoredTaskMonitor[]
  saveTasks(tasks: StoredTaskMonitor[]): void
  hasTerminal(key: string): boolean
  saveTerminal(key: string): void
}

interface TaskPollingMonitorOptions {
  fetchTask: (taskId: string) => Promise<TaskRecord>
  storage: MonitorStorage
  onTerminal: (task: TaskRecord, previousStatus: TaskStatus) => void | Promise<void>
  intervalMs?: number
  schedule?: (callback: () => void, delay: number) => ReturnType<typeof setTimeout>
  cancel?: (timer: ReturnType<typeof setTimeout>) => void
}

export function isTerminalTaskStatus(status: TaskStatus): boolean {
  return TERMINAL_TASK_STATUSES.has(status)
}

export function terminalNotificationKey(taskId: string, status: TaskStatus): string {
  return `${taskId}:${status}`
}

export class TaskPollingMonitor {
  private readonly tasks = new Map<string, StoredTaskMonitor>()
  private readonly timers = new Map<string, ReturnType<typeof setTimeout>>()
  private started = false
  private readonly intervalMs: number
  private readonly schedule: NonNullable<TaskPollingMonitorOptions['schedule']>
  private readonly cancel: NonNullable<TaskPollingMonitorOptions['cancel']>

  constructor(private readonly options: TaskPollingMonitorOptions) {
    this.intervalMs = options.intervalMs ?? 4000
    this.schedule = options.schedule ?? setTimeout
    this.cancel = options.cancel ?? clearTimeout
  }

  start(): void {
    if (this.started) return
    this.started = true
    for (const task of this.options.storage.loadTasks()) {
      if (!isTerminalTaskStatus(task.lastStatus)) this.tasks.set(task.taskId, task)
    }
    this.persist()
    for (const taskId of this.tasks.keys()) void this.poll(taskId)
  }

  stop(): void {
    this.started = false
    for (const timer of this.timers.values()) this.cancel(timer)
    this.timers.clear()
  }

  track(task: TaskRecord): void {
    if (isTerminalTaskStatus(task.status)) {
      const key = terminalNotificationKey(task.taskId, task.status)
      if (!this.options.storage.hasTerminal(key)) {
        this.options.storage.saveTerminal(key)
        void this.options.onTerminal(task, task.status)
      }
      return
    }
    this.tasks.set(task.taskId, {
      taskId: task.taskId,
      taskName: task.name || task.taskId,
      lastStatus: task.status,
    })
    this.persist()
    if (this.started && !this.timers.has(task.taskId)) this.scheduleNext(task.taskId, 0)
  }

  monitoredTaskIds(): string[] {
    return [...this.tasks.keys()]
  }

  async pollNow(taskId: string): Promise<void> {
    await this.poll(taskId, false)
  }

  private async poll(taskId: string, reschedule = true): Promise<void> {
    this.timers.delete(taskId)
    const current = this.tasks.get(taskId)
    if (!current) return
    try {
      const task = await this.options.fetchTask(taskId)
      if (isTerminalTaskStatus(task.status)) {
        this.tasks.delete(taskId)
        this.persist()
        const key = terminalNotificationKey(task.taskId, task.status)
        if (!this.options.storage.hasTerminal(key)) {
          this.options.storage.saveTerminal(key)
          await this.options.onTerminal(task, current.lastStatus)
        }
        return
      }
      this.tasks.set(taskId, {
        taskId,
        taskName: task.name || current.taskName,
        lastStatus: task.status,
      })
      this.persist()
    } catch {
      // A transient polling failure is not a Task failure. Keep monitoring.
    } finally {
      if (reschedule && this.started && this.tasks.has(taskId)) this.scheduleNext(taskId)
    }
  }

  private scheduleNext(taskId: string, delay = this.intervalMs): void {
    if (this.timers.has(taskId)) return
    this.timers.set(taskId, this.schedule(() => void this.poll(taskId), delay))
  }

  private persist(): void {
    this.options.storage.saveTasks([...this.tasks.values()])
  }
}
