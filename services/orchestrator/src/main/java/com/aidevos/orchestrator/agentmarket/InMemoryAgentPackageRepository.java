package com.aidevos.orchestrator.agentmarket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAgentPackageRepository implements AgentPackageRepository {

	private final Map<String, AgentPackage> packages = new LinkedHashMap<>();

	@Override
	public synchronized void save(AgentPackage agentPackage) {
		packages.put(agentPackage.getAgentId(), agentPackage);
	}

	@Override
	public synchronized AgentPackage get(String agentId) {
		return packages.get(agentId);
	}

	@Override
	public synchronized List<AgentPackage> list() {
		return new ArrayList<>(packages.values());
	}

	@Override
	public synchronized boolean delete(String agentId) {
		return packages.remove(agentId) != null;
	}
}
