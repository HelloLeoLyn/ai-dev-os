package com.aidevos.orchestrator.runtime;

import java.time.Instant;

/**
 * One long-running agent runtime session: the execution graph of a task
 * wrapped with a lifecycle status and the node the session is currently
 * positioned at. The execution graph executor creates sessions before a
 * graph run and advances the current node as checkpoints are saved.
 */
public class AgentSession {

	private final String sessionId;
	private final String taskId;
	private final String graphId;
	private volatile AgentSessionStatus status = AgentSessionStatus.CREATED;
	private volatile String currentNodeId;
	private volatile Instant startedAt;
	private volatile Instant updatedAt;

	public AgentSession(String sessionId, String taskId, String graphId) {
		this.sessionId = sessionId;
		this.taskId = taskId;
		this.graphId = graphId;
		this.startedAt = Instant.now();
		this.updatedAt = this.startedAt;
	}

	public synchronized void markRunning() {
		this.status = AgentSessionStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markPaused() {
		this.status = AgentSessionStatus.PAUSED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCompleted() {
		this.status = AgentSessionStatus.COMPLETED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		this.status = AgentSessionStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markStopped() {
		this.status = AgentSessionStatus.STOPPED;
		this.updatedAt = Instant.now();
	}

	public synchronized void setCurrentNodeId(String nodeId) {
		this.currentNodeId = nodeId;
		this.updatedAt = Instant.now();
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getGraphId() {
		return graphId;
	}

	public AgentSessionStatus getStatus() {
		return status;
	}

	public String getCurrentNodeId() {
		return currentNodeId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
