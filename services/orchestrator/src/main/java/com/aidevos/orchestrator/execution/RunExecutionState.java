package com.aidevos.orchestrator.execution;

/**
 * Persisted per-run execution counters and intervention state. The counters
 * survive service restarts through the execution state repository.
 */
public class RunExecutionState {

	private final String runId;
	private int totalAttempts;
	private int aiAttempts;
	private int toolAttempts;
	private int repairAttempts;
	private int replanAttempts;
	private int consecutiveFailures;
	private String interventionStatus = InterventionStatus.NONE.name();
	private String lastFailureClass;
	private String lastSeverity;
	private String lastResponse;
	private int lastAttempt;
	private int lastMaxAttempts;
	private String lastReason;
	private String recommendedAction;

	public RunExecutionState(String runId) {
		this.runId = runId;
	}

	public RunExecutionState(String runId, int totalAttempts, int aiAttempts, int toolAttempts,
			int repairAttempts, int replanAttempts, int consecutiveFailures,
			String interventionStatus, String lastFailureClass, String lastSeverity,
			String lastResponse, int lastAttempt, int lastMaxAttempts, String lastReason,
			String recommendedAction) {
		this.runId = runId;
		this.totalAttempts = totalAttempts;
		this.aiAttempts = aiAttempts;
		this.toolAttempts = toolAttempts;
		this.repairAttempts = repairAttempts;
		this.replanAttempts = replanAttempts;
		this.consecutiveFailures = consecutiveFailures;
		this.interventionStatus = interventionStatus;
		this.lastFailureClass = lastFailureClass;
		this.lastSeverity = lastSeverity;
		this.lastResponse = lastResponse;
		this.lastAttempt = lastAttempt;
		this.lastMaxAttempts = lastMaxAttempts;
		this.lastReason = lastReason;
		this.recommendedAction = recommendedAction;
	}

	public synchronized void incrementTotalAttempts() { totalAttempts++; }
	public synchronized void incrementAiAttempts() { aiAttempts++; }
	public synchronized void incrementToolAttempts() { toolAttempts++; }
	public synchronized void incrementRepairAttempts() { repairAttempts++; }
	public synchronized void incrementReplanAttempts() { replanAttempts++; }
	public synchronized void incrementConsecutiveFailures() { consecutiveFailures++; }
	public synchronized void resetConsecutiveFailures() { consecutiveFailures = 0; }

	public synchronized void recordFailure(FailureClass failureClass, FailureSeverity severity,
			FailureResponse response, int attempt, int maxAttempts, String reason) {
		this.lastFailureClass = failureClass == null ? null : failureClass.name();
		this.lastSeverity = severity == null ? null : severity.name();
		this.lastResponse = response == null ? null : response.name();
		this.lastAttempt = attempt;
		this.lastMaxAttempts = maxAttempts;
		this.lastReason = reason;
	}

	public String getRunId() { return runId; }
	public synchronized int getTotalAttempts() { return totalAttempts; }
	public synchronized int getAiAttempts() { return aiAttempts; }
	public synchronized int getToolAttempts() { return toolAttempts; }
	public synchronized int getRepairAttempts() { return repairAttempts; }
	public synchronized int getReplanAttempts() { return replanAttempts; }
	public synchronized int getConsecutiveFailures() { return consecutiveFailures; }
	public synchronized String getInterventionStatus() { return interventionStatus; }
	public synchronized void setInterventionStatus(String interventionStatus) {
		this.interventionStatus = interventionStatus;
	}
	public synchronized String getLastFailureClass() { return lastFailureClass; }
	public synchronized String getLastSeverity() { return lastSeverity; }
	public synchronized String getLastResponse() { return lastResponse; }
	public synchronized int getLastAttempt() { return lastAttempt; }
	public synchronized int getLastMaxAttempts() { return lastMaxAttempts; }
	public synchronized String getLastReason() { return lastReason; }
	public synchronized String getRecommendedAction() { return recommendedAction; }
	public synchronized void setRecommendedAction(String recommendedAction) {
		this.recommendedAction = recommendedAction;
	}
}
