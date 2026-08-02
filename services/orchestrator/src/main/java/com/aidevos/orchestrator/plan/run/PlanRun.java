package com.aidevos.orchestrator.plan.run;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.plan.Plan;

public class PlanRun {

	private final String id;
	private final String approvalId;
	private final Plan plan;
	private final List<StepRun> steps;
	private final Instant createdAt;
	private PlanRunStatus status = PlanRunStatus.DRAFT;
	private String error;
	private Instant startedAt;
	private Instant completedAt;

	public PlanRun(String id, String approvalId, Plan plan, List<StepRun> steps, Instant createdAt) {
		this.id = id;
		this.approvalId = approvalId;
		this.plan = plan;
		this.steps = List.copyOf(steps);
		this.createdAt = createdAt;
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
	public synchronized void markReplanRequired(String message, Instant time) {
		status = PlanRunStatus.REPLAN_REQUIRED;
		error = message;
		completedAt = time;
	}

	public String getId() { return id; }
	public String getApprovalId() { return approvalId; }
	public Plan getPlan() { return plan; }
	public String getPlanId() { return plan.id(); }
	public int getPlanVersion() { return plan.version(); }
	public List<StepRun> getSteps() { return steps; }
	public Instant getCreatedAt() { return createdAt; }
	public synchronized PlanRunStatus getStatus() { return status; }
	public synchronized String getError() { return error; }
	public synchronized Instant getStartedAt() { return startedAt; }
	public synchronized Instant getCompletedAt() { return completedAt; }
}
