export interface ExecutionResult {
  success: boolean
  message: string | null
  output: string | null
}

export type ExecutionStatus = 'SUCCESS' | 'FAILED'

export interface ExecutionReport {
  taskId: string | null
  agentName: string | null
  success: boolean
  beforeGitStatus: string | null
  afterGitDiff: string | null
  output: string | null
}

export interface ExecutionRecordSummary {
  id: string
  taskId: string | null
  agentName: string | null
  status: ExecutionStatus
  message: string | null
}

export interface ExecutionRecordDetail extends ExecutionRecordSummary {
  output: string | null
  report: ExecutionReport | null
}

export interface ExecutionRecordFilters {
  status?: ExecutionStatus
  taskId?: string
}
