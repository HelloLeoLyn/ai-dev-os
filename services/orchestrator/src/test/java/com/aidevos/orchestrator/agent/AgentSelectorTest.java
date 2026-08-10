package com.aidevos.orchestrator.agent;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	// --- Orchestration selection (agent registry) ---

	@Test
	void shouldMapCodeTaskToCodex() {
		AgentSelector selector = new AgentSelector(new AgentManager(),
			new InMemoryAgentRegistry());
		assertEquals(AgentType.CODEX, selector.selectType("CODE_TASK"));
		assertEquals(AgentType.CODEX, selector.selectType(TaskType.CODE_GENERATION));
		assertEquals("codex", selector.selectAgent("CODE_TASK").getAgentId());
	}

	@Test
	void shouldMapBrowserTaskToOpenClaw() {
		AgentSelector selector = new AgentSelector(new AgentManager(),
			new InMemoryAgentRegistry());
		assertEquals(AgentType.OPENCLAW, selector.selectType("BROWSER_TASK"));
		assertEquals(AgentType.OPENCLAW, selector.selectType(TaskType.BROWSER_TEST));
		assertEquals("openclaw", selector.selectAgent("BROWSER_TASK").getAgentId());
	}

	@Test
	void shouldMapTestTaskToTestAgent() {
		AgentSelector selector = new AgentSelector(new AgentManager(),
			new InMemoryAgentRegistry());
		assertEquals(AgentType.TEST_AGENT, selector.selectType("TEST_TASK"));
		assertEquals(AgentType.TEST_AGENT, selector.selectType(TaskType.TEST_VERIFY));
		assertEquals("test-agent", selector.selectAgent("TEST_TASK").getAgentId());
	}

	@Test
	void shouldMapRepairTaskToRepairAgent() {
		AgentSelector selector = new AgentSelector(new AgentManager(),
			new InMemoryAgentRegistry());
		assertEquals(AgentType.REPAIR_AGENT, selector.selectType("REPAIR_TASK"));
		assertEquals("repair-agent", selector.selectAgent("REPAIR_TASK").getAgentId());
	}

	@Test
	void shouldFallBackToHermesForUnknownCategory() {
		AgentSelector selector = new AgentSelector(new AgentManager(),
			new InMemoryAgentRegistry());
		assertEquals(AgentType.HERMES, selector.selectType("UNKNOWN"));
	}
}
