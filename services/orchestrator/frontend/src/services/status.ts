export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'safe' | 'write'

export function statusTone(status: string | null | undefined): StatusTone {
  const value = String(status || '').toUpperCase()
  if (['SUCCESS', 'COMPLETED', 'ACTIVE', 'APPROVED', 'CONSUMED'].includes(value)) return 'success'
  if (['FAILED', 'ERROR', 'REJECTED'].includes(value)) return 'danger'
  if (['RUNNING', 'CODING', 'TESTING'].includes(value)) return 'info'
  if (['ONLINE', 'READY', 'IDLE'].includes(value)) return value === 'IDLE' ? 'warning' : 'success'
  if (['DISABLED', 'CANCELLED', 'ABANDONED'].includes(value)) return 'danger'
  if (['RECOVERY_REQUIRED', 'RETRY_WAIT', 'STARTING'].includes(value)) return 'warning'
  if (['PENDING', 'PLANNING', 'CREATED', 'QUEUED', 'WAITING_APPROVAL'].includes(value)) return 'warning'
  if (value === 'READ_ONLY') return 'safe'
  if (value === 'SAFE') return 'safe'
  if (value === 'REVIEW') return 'danger'
  if (['READ_WRITE', 'WORKSPACE_WRITE'].includes(value)) return 'write'
  return 'neutral'
}
