package com.aidevos.orchestrator.collaboration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One agent collaboration team: the agents participating in a runtime
 * session's execution graph, their joined order and the team lifecycle.
 */
public class AgentTeam {

	private final String teamId;
	private final String taskId;
	private final String sessionId;
	private final List<String> agents = new ArrayList<>();
	private volatile AgentTeamStatus status = AgentTeamStatus.CREATED;
	private final Instant createdAt;
	private volatile Instant updatedAt;

	public AgentTeam(String teamId, String taskId, String sessionId) {
		this.teamId = teamId;
		this.taskId = taskId;
		this.sessionId = sessionId;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public synchronized void addAgent(String agentType) {
		if (agentType != null && !agentType.isBlank() && !agents.contains(agentType)) {
			agents.add(agentType);
		}
		this.updatedAt = Instant.now();
	}

	public synchronized void markRunning() {
		this.status = AgentTeamStatus.RUNNING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markWaiting() {
		this.status = AgentTeamStatus.WAITING;
		this.updatedAt = Instant.now();
	}

	public synchronized void markCompleted() {
		this.status = AgentTeamStatus.COMPLETED;
		this.updatedAt = Instant.now();
	}

	public synchronized void markFailed() {
		this.status = AgentTeamStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	public String getTeamId() {
		return teamId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public List<String> getAgents() {
		return List.copyOf(agents);
	}

	public AgentTeamStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
