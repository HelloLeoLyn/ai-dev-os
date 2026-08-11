package com.aidevos.orchestrator.adaptive;

import java.time.Instant;

/**
 * One piece of execution feedback collected when a graph node finishes or
 * fails: which task / session / node / agent produced it, the outcome status
 * (COMPLETED / FAILED), the error text, the node duration and when it was
 * collected. The adaptive service consumes the feedback history of a session
 * to decide whether to retry, switch the agent or replan.
 */
public class ExecutionFeedback {

	private final String feedbackId;
	private final String taskId;
	private final String sessionId;
	private final String nodeId;
	private final String agentType;
	private final String status;
	private final String error;
	private final long duration;
	private final Instant createdAt;

	public ExecutionFeedback(String feedbackId, String taskId, String sessionId,
			String nodeId, String agentType, String status, String error, long duration,
			Instant createdAt) {
		this.feedbackId = feedbackId;
		this.taskId = taskId;
		this.sessionId = sessionId;
		this.nodeId = nodeId;
		this.agentType = agentType;
		this.status = status;
		this.error = error;
		this.duration = duration;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
	}

	public String getFeedbackId() {
		return feedbackId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public String getAgentType() {
		return agentType;
	}

	public String getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

	public long getDuration() {
		return duration;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
