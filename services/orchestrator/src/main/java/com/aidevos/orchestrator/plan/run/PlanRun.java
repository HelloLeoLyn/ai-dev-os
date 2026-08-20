package com.aidevos.orchestrator.plan.run;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.plan.Plan;

public class PlanRun {

	private final String id;
	private final String approvalId;
	private final String originalTaskId;
	private final Plan plan;
	private final List<StepRun> steps;
	private final Instant createdAt;
	private PlanRunStatus status = PlanRunStatus.DRAFT;
	private String error;
	private Instant startedAt;
	private Instant completedAt;
	private int version;
	private String coordinatorOwner;
	private long coordinatorToken;
	private Instant coordinatorExpiresAt;

	public PlanRun(String id, String approvalId, Plan plan, List<StepRun> steps, Instant createdAt) {
		this(id, approvalId, null, plan, steps, createdAt);
	}
	public PlanRun(String id, String approvalId, String originalTaskId, Plan plan,
			List<StepRun> steps, Instant createdAt) {
		this.id = id;
		this.approvalId = approvalId;
		this.originalTaskId = originalTaskId;
		this.plan = plan;
		this.steps = List.copyOf(steps);
		this.createdAt = createdAt;
	}
	public static PlanRun restore(String id, String approvalId, Plan plan, List<StepRun> steps,
			Instant createdAt, PlanRunStatus status, String error, Instant startedAt,
			Instant completedAt) {
		return restore(id, approvalId, null, plan, steps, createdAt, status, error, startedAt,
			completedAt);
	}
	public static PlanRun restore(String id, String approvalId, String originalTaskId, Plan plan,
			List<StepRun> steps, Instant createdAt, PlanRunStatus status, String error,
			Instant startedAt, Instant completedAt) {
		PlanRun value = new PlanRun(id, approvalId, originalTaskId, plan, steps, createdAt);
		value.status=status; value.error=error; value.startedAt=startedAt;
		value.completedAt=completedAt;
		return value;
	}
	public synchronized void markRunning(Instant time) {
		status = PlanRunStatus.RUNNING;
		if (startedAt == null) {
			startedAt = time;
		}
	}
	public synchronized void markWaitingApproval() { status = PlanRunStatus.WAITING_APPROVAL; }
	public synchronized void markSuccess(Instant time) {
		status = PlanRunStatus.SUCCESS;
		completedAt = time;
	}
	public synchronized void markFailed(String message, Instant time) {
		status = PlanRunStatus.FAILED;
		error = message;
		completedAt = time;
	}
	public synchronized void markNeedsIntervention(String message, Instant time) {
		status = PlanRunStatus.NEEDS_INTERVENTION;
		error = message;
		completedAt = time;
	}
	public synchronized void markAborted(String message, Instant time) {
		status = PlanRunStatus.ABORTED;
		error = message;
		completedAt = time;
	}

	public synchronized void markReplanRequired(String message, Instant time) {
		status = PlanRunStatus.REPLAN_REQUIRED;
		error = message;
		completedAt = time;
	}

	public synchronized int bumpVersion() { return ++version; }

	public synchronized void setVersion(int version) { this.version = version; }

	public synchronized void applyCoordinatorLease(String owner, long token, Instant expiresAt) {
		this.coordinatorOwner = owner;
		this.coordinatorToken = token;
		this.coordinatorExpiresAt = expiresAt;
	}

	public synchronized void clearCoordinatorLease() {
		this.coordinatorOwner = null;
		this.coordinatorExpiresAt = null;
	}

	public String getId() { return id; }
	public String getApprovalId() { return approvalId; }
	public String getOriginalTaskId() { return originalTaskId; }
	public Plan getPlan() { return plan; }
	public String getPlanId() { return plan.id(); }
	public int getPlanVersion() { return plan.version(); }
	public List<StepRun> getSteps() { return steps; }
	public Instant getCreatedAt() { return createdAt; }
	public synchronized PlanRunStatus getStatus() { return status; }
	public synchronized String getError() { return error; }
	public synchronized Instant getStartedAt() { return startedAt; }
	public synchronized Instant getCompletedAt() { return completedAt; }
	public synchronized int getVersion() { return version; }
	public synchronized String getCoordinatorOwner() { return coordinatorOwner; }
	public synchronized long getCoordinatorToken() { return coordinatorToken; }
	public synchronized Instant getCoordinatorExpiresAt() { return coordinatorExpiresAt; }
}
