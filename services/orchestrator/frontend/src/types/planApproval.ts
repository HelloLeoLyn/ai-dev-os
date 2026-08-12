import type { ExecutionMode } from './task'

export type PlanApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CONSUMED'

export interface PlanAgentSnapshot {
  name: string
  executor: string
  capabilities: string[]
  permissionLevel: string | null
  enabled: boolean
}

export interface PlanToolSnapshot {
  providerId: string
  name: string
  access: 'READ_ONLY' | 'WORKSPACE_WRITE'
}

export interface PlanStep {
  id: string
  name: string
  description: string
  assignment: {
    agentName: string | null
    requiredCapabilities: string[]
    fallbackAgentNames: string[]
  }
  toolProviderId: string | null
  toolName: string | null
  failurePolicy: string
  expectedArtifacts: Array<{ name: string; type: string; required: boolean }>
}

export interface PlanApprovalRequest {
  id: string
  requestId: string
  planId: string
  planVersion: number
  planSnapshotHash: string
  status: PlanApprovalStatus
  decision: PlanApprovalStatus | null
  approver: string | null
  rejectionReason: string | null
  plan: {
    id: string
    version: number
    goal: string
    steps: PlanStep[]
    dependencies: Array<{ fromStepId: string; toStepId: string; required: boolean }>
    snapshot: {
      agents: PlanAgentSnapshot[]
      tools: PlanToolSnapshot[]
      executors: string[]
      policyVersion: string
      plannerMetadata: {
        projectId?: string
        workspaceId?: string
        workspacePath?: string
        executionMode?: ExecutionMode
      }
    }
  }
}
