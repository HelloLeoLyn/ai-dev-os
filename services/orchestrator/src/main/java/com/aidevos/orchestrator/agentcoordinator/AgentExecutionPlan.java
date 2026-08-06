package com.aidevos.orchestrator.agentcoordinator;

import java.time.Instant;

/**
 * One step of a collaborative agent execution plan: which agent runs, in which
 * order, and the outcome of that step.
 */
public class AgentExecutionPlan {

	private final String planId;
	private final String taskId;
	private final String agentId;
	private final int step;
	private final Instant createdAt;
	private volatile AgentPlanStatus status = AgentPlanStatus.PENDING;
	private volatile Instant updatedAt;
	private volatile Instant startedAt;
	private volatile Instant completedAt;
	private volatile String result;

	public AgentExecutionPlan(String planId, String taskId, String agentId, int step) {
		this.planId = planId;
		this.taskId = taskId;
		this.agentId = agentId;
		this.step = step;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void markRunning() {
		this.status = AgentPlanStatus.RUNNING;
		this.startedAt = Instant.now();
		this.updatedAt = this.startedAt;
	}

	public synchronized void markSuccess(String result) {
		this.status = AgentPlanStatus.SUCCESS;
		this.result = result;
		this.completedAt = Instant.now();
		this.updatedAt = this.completedAt;
	}

	public synchronized void markFailed(String result) {
		this.status = AgentPlanStatus.FAILED;
		this.result = result;
		this.completedAt = Instant.now();
		this.updatedAt = this.completedAt;
	}

	public String getPlanId() {
		return planId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getAgentId() {
		return agentId;
	}

	public int getStep() {
		return step;
	}

	public AgentPlanStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public String getResult() {
		return result;
	}
}
