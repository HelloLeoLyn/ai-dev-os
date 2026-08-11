export type TraceStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface TraceRecord {
  traceId: string
  taskId: string
  projectId: string | null
  graphId: string | null
  nodeId: string | null
  agentType: string | null
  toolId: string | null
  status: TraceStatus
  startTime: string
  endTime: string | null
  duration: number
  errorMessage: string | null
}

export interface UsageSummary {
  recordCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  estimatedCost: number
}

export interface TimelineEventDTO {
  eventId: string
  eventType: string
  sourceType: string
  sourceId: string
  status: string | null
  message: string
  timestamp: string
}

export interface UnifiedTimeline {
  scopeType: string
  scopeId: string
  events: TimelineEventDTO[]
}

export interface AgentSession {
  sessionId: string
  taskId: string
  graphId: string
  status: string
  currentNodeId: string | null
  startedAt: string
  updatedAt: string
}

export interface AgentMessage {
  messageId: string
  teamId: string
  fromAgent: string
  toAgent: string | null
  messageType: string
  content: string
  createdAt: string
}

export interface HumanApproval {
  approvalId: string
  taskId: string
  sessionId: string | null
  teamId: string | null
  nodeId: string | null
  status: string
  requester: string
  reviewer: string | null
  comment: string | null
  createdAt: string
  reviewedAt: string | null
}

export interface HumanFeedback {
  feedbackId: string
  taskId: string
  sessionId: string | null
  agentType: string
  content: string
  createdAt: string
}

export interface OptimizationRecord {
  id: string
  taskId: string
  sessionId: string | null
  type: string
  recommendation: string
  confidence: number
  createdAt: string
}

export interface AgentScore {
  agentType: string
  totalExecutions: number
  successRate: number
  avgDuration: number
  failureRate: number
  collaborationScore: number
  humanApprovalRate: number
}

export interface GraphOptimizationSuggestion {
  type: string
  nodeId: string | null
  currentAgent: string | null
  recommendedAgent: string | null
  currentTool: string | null
  recommendedTool: string | null
  reason: string
  confidence: number
}

export interface TaskObservability {
  taskId: string
  taskStatus: string
  timeline: UnifiedTimeline
  traces: TraceRecord[]
  agent: {
    taskId: string
    taskStatus: string
    executionCount: number
    successCount: number
    failedCount: number
    totalDurationMillis: number
    averageDurationMillis: number
    repairCount: number
    retryCount: number
    changeCount: number
    approvedChanges: number
    rejectedChanges: number
    reviewPassRate: number
    executions: Array<{
      taskId: string
      agentId: string
      executionId: string
      durationMillis: number
      status: string
      createdAt: string | null
    }>
  }
  toolTraces: TraceRecord[]
  usage: UsageSummary
  sessions: AgentSession[]
  teamId: string | null
  agents: string[]
  messages: AgentMessage[]
  handoffs: string[]
  approvals: HumanApproval[]
  humanFeedback: HumanFeedback[]
  optimizations: OptimizationRecord[]
  recommendations: string[]
  priority: string | null
  assignedAgents: string[]
  orchestrationStatus: string | null
  planId: string | null
  riskLevel: string | null
  estimatedCost: number | null
  feedback: Array<{
    feedbackId: string
    taskId: string
    sessionId: string
    nodeId: string | null
    agentType: string | null
    status: string
    error: string | null
    duration: number
    createdAt: string
  }>
  adaptations: Array<{
    decisionId: string
    taskId: string
    nodeId: string | null
    reason: string
    action: string
    confidence: number
    targetAgent: string | null
    toolId: string | null
  }>
  replans: string[]
  goalId: string | null
  milestoneId: string | null
}

export interface GoalMilestone {
  milestoneId: string
  goalId: string
  title: string
  description: string
  status: string
  progress: number
  createdAt: string
}

export interface GoalTask {
  goalId: string
  taskId: string
  relationType: string
  createdAt: string
}

export interface GoalEvaluation {
  goalId: string
  completedTasks: number
  totalTasks: number
  progress: number
  remainingWork: number
  confidence: number
}

export interface GoalObservability {
  goalId: string
  status: string
  progress: number
  milestones: GoalMilestone[]
  tasks: GoalTask[]
  evaluation: GoalEvaluation
}

export interface ProjectObservability {
  projectId: string
  taskCount: number
  successCount: number
  failedCount: number
  successRate: number
  failureRate: number
  averageDurationMillis: number
  totalTokens: number
  estimatedCost: number
}

export interface AgentObservability {
  agentType: string
  executionCount: number
  successCount: number
  failedCount: number
  successRate: number
  averageDurationMillis: number
  totalTokens: number
  estimatedCost: number
}

export interface ToolMetrics {
  toolId: string
  executeCount: number
  successCount: number
  failedCount: number
  deniedCount: number
  averageDurationMillis: number
}
