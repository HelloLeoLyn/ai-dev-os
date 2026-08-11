package com.aidevos.orchestrator.adaptive;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionNode;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanStep;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Graph replanning verification: the dynamic planner re-plans a task from its
 * current execution steps into a fresh plan + graph and the adaptive service
 * applies a REPLAN decision with the GRAPH_REPLANNED audit trail.
 */
class GraphReplanTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final OptimizationService optimizationService = mock(OptimizationService.class);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	private final PlanningService planningService = new PlanningService(taskCenterService,
		memoryService, optimizationService, agentOptimizationService,
		new AgentSelector(mock(com.aidevos.orchestrator.manager.AgentManager.class)),
		graphBuilder, auditService);
	private final AdaptiveExecutionService adaptiveService = new AdaptiveExecutionService(
		auditService, taskCenterService, agentOptimizationService, optimizationService,
		memoryService, planningService, mock(AgentRuntimeService.class));

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(new TaskRecord("task-1", "Implement login",
				"Append a line to a.txt", "project-x")));
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of());
		when(memoryService.findSimilarTasks(anyString(), anyString(), anyInt()))
			.thenReturn(List.of());
		when(memoryService.findSolutions(anyString(), anyString(), anyInt()))
			.thenReturn(List.of());
	}

	@Test
	void plannerReplansIntoNewPlanAndGraph() {
		ExecutionGraph original = graphBuilder.build("task-1", "CODE_TASK");
		List<PlanStep> steps = List.of(
			new PlanStep("step-1", "Plan the implementation", AgentType.HERMES,
				List.of(), List.of()),
			new PlanStep("step-2", "Implement the changes", AgentType.CODEX,
				List.of(), List.of("step-1")),
			new PlanStep("step-3", "Verify with tests", AgentType.TEST_AGENT,
				List.of(), List.of("step-2")));

		Plan plan = planningService.replan("task-1", steps);
		ExecutionGraph replanned = planningService.generateGraph(plan);

		assertNotEquals(original.getGraphId(), replanned.getGraphId());
		assertEquals(3, replanned.getNodes().size());
		assertEquals(List.of("step-1", "step-2", "step-3"),
			replanned.getTopologicalOrder());
		assertEvent(EventType.PLAN_CREATED);
		assertEvent(EventType.GRAPH_GENERATED);
	}

	@Test
	void applyReplanDecisionGeneratesNewGraphAndAudits() {
		ExecutionGraph original = graphBuilder.build("task-1", "CODE_TASK");
		AdaptationDecision decision = new AdaptationDecision("decision-1", "task-1",
			"CODEX_IMPLEMENTATION", "repeated failures", AdaptationAction.REPLAN, 0.8,
			null, null);

		ExecutionGraph replanned = adaptiveService.applyDecision(decision, original, null);

		assertNotEquals(original.getGraphId(), replanned.getGraphId());
		assertEquals("task-1", replanned.getTaskId());
		assertEquals(3, replanned.getNodes().size());
		assertEvent(EventType.GRAPH_REPLANNED);
		assertEquals(1, adaptiveService.replansForTask("task-1").size());
		EventRecord event = lastEvent(EventType.GRAPH_REPLANNED);
		assertEquals(replanned.getGraphId(), event.metadata().get("graphId"));
		assertEquals("task-1", event.taskId());
	}

	@Test
	void replayedGraphRunsTopologically() {
		ExecutionGraph original = graphBuilder.build("task-1", "CODE_TASK");
		AdaptationDecision decision = new AdaptationDecision("decision-1", "task-1",
			"CODEX_IMPLEMENTATION", "repeated failures", AdaptationAction.REPLAN, 0.8,
			null, null);

		ExecutionGraph replanned = adaptiveService.applyDecision(decision, original, null);

		ExecutionNode first = replanned.getNode(replanned.getTopologicalOrder().get(0));
		assertEquals(AgentType.HERMES, first.getAgentType());
		assertTrue(replanned.getNodes().stream()
			.anyMatch(node -> node.getAgentType() == AgentType.TEST_AGENT));
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	private EventRecord lastEvent(EventType type) {
		return events().stream()
			.filter(event -> event.type() == type)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing audit event " + type));
	}

	private void assertEvent(EventType type) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type),
			"missing audit event " + type);
	}
}
