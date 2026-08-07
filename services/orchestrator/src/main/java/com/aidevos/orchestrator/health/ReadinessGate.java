package com.aidevos.orchestrator.health;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Readiness gate for the operations phase. The application becomes ready only
 * after startup completes and, in PostgreSQL mode, after the V1..V7 schema
 * migrations have been fully applied. In-memory mode has no migration step and
 * only waits for startup.
 */
@Component
public class ReadinessGate {

	private final ObjectProvider<PostgresDocumentStore> documentStores;
	private final AgentManager agentManager;
	private final McpPluginRegistryService pluginRegistry;
	private final SkillRegistryService skillRegistry;
	private volatile boolean startupComplete;

	public ReadinessGate(ObjectProvider<PostgresDocumentStore> documentStores) {
		this(documentStores, null, null, null);
	}

	@Autowired
	public ReadinessGate(ObjectProvider<PostgresDocumentStore> documentStores,
			AgentManager agentManager, McpPluginRegistryService pluginRegistry,
			SkillRegistryService skillRegistry) {
		this.documentStores = documentStores;
		this.agentManager = agentManager;
		this.pluginRegistry = pluginRegistry;
		this.skillRegistry = skillRegistry;
	}

	@EventListener(ApplicationReadyEvent.class)
	void markStartupComplete() {
		startupComplete = true;
	}

	public boolean isReady() {
		if (!startupComplete) {
			return false;
		}
		PostgresDocumentStore store = documentStores.getIfAvailable();
		return store == null || store.migrationsComplete();
	}

	public Map<String, Object> details() {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("startupComplete", startupComplete);
		Map<String, Object> components = new LinkedHashMap<>();
		PostgresDocumentStore store = documentStores.getIfAvailable();
		if (store != null) {
			details.put("migrations", store.migrationsComplete() ? "complete" : "pending");
			components.put("database", store.migrationsComplete() ? "up" : "down");
			components.put("migration", store.migrationsComplete() ? "complete" : "pending");
		}
		else {
			details.put("migrations", "none");
			components.put("database", "in-memory");
			components.put("migration", "none");
		}
		components.put("agentRegistry",
			agentManager != null && !agentManager.getAllAgents().isEmpty() ? "up" : "down");
		components.put("mcpRegistry",
			pluginRegistry != null && !pluginRegistry.listPlugins().isEmpty() ? "up" : "down");
		components.put("skillRegistry",
			skillRegistry != null && !skillRegistry.listSkills().isEmpty() ? "up" : "down");
		details.put("components", components);
		return details;
	}
}
