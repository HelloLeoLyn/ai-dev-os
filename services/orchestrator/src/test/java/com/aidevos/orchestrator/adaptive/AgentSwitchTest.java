package com.aidevos.orchestrator.adaptive;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionNode;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent switching verification: after repeated failures the decision targets
 * the best-scored other agent and the applied decision replaces the failed
 * node's agent in a new graph while preserving the completed nodes, so the
 * retry continues from the failed node instead of re-running the whole task.
 */
class AgentSwitchTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final AdaptiveExecutionService service = new AdaptiveExecutionService(
		auditService, mock(TaskCenterService.class), agentOptimizationService,
		mock(OptimizationService.class), mock(MemoryService.class),
		mock(PlanningService.class), mock(AgentRuntimeService.class));

	@BeforeEach
	void setUp() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of(
			new AgentScore("HERMES", 10, 80, 900, 20, 50, 60),
			new AgentScore("CODEX", 10, 40, 1500, 60, 50, 50),
			new AgentScore("OPENCLAW", 10, 90, 800, 10, 50, 80),
			new AgentScore("TEST_AGENT", 10, 70, 1500, 30, 50, 50)));
	}

	@Test
	void decisionTargetsBestOtherAgent() {
		AdaptationDecision decision = service.decide("task-1", "session-1",
			"CODEX_IMPLEMENTATION", "CODEX", "compile error", 2);

		assertEquals(AdaptationAction.SWITCH_AGENT, decision.getAction());
		assertEquals("OPENCLAW", decision.getTargetAgent());
	}

	@Test
	void applyDecisionReplacesFailedNodeAgentAndKeepsCompletedNodes() {
		ExecutionGraph graph = new ExecutionGraphBuilder().build("task-1", "CODE_TASK");
		graph.getNode("HERMES_PLANNING").markCompleted("ok");
		graph.getNode("CODEX_IMPLEMENTATION").markFailed("compile error");
		AdaptationDecision decision = new AdaptationDecision("decision-1", "task-1",
			"CODEX_IMPLEMENTATION", "switch agent", AdaptationAction.SWITCH_AGENT, 0.7,
			"OPENCLAW", null);

		ExecutionGraph switched = service.applyDecision(decision, graph, null);

		assertNotSame(graph, switched);
		ExecutionNode node = switched.getNode("CODEX_IMPLEMENTATION");
		assertEquals(AgentType.OPENCLAW, node.getAgentType());
		assertEquals(ExecutionNodeStatus.PENDING, node.getStatus());
		assertEquals(ExecutionNodeStatus.COMPLETED,
			switched.getNode("HERMES_PLANNING").getStatus());
		assertEquals(List.of("HERMES_PLANNING", "CODEX_IMPLEMENTATION", "TEST_AGENT_VERIFY"),
			switched.getTopologicalOrder());
	}

	@Test
	void switchWithoutTargetFallsBackToInPlaceRetry() {
		ExecutionGraph graph = new ExecutionGraphBuilder().build("task-1", "CODE_TASK");
		graph.getNode("CODEX_IMPLEMENTATION").markFailed("compile error");
		AdaptationDecision decision = new AdaptationDecision("decision-1", "task-1",
			"CODEX_IMPLEMENTATION", "no target", AdaptationAction.SWITCH_AGENT, 0.5,
			null, null);

		ExecutionGraph result = service.applyDecision(decision, graph, null);

		assertTrue(result == graph);
		assertEquals(ExecutionNodeStatus.PENDING,
			graph.getNode("CODEX_IMPLEMENTATION").getStatus());
	}
}
