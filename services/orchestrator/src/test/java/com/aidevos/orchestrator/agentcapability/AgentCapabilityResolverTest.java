package com.aidevos.orchestrator.agentcapability;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.modelrouter.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityResolverTest {

	private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
	private static final Instant T1 = Instant.parse("2026-08-01T00:00:01Z");
	private static final Instant T2 = Instant.parse("2026-08-01T00:00:02Z");

	private AgentManager agentManager;
	private AgentCapabilityResolver resolver;

	@BeforeEach
	void setUp() {
		agentManager = new AgentManager();
		resolver = new AgentCapabilityResolver(agentManager);
	}

	@Test
	void shouldMapTaskTypeToCapability() {
		assertEquals("planning", resolver.capabilityFor(TaskType.TASK_ANALYSIS));
		assertEquals("coding", resolver.capabilityFor(TaskType.CODE_GENERATION));
		assertEquals("browser", resolver.capabilityFor(TaskType.BROWSER_TEST));
		assertEquals("testing", resolver.capabilityFor(TaskType.TEST_VERIFY));
		assertEquals("planning", resolver.capabilityFor(TaskType.GENERAL));
		assertEquals("planning", resolver.capabilityFor(null));
	}

	@Test
	void shouldResolveAgentForTaskTypeAndCapability() {
		agentManager.register(agent("planner", "1.0.0", List.of("planning", "analysis"), true, T0));
		agentManager.register(agent("coder", "1.0.0", List.of("coding", "git"), true, T1));

		assertEquals("planner", resolver.resolveAgent(TaskType.TASK_ANALYSIS).orElseThrow()
			.getName());
		assertEquals("coder", resolver.resolveAgent("coding").orElseThrow().getName());
	}

	@Test
	void shouldReturnEmptyWhenNoAgentProvidesCapability() {
		agentManager.register(agent("coder", "1.0.0", List.of("coding"), true, T0));

		assertTrue(resolver.resolveAgent("browser").isEmpty());
		assertTrue(resolver.resolveAgent((TaskType) null).isEmpty());
		assertTrue(resolver.resolveAgent(" ").isEmpty());
	}

	@Test
	void shouldPreferEnabledAgentOverDisabled() {
		agentManager.register(agent("browser-disabled", "9.0.0", List.of("browser"), false, T0));
		agentManager.register(agent("browser-enabled", "0.5.0", List.of("browser"), true, T1));

		assertEquals("browser-enabled", resolver.resolveAgent("browser").orElseThrow()
			.getName());
	}

	@Test
	void shouldPreferHighestVersion() {
		agentManager.register(agent("tester-a", "1.0.0", List.of("testing"), true, T0));
		agentManager.register(agent("tester-c", "1.9.0", List.of("testing"), true, T1));
		agentManager.register(agent("tester-b", "1.10.0", List.of("testing"), true, T2));

		assertEquals("tester-b", resolver.resolveAgent("testing").orElseThrow().getName());
	}

	@Test
	void shouldPreferMostRecentlyUpdatedOnVersionTie() {
		agentManager.register(agent("coder-a", "1.0.0", List.of("coding"), true, T0));
		agentManager.register(agent("coder-b", "1.0.0", List.of("coding"), true, T1));

		assertEquals("coder-b", resolver.resolveAgent("coding").orElseThrow().getName());
	}

	@Test
	void shouldResolveByCapabilityReturnBestFirst() {
		agentManager.register(agent("browser-old", "0.9.0", List.of("browser"), true, T0));
		agentManager.register(agent("browser-new", "1.2.0", List.of("browser"), true, T1));

		List<AgentDefinition> agents = resolver.resolveByCapability("browser");

		assertEquals(List.of("browser-new", "browser-old"),
			agents.stream().map(AgentDefinition::getName).toList());
	}

	@Test
	void shouldListPresetCapabilities() {
		assertEquals(List.of("planning", "coding", "browser", "testing", "analysis"),
			resolver.listCapabilities().stream().map(AgentCapability::getCapabilityId).toList());
	}

	private AgentDefinition agent(String name, String version, List<String> capabilities,
			boolean enabled, Instant updatedAt) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		definition.setVersion(version);
		definition.setCapabilities(capabilities);
		definition.setEnabled(enabled);
		definition.setUpdatedAt(updatedAt);
		return definition;
	}
}
