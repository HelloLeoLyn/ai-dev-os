export interface ScheduledTask {
  id: string
  taskId: string
  cron: string
  enabled: boolean
  zoneId: string
}

export type CreateScheduleRequest = ScheduledTask
