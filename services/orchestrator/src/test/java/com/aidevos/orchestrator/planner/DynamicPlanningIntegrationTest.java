package com.aidevos.orchestrator.planner;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.collaboration.InMemoryAgentMessageRepository;
import com.aidevos.orchestrator.collaboration.InMemoryAgentTeamRepository;
import com.aidevos.orchestrator.human.InMemoryHumanApprovalRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.memory.search.MemoryQuery;
import com.aidevos.orchestrator.memory.search.MemorySearchService;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.InMemoryOptimizationRepository;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dynamic planning integration verification: the full create -> analyze ->
 * evaluate -> optimize -> generateGraph flow with the real memory service,
 * optimization service and graph builder. A FAILURE_PATTERN recommendation
 * learned from a previous run makes the optimizer add a repair agent to the
 * plan and the generated graph carries the analyzed memory context.
 */
class DynamicPlanningIntegrationTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
	private final MemorySearchService memorySearch = mock(MemorySearchService.class);
	private final MemoryService memoryService = new MemoryService(memoryRepository, memorySearch);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final InMemoryOptimizationRepository optimizationRepository =
		new InMemoryOptimizationRepository();
	private final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	private final AgentMetricsService metricsService = mock(AgentMetricsService.class);
	private final UsageService usageService = mock(UsageService.class);
	private final AgentOptimizationService agentOptimizationService =
		new AgentOptimizationService(metricsService, traceService, usageService,
			new InMemoryAgentTeamRepository(), new InMemoryAgentMessageRepository(),
			new InMemoryHumanApprovalRepository(), auditService);
	private final OptimizationService optimizationService = new OptimizationService(
		optimizationRepository, auditService, memoryService, taskCenterService,
		agentOptimizationService);
	private final PlanningService planningService = new PlanningService(taskCenterService,
		memoryService, optimizationService, agentOptimizationService,
		new AgentSelector(mock(AgentManager.class)), new ExecutionGraphBuilder(), auditService);

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(new TaskRecord("task-1", "Implement login",
				"Append a line to a.txt", "project-x")));
		MemoryMatch similar = new MemoryMatch("m1", MemoryType.HISTORY_TASK, 0.9,
			"similar login task", "solution-a", Map.of());
		MemoryMatch bug = new MemoryMatch("m2", MemoryType.BUG_RECORD, 0.8,
			"known login bug", null, Map.of("resolved", false));
		when(memorySearch.search(any(MemoryQuery.class))).thenAnswer(invocation -> {
			MemoryQuery query = invocation.getArgument(0);
			return query.taskType() == null ? List.of(similar, bug) : List.of(similar);
		});
	}

	@Test
	void fullPlanningFlowGeneratesGraphFromOptimizedPlan() {
		optimizationService.recordOptimization("task-1", null,
			OptimizationType.FAILURE_PATTERN, "failed verification detected; route a "
				+ "repair agent", 0.8);
		OrchestrationTask orchestrated = new OrchestrationTask("task-1", "CODE_GENERATION",
			TaskPriority.CRITICAL, List.of("codex"));

		Plan plan = planningService.createPlan(orchestrated);
		Plan evaluated = planningService.evaluatePlan(plan);
		Plan optimized = planningService.optimizePlan(evaluated);
		ExecutionGraph graph = planningService.generateGraph(optimized);

		assertTrue(optimized.steps().stream()
			.anyMatch(step -> step.agentType() == AgentType.REPAIR_AGENT),
			"failure pattern should add a repair agent to the plan");
		assertTrue(optimized.riskLevel().ordinal() >= RiskLevel.HIGH.ordinal());
		assertEquals(List.of("HERMES", "CODEX", "REPAIR_AGENT", "TEST_AGENT"),
			optimized.selectedAgents());
		assertEquals(optimized.steps().size(), graph.getNodes().size());
		assertEquals(List.of("step-1", "step-2", "step-repair", "step-3"),
			graph.getTopologicalOrder());
		assertNotNull(graph.getMemoryContext());
		assertEquals(1, graph.getMemoryContext().getSimilarTasks().size());
		assertEquals(1, graph.getMemoryContext().getWarnings().size());
		assertEvent(EventType.PLAN_CREATED);
		assertEvent(EventType.PLAN_EVALUATED);
		assertEvent(EventType.PLAN_OPTIMIZED);
		assertEvent(EventType.GRAPH_GENERATED);
		EventRecord graphEvent = lastEvent(EventType.GRAPH_GENERATED);
		assertEquals(graph.getGraphId(), graphEvent.metadata().get("graphId"));
		assertEquals("task-1", graphEvent.taskId());
	}

	@Test
	void generatedGraphRunsThroughExecutors() {
		OrchestrationTask orchestrated = new OrchestrationTask("task-1", "CODE_GENERATION",
			TaskPriority.NORMAL, List.of());
		Plan plan = planningService.createPlan(orchestrated);
		Plan evaluated = planningService.evaluatePlan(plan);
		ExecutionGraph graph = planningService.generateGraph(
			planningService.optimizePlan(evaluated));

		assertEquals(3, graph.getNodes().size());
		assertEquals(AgentType.HERMES, graph.getNodes().get(0).getAgentType());
		assertEquals(AgentType.TEST_AGENT, graph.getNodes().get(2).getAgentType());
	}

	@Test
	void planIsRetrievableByTaskId() {
		OrchestrationTask orchestrated = new OrchestrationTask("task-1", "CODE_GENERATION",
			TaskPriority.NORMAL, List.of());
		Plan plan = planningService.createPlan(orchestrated);

		assertEquals(plan.planId(), planningService.getPlanByTaskId("task-1")
			.orElseThrow().planId());
		assertTrue(planningService.listPlans().stream()
			.anyMatch(item -> item.planId().equals(plan.planId())));
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
