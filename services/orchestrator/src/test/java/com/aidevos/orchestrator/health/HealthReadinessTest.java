package com.aidevos.orchestrator.health;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPlugin;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.skill.Skill;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import com.aidevos.orchestrator.skill.SkillType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Verifies the readiness endpoint exposes registry component states without
 * changing the HTTP status semantics.
 */
class HealthReadinessTest {

	@Test
	void detailsIncludeComponentStates() {
		AgentManager agentManager = mock(AgentManager.class);
		when(agentManager.getAllAgents()).thenReturn(List.of(agent()));
		McpPluginRegistryService pluginRegistry = mock(McpPluginRegistryService.class);
		when(pluginRegistry.listPlugins()).thenReturn(List.of(plugin()));
		SkillRegistryService skillRegistry = mock(SkillRegistryService.class);
		when(skillRegistry.listSkills()).thenReturn(List.of(skill()));

		ReadinessGate gate = new ReadinessGate(emptyProvider(), agentManager, pluginRegistry,
			skillRegistry);
		gate.markStartupComplete();

		Map<String, Object> components = components(gate);
		assertEquals("in-memory", components.get("database"));
		assertEquals("none", components.get("migration"));
		assertEquals("up", components.get("agentRegistry"));
		assertEquals("up", components.get("mcpRegistry"));
		assertEquals("up", components.get("skillRegistry"));
	}

	@Test
	void componentsReportDownWhenRegistryEmpty() {
		AgentManager agentManager = mock(AgentManager.class);
		when(agentManager.getAllAgents()).thenReturn(List.of());
		McpPluginRegistryService pluginRegistry = mock(McpPluginRegistryService.class);
		when(pluginRegistry.listPlugins()).thenReturn(List.of());
		SkillRegistryService skillRegistry = mock(SkillRegistryService.class);
		when(skillRegistry.listSkills()).thenReturn(List.of());

		ReadinessGate gate = new ReadinessGate(emptyProvider(), agentManager, pluginRegistry,
			skillRegistry);
		gate.markStartupComplete();

		Map<String, Object> components = components(gate);
		assertEquals("down", components.get("agentRegistry"));
		assertEquals("down", components.get("mcpRegistry"));
		assertEquals("down", components.get("skillRegistry"));
	}

	@Test
	void readinessEndpointReturnsReadyWithComponents() throws Exception {
		AgentManager agentManager = mock(AgentManager.class);
		when(agentManager.getAllAgents()).thenReturn(List.of(agent()));
		McpPluginRegistryService pluginRegistry = mock(McpPluginRegistryService.class);
		when(pluginRegistry.listPlugins()).thenReturn(List.of(plugin()));
		SkillRegistryService skillRegistry = mock(SkillRegistryService.class);
		when(skillRegistry.listSkills()).thenReturn(List.of(skill()));
		ReadinessGate gate = new ReadinessGate(emptyProvider(), agentManager, pluginRegistry,
			skillRegistry);
		gate.markStartupComplete();
		MockMvc mockMvc = standaloneSetup(new HealthController(gate))
			.setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("READY"))
			.andExpect(jsonPath("$.details.components.database").value("in-memory"))
			.andExpect(jsonPath("$.details.components.agentRegistry").value("up"))
			.andExpect(jsonPath("$.details.components.mcpRegistry").value("up"))
			.andExpect(jsonPath("$.details.components.skillRegistry").value("up"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> components(ReadinessGate gate) {
		return (Map<String, Object>) gate.details().get("components");
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<PostgresDocumentStore> emptyProvider() {
		ObjectProvider<PostgresDocumentStore> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	private AgentDefinition agent() {
		AgentDefinition definition = new AgentDefinition();
		definition.setName("planner");
		return definition;
	}

	private Skill skill() {
		return new Skill("coding-skill", "Coding Skill", null, SkillType.CODING, "1.0.0", true,
			List.of(), null);
	}

	private McpPlugin plugin() {
		return new McpPlugin("filesystem", "Filesystem", "filesystem", null, "read-only", true,
			List.of());
	}
}
