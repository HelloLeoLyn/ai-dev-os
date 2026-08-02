package com.aidevos.orchestrator.plan.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StepRun {

	private final String id;
	private final String stepId;
	private final List<StepAttempt> attempts = new ArrayList<>();
	private StepRunStatus status = StepRunStatus.PENDING;
	private String error;
	private Instant startedAt;
	private Instant completedAt;

	public StepRun(String id, String stepId) {
		this.id = id;
		this.stepId = stepId;
	}

	public synchronized StepAttempt startAttempt(String attemptId, Instant time) {
		if (status != StepRunStatus.PENDING) {
			throw new IllegalStateException("Step is not pending: " + stepId);
		}
		status = StepRunStatus.RUNNING;
		startedAt = time;
		StepAttempt attempt = new StepAttempt(attemptId, attempts.size() + 1, time);
		attempts.add(attempt);
		return attempt;
	}

	public synchronized void markWaitingApproval() { status = StepRunStatus.WAITING_APPROVAL; }
	public synchronized void markRunning() { status = StepRunStatus.RUNNING; }
	public synchronized void markSuccess(Instant time) {
		status = StepRunStatus.SUCCESS;
		completedAt = time;
	}
	public synchronized void markFailed(String message, Instant time) {
		status = StepRunStatus.FAILED;
		error = message;
		completedAt = time;
	}
	public synchronized void markSkipped(Instant time) {
		status = StepRunStatus.SKIPPED;
		completedAt = time;
	}

	public String getId() { return id; }
	public String getStepId() { return stepId; }
	public synchronized StepRunStatus getStatus() { return status; }
	public synchronized String getError() { return error; }
	public synchronized Instant getStartedAt() { return startedAt; }
	public synchronized Instant getCompletedAt() { return completedAt; }
	public synchronized List<StepAttempt> getAttempts() { return List.copyOf(attempts); }
	public synchronized StepAttempt getCurrentAttempt() {
		return attempts.isEmpty() ? null : attempts.getLast();
	}
}
