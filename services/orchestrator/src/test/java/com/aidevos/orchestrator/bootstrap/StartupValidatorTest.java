package com.aidevos.orchestrator.bootstrap;

import java.util.List;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPlugin;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.skill.Skill;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StartupValidatorTest {

	private AgentManager agentManager;
	private SkillRegistryService skillRegistry;
	private McpPluginRegistryService pluginRegistry;
	private StartupValidator validator;

	@BeforeEach
	void setUp() {
		agentManager = mock(AgentManager.class);
		skillRegistry = mock(SkillRegistryService.class);
		pluginRegistry = mock(McpPluginRegistryService.class);
		when(agentManager.getAllAgents()).thenReturn(List.of(agent()));
		when(skillRegistry.listSkills()).thenReturn(List.of(skill()));
		when(pluginRegistry.listPlugins()).thenReturn(List.of(plugin()));
		validator = new StartupValidator(emptyProvider(), agentManager, skillRegistry,
			pluginRegistry);
	}

	@Test
	void inMemoryModeSkipsPostgresCheckAndPasses() {
		assertDoesNotThrow(() -> validator.run(null));
	}

	@Test
	void postgresModeFailsWhenMigrationsIncomplete() {
		PostgresDocumentStore store = mock(PostgresDocumentStore.class);
		when(store.migrationsComplete()).thenReturn(false);
		StartupValidator postgresValidator = new StartupValidator(provider(store), agentManager,
			skillRegistry, pluginRegistry);

		assertThrows(IllegalStateException.class, postgresValidator::validatePostgres);
	}

	@Test
	void postgresModePassesWhenMigrationsComplete() {
		PostgresDocumentStore store = mock(PostgresDocumentStore.class);
		when(store.migrationsComplete()).thenReturn(true);
		StartupValidator postgresValidator = new StartupValidator(provider(store), agentManager,
			skillRegistry, pluginRegistry);

		assertDoesNotThrow(postgresValidator::validatePostgres);
	}

	@Test
	void failsWhenNoAgentsConfigured() {
		when(agentManager.getAllAgents()).thenReturn(List.of());

		assertThrows(IllegalStateException.class, validator::validateAgentConfiguration);
	}

	@Test
	void failsWhenNoSkillsConfigured() {
		when(skillRegistry.listSkills()).thenReturn(List.of());

		assertThrows(IllegalStateException.class, validator::validateSkillConfiguration);
	}

	@Test
	void failsWhenNoPluginsConfigured() {
		when(pluginRegistry.listPlugins()).thenReturn(List.of());

		assertThrows(IllegalStateException.class, validator::validatePluginConfiguration);
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<PostgresDocumentStore> emptyProvider() {
		ObjectProvider<PostgresDocumentStore> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<PostgresDocumentStore> provider(PostgresDocumentStore store) {
		ObjectProvider<PostgresDocumentStore> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(store);
		return provider;
	}

	private AgentDefinition agent() {
		AgentDefinition definition = new AgentDefinition();
		definition.setName("planner");
		return definition;
	}

	private Skill skill() {
		return new Skill("coding-skill", "Coding Skill", null, com.aidevos.orchestrator.skill.SkillType.CODING,
			"1.0.0", true, List.of(), null);
	}

	private McpPlugin plugin() {
		return new McpPlugin("filesystem", "Filesystem", "filesystem", null, "read-only", true,
			List.of());
	}
}
