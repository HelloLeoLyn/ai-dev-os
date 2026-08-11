package com.aidevos.orchestrator.collaboration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory agent message store, ordered by insertion time per team.
 */
@Repository
public class InMemoryAgentMessageRepository implements AgentMessageRepository {

	private final Map<String, List<AgentMessage>> messages = new LinkedHashMap<>();

	@Override
	public synchronized void save(AgentMessage message) {
		messages.computeIfAbsent(message.getTeamId(), ignored -> new ArrayList<>())
			.add(message);
	}

	@Override
	public synchronized List<AgentMessage> listByTeam(String teamId) {
		List<AgentMessage> stored = messages.get(teamId);
		return stored == null ? List.of() : List.copyOf(stored);
	}

	@Override
	public synchronized List<AgentMessage> list() {
		return messages.values().stream().flatMap(List::stream).toList();
	}
}
