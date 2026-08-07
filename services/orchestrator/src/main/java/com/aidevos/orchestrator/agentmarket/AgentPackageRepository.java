package com.aidevos.orchestrator.agentmarket;

import java.util.List;

/**
 * Persistence boundary for the agent market registry state (version, installed
 * and enabled). Package definitions come from agents-market.yaml; the
 * repository keeps the runtime state across restarts.
 */
public interface AgentPackageRepository {

	void save(AgentPackage agentPackage);

	AgentPackage get(String agentId);

	List<AgentPackage> list();

	boolean delete(String agentId);
}
