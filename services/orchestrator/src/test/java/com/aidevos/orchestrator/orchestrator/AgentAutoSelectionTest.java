package com.aidevos.orchestrator.orchestrator;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent auto-selection verification: the base flow by task category, the
 * highest-score replacement rule, the memory-warning repair insertion and
 * the low historical success-rate rule, plus the AGENT_AUTO_SELECTED audit
 * trail.
 */
class AgentAutoSelectionTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final AgentAutoSelectionService service = new AgentAutoSelectionService(
		new AgentSelector(mock(AgentManager.class)), agentOptimizationService, auditService);

	@Test
	void baseFlowFollowsTaskCategory() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of());

		assertEquals(List.of(AgentType.HERMES, AgentType.CODEX, AgentType.TEST_AGENT),
			service.selectAgents("task-1", "CODE_GENERATION", new MemoryContext()));
		assertEquals(List.of(AgentType.HERMES, AgentType.OPENCLAW, AgentType.TEST_AGENT),
			service.selectAgents("task-1", "BROWSER_TEST", new MemoryContext()));
		assertEquals(List.of(AgentType.TEST_AGENT),
			service.selectAgents("task-1", "TEST_VERIFY", new MemoryContext()));
		assertEquals(List.of(AgentType.REPAIR_AGENT, AgentType.CODEX, AgentType.TEST_AGENT),
			service.selectAgents("task-1", "REPAIR_TASK", new MemoryContext()));
	}

	@Test
	void highestScoredAgentReplacesPrimaryExecutionAgent() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of(
			score("CODEX", 80.0, 10),
			score("REPAIR_AGENT", 95.0, 10),
			score("HERMES", 70.0, 10),
			score("TEST_AGENT", 60.0, 10)));

		List<AgentType> flow = service.selectAgents("task-1", "CODE_GENERATION",
			new MemoryContext());

		assertEquals(List.of(AgentType.HERMES, AgentType.REPAIR_AGENT,
			AgentType.TEST_AGENT), flow);
		var event = eventFor(EventType.AGENT_AUTO_SELECTED, "REPAIR_AGENT");
		assertEquals("highest composite agent score", event.metadata().get("reason"));
		assertEquals("task-1", event.taskId());
	}

	@Test
	void memoryWarningsInsertRepairAgentBeforeVerifier() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of());

		MemoryContext memory = new MemoryContext(List.of(), List.of(),
			List.of("known flaky tests"), List.of());

		List<AgentType> flow = service.selectAgents("task-1", "CODE_GENERATION", memory);

		assertEquals(List.of(AgentType.HERMES, AgentType.CODEX, AgentType.REPAIR_AGENT,
			AgentType.TEST_AGENT), flow);
		assertEvent(EventType.AGENT_AUTO_SELECTED);
	}

	@Test
	void lowSuccessRateOfPrimaryInsertsRepairAgent() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of(
			score("HERMES", 90.0, 10),
			score("CODEX", 40.0, 10),
			score("TEST_AGENT", 80.0, 10)));

		List<AgentType> flow = service.selectAgents("task-1", "CODE_GENERATION",
			new MemoryContext());

		assertEquals(List.of(AgentType.HERMES, AgentType.CODEX, AgentType.REPAIR_AGENT,
			AgentType.TEST_AGENT), flow);
		var event = eventFor(EventType.AGENT_AUTO_SELECTED, "REPAIR_AGENT");
		assertTrue(((String) event.metadata().get("reason"))
			.startsWith("low historical success rate"));
	}

	@Test
	void categoryMappingCoversTaskTypeAliases() {
		assertEquals("CODE_TASK", service.categoryOf("CODE_GENERATION"));
		assertEquals("BROWSER_TASK", service.categoryOf("browser_test"));
		assertEquals("TEST_TASK", service.categoryOf("TEST_VERIFY"));
		assertEquals("REPAIR_TASK", service.categoryOf("REPAIR"));
		assertEquals("CODE_TASK", service.categoryOf(null));
	}

	private AgentScore score(String agent, double successRate, int executions) {
		return new AgentScore(agent, executions, successRate, 1000,
			100.0 - successRate, 0.0, 0.0);
	}

	private EventRecord eventFor(EventType type, String agentType) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type
				&& agentType.equals(event.metadata().get("agentType")))
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing audit event " + type
				+ " for " + agentType));
	}

	private EventRecord lastEvent(EventType type) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing audit event " + type));
	}

	private void assertEvent(EventType type) {
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == type), "missing audit event " + type);
	}
}
