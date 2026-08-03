package com.aidevos.orchestrator.manager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAgentRepository implements AgentRepository {
	private final Map<String, AgentDefinition> agents = new LinkedHashMap<>();
	public synchronized void save(AgentDefinition agent) { agents.put(agent.getName(), agent); }
	public synchronized AgentDefinition get(String id) { return agents.get(id); }
	public synchronized List<AgentDefinition> getAll() { return new ArrayList<>(agents.values()); }
	public synchronized void remove(String id) { agents.remove(id); }
}
