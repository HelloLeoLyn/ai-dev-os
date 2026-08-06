package com.aidevos.orchestrator.testagent;

import java.time.Instant;

/**
 * A test task planned by the Testing Agent: what to run, its lifecycle and the
 * execution result. Linked to TaskCenter via taskId and to Execution via the
 * optional executionId; lifecycle is recorded through Audit.
 */
public class TestPlan {

	private final String testId;
	private final String taskId;
	private final TestType testType;
	private final String command;
	private final String projectId;
	private final String executionId;
	private final Instant createdAt;
	private volatile TestStatus status = TestStatus.QUEUED;
	private volatile Instant updatedAt;
	private volatile Instant startedAt;
	private volatile Instant completedAt;
	private volatile String result;
	private volatile String logs;
	private volatile String errorMessage;
	private volatile String screenshotPath;

	public TestPlan(String testId, String taskId, TestType testType, String command,
			String projectId, String executionId) {
		this.testId = testId;
		this.taskId = taskId;
		this.testType = testType;
		this.command = command;
		this.projectId = projectId;
		this.executionId = executionId;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void markQueued() {
		this.status = TestStatus.QUEUED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markRunning() {
		this.status = TestStatus.RUNNING;
		this.startedAt = Instant.now();
		this.updatedAt = this.startedAt;
	}

	public synchronized void markSuccess(String result, String logs) {
		this.status = TestStatus.SUCCESS;
		this.result = result;
		this.logs = logs;
		this.completedAt = Instant.now();
		this.updatedAt = this.completedAt;
	}

	public synchronized void markFailed(String errorMessage, String logs) {
		this.status = TestStatus.FAILED;
		this.errorMessage = errorMessage;
		this.logs = logs;
		this.completedAt = Instant.now();
		this.updatedAt = this.completedAt;
	}

	public synchronized void setScreenshotPath(String screenshotPath) {
		this.screenshotPath = screenshotPath;
	}

	public String getTestId() {
		return testId;
	}

	public String getTaskId() {
		return taskId;
	}

	public TestType getTestType() {
		return testType;
	}

	public String getCommand() {
		return command;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getExecutionId() {
		return executionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public TestStatus getStatus() {
		return status;
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

	public String getLogs() {
		return logs;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getScreenshotPath() {
		return screenshotPath;
	}
}
