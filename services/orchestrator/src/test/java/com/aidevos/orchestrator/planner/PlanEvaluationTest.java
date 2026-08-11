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
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan evaluation and optimization verification: the score reacts to memory,
 * risk and cost, stays clamped to [0, 100] and is stored and audited; the
 * optimizer applies the optimization recommendations (repair agent, agent
 * replacement, tools) to the plan only.
 */
class PlanEvaluationTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final OptimizationService optimizationService = mock(OptimizationService.class);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final PlanningService service = new PlanningService(taskCenterService,
		memoryService, optimizationService, agentOptimizationService,
		mock(AgentSelector.class), new ExecutionGraphBuilder(), auditService);

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(new TaskRecord("task-1", "Implement login",
				"Append a line to a.txt", "project-x")));
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of());
		when(memoryService.findSimilarTasks(anyString(), anyString(), anyInt())).thenReturn(List.of());
		when(memoryService.findSolutions(anyString(), anyString(), anyInt())).thenReturn(List.of());
	}

	@Test
	void memoryRaisesScoreAndRiskPenaltyLowersIt() {
		Plan empty = service.createPlan(orchestrationTask());
		Plan evaluatedEmpty = service.evaluatePlan(empty);

		when(memoryService.findSimilarTasks(anyString(), anyString(), anyInt()))
			.thenReturn(List.of(match("m1", MemoryType.HISTORY_TASK, "similar task")));
		when(memoryService.findSolutions(anyString(), anyString(), anyInt()))
			.thenReturn(List.of(match("m2", MemoryType.BUG_RECORD, "known fix")));
		Plan withMemory = service.createPlan(orchestrationTask());
		Plan evaluatedWithMemory = service.evaluatePlan(withMemory);

		assertTrue(evaluatedWithMemory.score() > evaluatedEmpty.score(),
			"memory should raise the score");
		assertTrue(evaluatedEmpty.riskLevel() == RiskLevel.MEDIUM);
		assertTrue(evaluatedWithMemory.riskLevel() == RiskLevel.LOW);
	}

	@Test
	void warningsRaiseRiskAndLowerScore() {
		when(optimizationService.getRecommendations("task-1")).thenReturn(List.of(
			record(OptimizationType.FAILURE_PATTERN, "failed node detected")));
		Plan plan = service.createPlan(orchestrationTask());

		assertEquals(RiskLevel.HIGH, plan.riskLevel());

		Plan evaluated = service.evaluatePlan(plan);
		when(optimizationService.getRecommendations("task-1")).thenReturn(List.of());
		Plan clean = service.evaluatePlan(service.createPlan(orchestrationTask()));

		assertTrue(evaluated.score() < clean.score(), "warnings should lower the score");
	}

	@Test
	void scoreIsStoredAndAudited() {
		Plan plan = service.createPlan(orchestrationTask());

		Plan evaluated = service.evaluatePlan(plan);

		assertTrue(evaluated.score() > 0 && evaluated.score() <= 100);
		assertEquals(evaluated.score(), service.getPlan(plan.planId()).orElseThrow().score());
		EventRecord event = events().stream()
			.filter(item -> item.type() == EventType.PLAN_EVALUATED)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing PLAN_EVALUATED"));
		assertEquals(evaluated.score(), event.metadata().get("score"));
		assertEquals("task-1", event.taskId());
	}

	@Test
	void failurePatternAddsRepairAgentAndRaisesRisk() {
		when(optimizationService.getRecommendations("task-1")).thenReturn(List.of(
			record(OptimizationType.FAILURE_PATTERN, "failed verification detected")));
		Plan plan = service.createPlan(orchestrationTask());

		Plan optimized = service.optimizePlan(plan);

		assertTrue(optimized.steps().stream()
			.anyMatch(step -> step.agentType() == AgentType.REPAIR_AGENT),
			"repair agent should join the flow");
		assertTrue(optimized.riskLevel().ordinal() >= RiskLevel.HIGH.ordinal());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.PLAN_OPTIMIZED));
	}

	@Test
	void agentSelectionReplacesPrimaryAgent() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of(
			new AgentScore("OPENCLAW", 10, 95, 800, 5, 80, 90)));
		when(optimizationService.getRecommendations("task-1")).thenReturn(List.of(
			record(OptimizationType.AGENT_SELECTION, "prefer OPENCLAW")));
		Plan plan = service.createPlan(orchestrationTask());

		Plan optimized = service.optimizePlan(plan);

		assertTrue(optimized.steps().stream()
			.anyMatch(step -> step.agentType() == AgentType.OPENCLAW));
		assertTrue(optimized.steps().stream()
			.noneMatch(step -> step.agentType() == AgentType.CODEX),
			"primary codex step should be replaced");
		assertEquals(List.of("HERMES", "OPENCLAW", "TEST_AGENT"), optimized.selectedAgents());
	}

	@Test
	void toolUsageRecommendationAttachesTools() {
		when(optimizationService.getRecommendations("task-1")).thenReturn(List.of(
			record(OptimizationType.TOOL_USAGE, "enable MCP tools")));
		Plan plan = service.createPlan(orchestrationTask());

		Plan optimized = service.optimizePlan(plan);

		assertTrue(optimized.steps().stream()
			.allMatch(step -> step.tools() != null && !step.tools().isEmpty()),
			"every step should carry tools after the tool optimization");
	}

	private OrchestrationTask orchestrationTask() {
		return new OrchestrationTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL,
			List.of());
	}

	private MemoryMatch match(String id, MemoryType type, String summary) {
		return new MemoryMatch(id, type, 0.9, summary, "solution", Map.of());
	}

	private OptimizationRecord record(OptimizationType type, String recommendation) {
		return new OptimizationRecord("optimization-1", "task-1", null, type,
			recommendation, 0.8, Instant.now());
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
