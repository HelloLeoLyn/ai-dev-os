export interface ExecutionResult {
  success: boolean
  message: string | null
  output: string | null
  artifacts: ExecutionArtifact[]
  approvalRequired: boolean
  approvalId: string | null
  metadata: Record<string, unknown>
}

export interface ExecutionArtifact {
  type: string | null
  name: string | null
  mediaType: string | null
  uri: string | null
  content: string | null
  metadata: Record<string, unknown>
}

export type ExecutionStatus = 'SUCCESS' | 'FAILED' | 'WAITING_APPROVAL'

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
	 executorName: string | null
  operation: string | null
  output: string | null
  report: ExecutionReport | null
  artifacts: ExecutionArtifact[]
  executionId: string | null
  jobId: string | null
	planRunId: string | null
	stepRunId: string | null
	attemptId: string | null
  workspace: string | null
  sandbox: string | null
  approvalId: string | null
  branch: string | null
  beforeHead: string | null
  afterHead: string | null
  exitCode: number | null
  codexThreadId: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface ExecutionRecordFilters {
  status?: ExecutionStatus
  taskId?: string
}
