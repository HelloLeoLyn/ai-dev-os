package com.aidevos.orchestrator.runtime;

import java.time.Instant;

import com.aidevos.orchestrator.orchestration.AgentExecutionContext;

/**
 * One checkpoint of a runtime session: the graph node the session reached
 * and the execution context at that point. Completed nodes and failed nodes
 * both produce checkpoints so a session can be recovered from its current
 * node instead of re-running the whole graph.
 */
public class AgentCheckpoint {

	private final String sessionId;
	private final String nodeId;
	private final AgentExecutionContext executionContext;
	private final Instant createdAt;

	public AgentCheckpoint(String sessionId, String nodeId,
			AgentExecutionContext executionContext, Instant createdAt) {
		this.sessionId = sessionId;
		this.nodeId = nodeId;
		this.executionContext = executionContext;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public AgentExecutionContext getExecutionContext() {
		return executionContext;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
