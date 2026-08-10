package com.aidevos.orchestrator.agent;

import java.time.Instant;

/**
 * A registered agent in the agent registry: its id, type, capability
 * description, lifecycle status and selection priority. This is the
 * registry-level definition (distinct from the executor wiring in
 * com.aidevos.orchestrator.model.AgentDefinition).
 */
public class AgentDefinition {

	private final String agentId;
	private final AgentType agentType;
	private final AgentCapability capabilities;
	private volatile String status;
	private final int priority;
	private final Instant createdAt;

	public AgentDefinition(String agentId, AgentType agentType, AgentCapability capabilities,
			String status, int priority) {
		this.agentId = agentId;
		this.agentType = agentType;
		this.capabilities = capabilities;
		this.status = status == null || status.isBlank() ? "ACTIVE" : status;
		this.priority = priority;
		this.createdAt = Instant.now();
	}

	public String getAgentId() {
		return agentId;
	}

	public AgentType getAgentType() {
		return agentType;
	}

	public AgentCapability getCapabilities() {
		return capabilities;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status == null || status.isBlank() ? "ACTIVE" : status;
	}

	public int getPriority() {
		return priority;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
