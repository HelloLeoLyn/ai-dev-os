package com.aidevos.orchestrator.validation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ValidationRun {
	private String validationRunId;
	private String taskId;
	private String projectId;
	private String workspaceId;
	private String planRunId;
	private String executionId;
	private ValidationStatus status = ValidationStatus.PENDING;
	private Instant startedAt;
	private Instant completedAt;
	private List<ValidationCheck> checks = new ArrayList<>();
	private ValidationDecision decision;
	private String summary;

	public ValidationRun() { }

	public ValidationRun(String validationRunId, String taskId, String projectId,
			String workspaceId, String planRunId, String executionId) {
		this.validationRunId = validationRunId;
		this.taskId = taskId;
		this.projectId = projectId;
		this.workspaceId = workspaceId;
		this.planRunId = planRunId;
		this.executionId = executionId;
	}

	public String getValidationRunId() { return validationRunId; }
	public void setValidationRunId(String value) { validationRunId = value; }
	public String getTaskId() { return taskId; }
	public void setTaskId(String value) { taskId = value; }
	public String getProjectId() { return projectId; }
	public void setProjectId(String value) { projectId = value; }
	public String getWorkspaceId() { return workspaceId; }
	public void setWorkspaceId(String value) { workspaceId = value; }
	public String getPlanRunId() { return planRunId; }
	public void setPlanRunId(String value) { planRunId = value; }
	public String getExecutionId() { return executionId; }
	public void setExecutionId(String value) { executionId = value; }
	public ValidationStatus getStatus() { return status; }
	public void setStatus(ValidationStatus value) { status = value; }
	public Instant getStartedAt() { return startedAt; }
	public void setStartedAt(Instant value) { startedAt = value; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant value) { completedAt = value; }
	public List<ValidationCheck> getChecks() { return checks; }
	public void setChecks(List<ValidationCheck> value) {
		checks = value == null ? new ArrayList<>() : new ArrayList<>(value);
	}
	public ValidationDecision getDecision() { return decision; }
	public void setDecision(ValidationDecision value) { decision = value; }
	public String getSummary() { return summary; }
	public void setSummary(String value) { summary = value; }
}
