package com.aidevos.orchestrator.planner;

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
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dynamic planning unit verification: plan creation (goal / steps / agents /
 * cost / risk), task analysis against memory and optimization, plan lookup by
 * plan id and task id, and the conversion of a plan into an execution graph.
 */
class PlanningServiceTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final OptimizationService optimizationService = mock(OptimizationService.class);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final AgentSelector agentSelector = mock(AgentSelector.class);
	private final ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
	private final PlanningService service = new PlanningService(taskCenterService,
		memoryService, optimizationService, agentOptimizationService, agentSelector,
		graphBuilder, auditService);

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(new TaskRecord("task-1", "Implement login",
				"Append a line to a.txt", "project-x")));
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of());
		when(agentSelector.selectType(anyString())).thenReturn(AgentType.CODEX);
	}

	@Test
	void createPlanBuildsCompletePlan() {
		Plan plan = service.createPlan(orchestrationTask("CODE_GENERATION"));

		assertNotNull(plan.planId());
		assertEquals("task-1", plan.taskId());
		assertEquals("Append a line to a.txt", plan.goal());
		assertEquals(3, plan.steps().size());
		assertEquals(List.of(AgentType.HERMES, AgentType.CODEX, AgentType.TEST_AGENT),
			plan.steps().stream().map(PlanStep::agentType).toList());
		assertEquals(List.of("HERMES", "CODEX", "TEST_AGENT"), plan.selectedAgents());
		assertTrue(plan.estimatedCost() > 0);
		assertNotNull(plan.riskLevel());
		assertEquals(0.0, plan.score());
		assertNotNull(plan.createdAt());
		assertTrue(service.getPlan(plan.planId()).isPresent());
		assertEquals(plan.planId(), service.getPlanByTaskId("task-1").orElseThrow().planId());
	}

	@Test
	void createPlanDerivesStepsFromTaskCategory() {
		Plan browser = service.createPlan(orchestrationTask("BROWSER_TEST"));
		Plan repair = service.createPlan(orchestrationTask("REPAIR_TASK"));

		assertTrue(browser.steps().stream().anyMatch(step -> step.agentType() == AgentType.OPENCLAW));
		assertEquals(3, browser.steps().size());
		assertTrue(repair.steps().stream().anyMatch(step -> step.agentType() == AgentType.REPAIR_AGENT));
		assertEquals(4, repair.steps().size());
	}

	@Test
	void createPlanAuditsPlanCreated() {
		Plan plan = service.createPlan(orchestrationTask("CODE_GENERATION"));

		EventRecord event = events().stream()
			.filter(item -> item.type() == EventType.PLAN_CREATED)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing PLAN_CREATED"));
		assertEquals(plan.planId(), event.aggregateId());
		assertEquals("task-1", event.taskId());
		assertEquals("CODE_GENERATION", event.metadata().get("taskType"));
		assertEquals(3, event.metadata().get("stepCount"));
	}

	@Test
	void analyzeTaskBuildsMemoryContext() {
		when(memoryService.findSimilarTasks(anyString(), anyString(), anyInt()))
			.thenReturn(List.of(new MemoryMatch("m1", com.aidevos.orchestrator.memory.MemoryType.HISTORY_TASK,
				0.9, "similar login task", "solution-a", Map.of())));
		when(memoryService.findSolutions(anyString(), anyString(), anyInt()))
			.thenReturn(List.of(new MemoryMatch("m2", com.aidevos.orchestrator.memory.MemoryType.BUG_RECORD,
				0.8, "unresolved bug", "solution-a", Map.of("resolved", false))));

		MemoryContext context = service.analyzeTask(orchestrationTask("CODE_GENERATION"));

		assertEquals(1, context.getSimilarTasks().size());
		assertEquals(1, context.getSolutions().size());
		assertEquals("solution-a", context.getSolutions().get(0).solution());
	}

	@Test
	void generateGraphBuildsGraphFromPlanSteps() {
		Plan plan = service.createPlan(orchestrationTask("CODE_GENERATION"));

		ExecutionGraph graph = service.generateGraph(plan);

		assertEquals("task-1", graph.getTaskId());
		assertEquals(3, graph.getNodes().size());
		assertEquals(List.of("step-1", "step-2", "step-3"),
			graph.getTopologicalOrder());
		assertEquals(AgentType.TEST_AGENT,
			graph.getNode("step-3").getAgentType());
		assertEquals(List.of("step-2"), graph.getNode("step-3").getDependencies());
		assertNotNull(graph.getMemoryContext());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.GRAPH_GENERATED));
	}

	@Test
	void createPlanRejectsMissingTask() {
		assertThrows(IllegalArgumentException.class, () -> service.createPlan(null));
	}

	private OrchestrationTask orchestrationTask(String taskType) {
		return new OrchestrationTask("task-1", taskType, TaskPriority.NORMAL, List.of());
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
