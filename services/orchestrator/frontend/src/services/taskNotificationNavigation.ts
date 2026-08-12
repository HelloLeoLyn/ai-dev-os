export function taskExecutionPath(taskId: string): string {
  return `/tasks/${encodeURIComponent(taskId)}/execution`
}

export function taskTimelinePath(taskId: string): string {
  return `/timeline?id=${encodeURIComponent(taskId)}`
}
