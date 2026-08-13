export type ValidationStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'BLOCKED' | 'SKIPPED'
export type ValidationDecision = 'PASS' | 'FAIL' | 'BLOCK' | 'REQUIRE_APPROVAL'

export interface ValidationCheck {
  checkId: string
  type: string
  name: string
  status: ValidationStatus
  required: boolean
  blocking: boolean
  startedAt?: string
  completedAt?: string
  durationMs: number
  summary?: string
  errorMessage?: string
  artifactIds: string[]
  metadata: Record<string, unknown>
}

export interface ValidationRun {
  validationRunId: string
  taskId: string
  projectId: string
  workspaceId: string
  planRunId?: string
  executionId?: string
  status: ValidationStatus
  startedAt: string
  completedAt?: string
  checks: ValidationCheck[]
  decision?: ValidationDecision
  summary?: string
}

export type SecuritySeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export interface SecurityFinding { findingId:string; scanner:string; category:string; severity:SecuritySeverity; ruleId:string; title:string; message:string; file?:string; line?:number; column?:number; packageName?:string; installedVersion?:string; fixedVersion?:string; vulnerabilityId?:string; recommendation?:string; blockingCandidate:boolean; fingerprint:string }
export interface SecurityReport { reportId:string; taskId:string; projectId:string; workspaceId:string; validationRunId:string; scanner:string; status:string; findings:SecurityFinding[]; countsBySeverity:Record<SecuritySeverity,number>; startedAt:string; completedAt:string; durationMs:number; artifactIds:string[]; summary:string }
export type QualityGateDecision='PASS'|'BLOCK'|'REQUIRE_APPROVAL'
export interface QualityGateReason{code:string;severity:string;message:string;sourceType:string;sourceId:string;blocking:boolean}
export interface QualityGateResult{gateResultId:string;validationRunId:string;taskId:string;projectId:string;workspaceId:string;decision:QualityGateDecision;status:string;policyVersion:string;evidenceFingerprint:string;reasons:QualityGateReason[];securitySummary:Record<string,number>;validationSummary:Record<string,unknown>;createdAt:string;decidedAt:string;approvalId?:string}
