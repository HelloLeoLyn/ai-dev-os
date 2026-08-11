package com.aidevos.orchestrator.collaboration;

import java.time.Instant;

/**
 * One message exchanged inside an agent team: the sending and receiving
 * agent, the message kind and the payload. Handoff messages carry the
 * memory context so the receiving agent sees similar tasks, solutions and
 * warnings from project memory.
 */
public class AgentMessage {

	private final String messageId;
	private final String teamId;
	private final String fromAgent;
	private final String toAgent;
	private final AgentMessageType messageType;
	private final String content;
	private final Instant createdAt;

	public AgentMessage(String messageId, String teamId, String fromAgent, String toAgent,
			AgentMessageType messageType, String content, Instant createdAt) {
		this.messageId = messageId;
		this.teamId = teamId;
		this.fromAgent = fromAgent == null ? "" : fromAgent;
		this.toAgent = toAgent;
		this.messageType = messageType == null ? AgentMessageType.REQUEST : messageType;
		this.content = content == null ? "" : content;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
	}

	public String getMessageId() {
		return messageId;
	}

	public String getTeamId() {
		return teamId;
	}

	public String getFromAgent() {
		return fromAgent;
	}

	public String getToAgent() {
		return toAgent;
	}

	public AgentMessageType getMessageType() {
		return messageType;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
