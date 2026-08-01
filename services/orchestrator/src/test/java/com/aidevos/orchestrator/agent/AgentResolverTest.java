package com.aidevos.orchestrator.agent;

import java.util.List;

import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentResolverTest {

	@Test
	void shouldResolveNamedAgentAndExecutor() {
		AgentDefinition agent = agent("browser-agent", "openclaw", "main", List.of("browser"));
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("openclaw");
		AgentResolver resolver = resolver(List.of(agent), executor);
		TaskDefinition task = task("browser-agent", List.of("browser"));

		ResolvedAgent resolved = resolver.resolve(task);

		assertSame(agent, resolved.definition());
		assertSame(executor, resolved.executor());
		assertEquals("main", resolved.definition().getExternalId());
	}

	@Test
	void shouldSelectByCapabilitiesWhenAgentNameIsAbsent() {
		AgentDefinition agent = agent("browser-agent", "mock", "main", List.of("browser"));
		AgentResolver resolver = resolver(List.of(agent), new MockAgentExecutor());

		ResolvedAgent resolved = resolver.resolve(task(null, List.of("browser")));

		assertSame(agent, resolved.definition());
	}

	@Test
	void shouldRejectCapabilityMismatchForNamedAgent() {
		AgentDefinition agent = agent("browser-agent", "mock", "main", List.of("browser"));
		AgentResolver resolver = resolver(List.of(agent), new MockAgentExecutor());

		AgentResolutionException exception = assertThrows(AgentResolutionException.class,
			() -> resolver.resolve(task("browser-agent", List.of("coding"))));

		assertEquals("Agent browser-agent does not provide required capabilities: [coding]",
			exception.getMessage());
	}

	@Test
	void shouldRejectDisabledAgent() {
		AgentDefinition agent = agent("browser-agent", "mock", "main", List.of("browser"));
		agent.setEnabled(false);
		AgentResolver resolver = resolver(List.of(agent), new MockAgentExecutor());

		AgentResolutionException exception = assertThrows(AgentResolutionException.class,
			() -> resolver.resolve(task("browser-agent", null)));

		assertEquals("Agent is disabled: browser-agent", exception.getMessage());
	}

	@Test
	void shouldRejectUnknownExecutor() {
		AgentDefinition agent = agent("browser-agent", "mcp", "remote-agent", null);
		AgentResolver resolver = resolver(List.of(agent), new MockAgentExecutor());

		AgentResolutionException exception = assertThrows(AgentResolutionException.class,
			() -> resolver.resolve(task("browser-agent", null)));

		assertEquals("Executor not found: mcp for agent: browser-agent", exception.getMessage());
	}

	private AgentResolver resolver(List<AgentDefinition> agents, AgentExecutor executor) {
		AgentManager manager = new AgentManager();
		agents.forEach(manager::register);
		ExecutorManager executorManager = new ExecutorManager(manager,
			new ExecutorRegistry(List.of(executor)));
		return new AgentResolver(manager, new AgentSelector(manager), executorManager);
	}

	private AgentDefinition agent(String name, String executor, String externalId,
			List<String> capabilities) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name);
		agent.setExecutor(executor);
		agent.setExternalId(externalId);
		agent.setCapabilities(capabilities);
		return agent;
	}

	private TaskDefinition task(String agentName, List<String> capabilities) {
		TaskDefinition task = new TaskDefinition();
		task.setAgentName(agentName);
		task.setRequiredCapabilities(capabilities);
		return task;
	}
}
