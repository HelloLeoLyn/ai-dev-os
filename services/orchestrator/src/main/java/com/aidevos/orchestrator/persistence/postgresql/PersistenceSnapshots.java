package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.approval.*;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.tool.approval.ToolApprovalRequest;

final class PersistenceSnapshots {
	private PersistenceSnapshots() { }

	record Job(String id, TaskDefinition taskSnapshot, Instant createdAt, JobStatus status,
		Instant startedAt, Instant completedAt, ExecutionResult result, String executionRecordId,
		String resultSummary, String errorMessage, String approvalId, int attemptNo, int maxAttempts,
		Instant availableAt, int priority, String leaseOwner, Long leaseToken,
		Instant leaseExpiresAt, Instant heartbeatAt, int version, int recoveryCount,
		String lastFailureCode, ExecutionJob.RecoveryPolicy recoveryPolicy) {
		static Job of(ExecutionJob value) {
			return new Job(value.getId(),value.getTaskSnapshot(),value.getCreatedAt(),value.getStatus(),value.getStartedAt(),value.getCompletedAt(),value.getResult(),value.getExecutionRecordId(),value.getResultSummary(),value.getErrorMessage(),value.getApprovalId(),value.getAttemptNo(),value.getMaxAttempts(),value.getAvailableAt(),value.getPriority(),value.getLeaseOwner(),value.getLeaseToken(),value.getLeaseExpiresAt(),value.getHeartbeatAt(),value.getVersion(),value.getRecoveryCount(),value.getLastFailureCode(),value.getRecoveryPolicy());
		}
		ExecutionJob value() {
			return ExecutionJob.restore(id,taskSnapshot,createdAt,status,startedAt,completedAt,result,executionRecordId,resultSummary,errorMessage,approvalId,attemptNo,maxAttempts,availableAt,priority,leaseOwner,leaseToken,leaseExpiresAt,heartbeatAt,version,recoveryCount,lastFailureCode,recoveryPolicy);
		}
	}
	record CodingApproval(String id,String taskId,String jobId,String workspace,String sandbox,
		String reason,Instant createdAt,ApprovalStatus status,Instant decidedAt) {
		static CodingApproval of(CodingApprovalRequest v){return new CodingApproval(v.getId(),v.getTaskId(),v.getJobId(),v.getWorkspace(),v.getSandbox(),v.getReason(),v.getCreatedAt(),v.getStatus(),v.getDecidedAt());}
		CodingApprovalRequest value(){return CodingApprovalRequest.restore(id,taskId,jobId,workspace,sandbox,reason,createdAt,status,decidedAt);}
	}
	record ToolApproval(String id,String executionId,String invocationId,String jobId,String providerId,
		String toolName,String argumentsHash,String workspace,String permissionLevel,String reason,
		Instant createdAt,ApprovalStatus status,Instant decidedAt){
		static ToolApproval of(ToolApprovalRequest v){return new ToolApproval(v.getId(),v.getExecutionId(),v.getInvocationId(),v.getJobId(),v.getProviderId(),v.getToolName(),v.getArgumentsHash(),v.getWorkspace(),v.getPermissionLevel(),v.getReason(),v.getCreatedAt(),v.getStatus(),v.getDecidedAt());}
		ToolApprovalRequest value(){return ToolApprovalRequest.restore(id,executionId,invocationId,jobId,providerId,toolName,argumentsHash,workspace,permissionLevel,reason,createdAt,status,decidedAt);}
	}
	record PlanApproval(String id,String requestId,Plan plan,String hash,Instant createdAt,
		ApprovalStatus status,ApprovalStatus decision,Instant decidedAt,String approver,String rejectionReason){
		static PlanApproval of(PlanApprovalRequest v){return new PlanApproval(v.getId(),v.getRequestId(),v.getPlan(),v.getPlanSnapshotHash(),v.getCreatedAt(),v.getStatus(),v.getDecision(),v.getDecidedAt(),v.getApprover(),v.getRejectionReason());}
		PlanApprovalRequest value(){return PlanApprovalRequest.restore(id,requestId,plan,hash,createdAt,status,decision,decidedAt,approver,rejectionReason);}
	}
	record Attempt(String id,int number,Instant createdAt,StepRunStatus status,String jobId,
		String executionRecordId,String error,Instant completedAt){
		static Attempt of(StepAttempt v){return new Attempt(v.getId(),v.getNumber(),v.getCreatedAt(),v.getStatus(),v.getJobId(),v.getExecutionRecordId(),v.getError(),v.getCompletedAt());}
		StepAttempt value(){return StepAttempt.restore(id,number,createdAt,status,jobId,executionRecordId,error,completedAt);}
	}
	record Step(String id,String stepId,List<Attempt> attempts,StepRunStatus status,String error,
		Instant startedAt,Instant completedAt){
		static Step of(StepRun v){return new Step(v.getId(),v.getStepId(),v.getAttempts().stream().map(Attempt::of).toList(),v.getStatus(),v.getError(),v.getStartedAt(),v.getCompletedAt());}
		StepRun value(){return StepRun.restore(id,stepId,attempts.stream().map(Attempt::value).toList(),status,error,startedAt,completedAt);}
	}
	record Run(String id,String approvalId,Plan plan,List<Step> steps,Instant createdAt,
		PlanRunStatus status,String error,Instant startedAt,Instant completedAt,
		String coordinatorOwner,Long coordinatorToken,Instant coordinatorExpiresAt){
		static Run of(PlanRun v){return new Run(v.getId(),v.getApprovalId(),v.getPlan(),v.getSteps().stream().map(Step::of).toList(),v.getCreatedAt(),v.getStatus(),v.getError(),v.getStartedAt(),v.getCompletedAt(),v.getCoordinatorOwner(),v.getCoordinatorToken()==0?null:v.getCoordinatorToken(),v.getCoordinatorExpiresAt());}
		PlanRun value(){
			PlanRun run=PlanRun.restore(id,approvalId,plan,steps.stream().map(Step::value).toList(),createdAt,status,error,startedAt,completedAt);
			run.applyCoordinatorLease(coordinatorOwner,coordinatorToken==null?0:coordinatorToken,coordinatorExpiresAt);
			return run;
		}
	}
}
