package com.aidevos.orchestrator.agent;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentSelectorTest {

	private AgentDefinition planner;
	private AgentDefinition executor;
	private AgentSelector agentSelector;

	@BeforeEach
	void setUp() {
		AgentManager agentManager = new AgentManager();
		planner = createAgent("planner", List.of("analysis"));
		executor = createAgent("executor", List.of("coding", "git"));
		agentManager.register(planner);
		agentManager.register(executor);
		agentSelector = new AgentSelector(agentManager);
	}

	@Test
	void shouldSelectExecutorForCodingCapability() {
		assertSame(executor, agentSelector.select(List.of("coding")));
	}

	@Test
	void shouldSelectPlannerForAnalysisCapability() {
		assertSame(planner, agentSelector.select(List.of("analysis")));
	}

	@Test
	void shouldReturnNullForUnknownCapability() {
		assertNull(agentSelector.select(List.of("unknown")));
	}

	@Test
	void shouldReturnNullForEmptyCapabilities() {
		assertNull(agentSelector.select(List.of()));
	}

	@Test
	void shouldReturnNullForNullCapabilities() {
		assertNull(agentSelector.select(null));
	}

	private AgentDefinition createAgent(String name, List<String> capabilities) {
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName(name);
		agentDefinition.setCapabilities(capabilities);
		return agentDefinition;
	}
}
