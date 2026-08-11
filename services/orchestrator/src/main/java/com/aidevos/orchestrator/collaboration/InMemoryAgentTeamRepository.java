package com.aidevos.orchestrator.collaboration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory agent team store. Collaboration is kept in-memory in this phase
 * (no database migration), so this repository is registered regardless of
 * the persistence type, like the runtime session repository.
 */
@Repository
public class InMemoryAgentTeamRepository implements AgentTeamRepository {

	private final Map<String, AgentTeam> teams = new LinkedHashMap<>();

	@Override
	public synchronized void save(AgentTeam team) {
		teams.put(team.getTeamId(), team);
	}

	@Override
	public synchronized AgentTeam get(String teamId) {
		return teams.get(teamId);
	}

	@Override
	public synchronized List<AgentTeam> listByTask(String taskId) {
		if (taskId == null) {
			return List.of();
		}
		return teams.values().stream()
			.filter(team -> taskId.equals(team.getTaskId()))
			.toList();
	}

	@Override
	public synchronized List<AgentTeam> list() {
		return List.copyOf(teams.values());
	}
}
