import type { BacklogStatus } from '../types/backlog'

export function canBlockBacklog(status: BacklogStatus): boolean {
  return status === 'PLANNED' || status === 'READY'
}

export function canUnblockBacklog(status: BacklogStatus): boolean {
  return status === 'BLOCKED'
}
