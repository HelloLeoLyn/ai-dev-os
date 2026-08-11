package com.aidevos.orchestrator.human;

import java.time.Instant;

/**
 * One piece of human feedback addressed to an agent of a runtime session.
 * The feedback is stored, audited and delivered to the agent's collaboration
 * team as a HUMAN_RESPONSE message.
 */
public class HumanFeedback {

	private final String feedbackId;
	private final String taskId;
	private final String sessionId;
	private final String agentType;
	private final String content;
	private final Instant createdAt;

	public HumanFeedback(String feedbackId, String taskId, String sessionId,
			String agentType, String content, Instant createdAt) {
		this.feedbackId = feedbackId;
		this.taskId = taskId;
		this.sessionId = sessionId;
		this.agentType = agentType == null ? "" : agentType;
		this.content = content == null ? "" : content;
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

	public String getAgentType() {
		return agentType;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
