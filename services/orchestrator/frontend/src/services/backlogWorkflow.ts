import type { BacklogItem, BacklogStatus } from '../types/backlog'

const actions: Record<BacklogStatus, string> = {
  IDEA: 'Move to PLANNED', PLANNED: 'Mark READY', READY: 'Convert to Task',
  BLOCKED: 'Review blocker', CONVERTED: 'Open Task', DONE: 'Review completed Task',
  CANCELLED: 'No further action',
}

export function backlogNextAction(item: Pick<BacklogItem, 'status' | 'convertedTaskId'>): string {
  return item.status === 'CONVERTED' && !item.convertedTaskId ? 'Recover converted Task link' : actions[item.status]
}
