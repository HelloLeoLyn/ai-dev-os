package com.aidevos.orchestrator.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory runtime session store. The runtime is intentionally kept
 * in-memory in this phase (no database migration), so this repository is
 * registered regardless of the persistence type, like the agent registry.
 */
@Repository
public class InMemoryAgentSessionRepository implements AgentSessionRepository {

	private final Map<String, AgentSession> sessions = new LinkedHashMap<>();
	private final Map<String, List<AgentCheckpoint>> checkpoints = new LinkedHashMap<>();

	@Override
	public synchronized void save(AgentSession session) {
		sessions.put(session.getSessionId(), session);
	}

	@Override
	public synchronized AgentSession get(String sessionId) {
		return sessions.get(sessionId);
	}

	@Override
	public synchronized List<AgentSession> listByTask(String taskId) {
		if (taskId == null) {
			return List.of();
		}
		return sessions.values().stream()
			.filter(session -> taskId.equals(session.getTaskId()))
			.toList();
	}

	@Override
	public synchronized List<AgentSession> list() {
		return List.copyOf(sessions.values());
	}

	@Override
	public synchronized void saveCheckpoint(AgentCheckpoint checkpoint) {
		checkpoints.computeIfAbsent(checkpoint.getSessionId(), ignored -> new ArrayList<>())
			.add(checkpoint);
	}

	@Override
	public synchronized List<AgentCheckpoint> listCheckpoints(String sessionId) {
		List<AgentCheckpoint> stored = checkpoints.get(sessionId);
		return stored == null ? List.of() : List.copyOf(stored);
	}
}
