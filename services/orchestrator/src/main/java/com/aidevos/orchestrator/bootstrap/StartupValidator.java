package com.aidevos.orchestrator.bootstrap;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Startup configuration and environment validation. In PostgreSQL mode the
 * database connection and schema migration state are verified; agent, skill
 * and MCP plugin configuration are always checked. Failures abort startup
 * with a clear error message. In-memory mode is not affected (no database
 * check, configs are loaded from the bundled YAML files).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StartupValidator implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(StartupValidator.class);

	private final ObjectProvider<PostgresDocumentStore> documentStores;
	private final AgentManager agentManager;
	private final SkillRegistryService skillRegistryService;
	private final McpPluginRegistryService pluginRegistry;

	public StartupValidator(ObjectProvider<PostgresDocumentStore> documentStores,
			AgentManager agentManager, SkillRegistryService skillRegistryService,
			McpPluginRegistryService pluginRegistry) {
		this.documentStores = documentStores;
		this.agentManager = agentManager;
		this.skillRegistryService = skillRegistryService;
		this.pluginRegistry = pluginRegistry;
	}

	@Override
	public void run(ApplicationArguments args) {
		validatePostgres();
		validateAgentConfiguration();
		validateSkillConfiguration();
		validatePluginConfiguration();
		logger.info("Startup validation passed (postgres={})",
			documentStores.getIfAvailable() != null);
	}

	/**
	 * PostgreSQL connection and migration version check. Skipped in-memory.
	 */
	public void validatePostgres() {
		PostgresDocumentStore store = documentStores.getIfAvailable();
		if (store == null) {
			return;
		}
		if (!store.migrationsComplete()) {
			throw new IllegalStateException("Startup validation failed: "
				+ "PostgreSQL connection or schema migrations are not ready");
		}
	}

	public void validateAgentConfiguration() {
		if (agentManager.getAllAgents().isEmpty()) {
			throw new IllegalStateException(
				"Startup validation failed: no agents configured (agents.yaml)");
		}
	}

	public void validateSkillConfiguration() {
		if (skillRegistryService.listSkills().isEmpty()) {
			throw new IllegalStateException(
				"Startup validation failed: no skills configured (skills.yaml)");
		}
	}

	public void validatePluginConfiguration() {
		if (pluginRegistry.listPlugins().isEmpty()) {
			throw new IllegalStateException(
				"Startup validation failed: no MCP plugins configured (mcp-plugins.yaml)");
		}
	}
}
