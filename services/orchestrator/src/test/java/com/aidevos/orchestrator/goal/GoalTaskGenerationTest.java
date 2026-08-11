package com.aidevos.orchestrator.goal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.OrchestratorService;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanStep;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.planner.RiskLevel;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Goal task generation verification: the goal is planned through the dynamic
 * planner and the plan steps are turned into tasks submitted to the
 * orchestrator task pool (GOAL_TASK_CREATED per task). Without a planner the
 * default template still produces the four standard tasks.
 */
class GoalTaskGenerationTest {

	private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();
	private final InMemoryGoalMilestoneRepository milestoneRepository =
		new InMemoryGoalMilestoneRepository();
	private final InMemoryGoalTaskRepository goalTaskRepository =
		new InMemoryGoalTaskRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final OptimizationService optimizationService = mock(OptimizationService.class);
	private final PlanningService planningService = mock(PlanningService.class);
	private final OrchestratorService orchestratorService = mock(OrchestratorService.class);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final GoalManagementService service = new GoalManagementService(goalRepository,
		milestoneRepository, goalTaskRepository, planningService, orchestratorService,
		taskCenterService, memoryService, optimizationService,
		mock(AgentRuntimeService.class), auditService);

	@BeforeEach
	void setUp() {
		when(memoryService.findSimilarTasks(anyString(), anyString(), anyInt()))
			.thenReturn(List.of());
		when(memoryService.findSolutions(anyString(), anyString(), anyInt()))
			.thenReturn(List.of());
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
	}

	@Test
	void planDrivenGenerationSubmitsOneTaskPerStep() {
		Plan plan = planWithSteps();
		when(planningService.createPlan(any(OrchestrationTask.class))).thenReturn(plan);
		when(planningService.evaluatePlan(plan)).thenReturn(plan);
		when(planningService.optimizePlan(plan)).thenReturn(plan);
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.HIGH);
		service.decomposeGoal(goal.getGoalId());

		List<GoalTask> tasks = service.generateTasks(goal.getGoalId());

		assertEquals(4, tasks.size());
		verify(orchestratorService, times(4))
			.submitTask(anyString(), anyString(), any(TaskPriority.class), anyList());
		verify(taskCenterService, times(4)).registerTask(any());
		assertEquals(4, goalTaskRepository.listByGoal(goal.getGoalId()).size());
		String prefix = goal.getGoalId() + "-task-";
		assertTrue(service.milestoneIdForTask(prefix + "1").isPresent());
		assertEquals(goal.getGoalId() + "-milestone-plan",
			service.milestoneIdForTask(prefix + "1").orElseThrow());
		assertEquals(goal.getGoalId() + "-milestone-implement",
			service.milestoneIdForTask(prefix + "2").orElseThrow());
		assertEquals(goal.getGoalId() + "-milestone-verify",
			service.milestoneIdForTask(prefix + "4").orElseThrow());
	}

	@Test
	void planDrivenGenerationAuditsTaskCreatedPerTask() {
		Plan plan = planWithSteps();
		when(planningService.createPlan(any(OrchestrationTask.class))).thenReturn(plan);
		when(planningService.evaluatePlan(plan)).thenReturn(plan);
		when(planningService.optimizePlan(plan)).thenReturn(plan);
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		service.generateTasks(goal.getGoalId());

		long created = events().stream()
			.filter(event -> event.type() == EventType.GOAL_TASK_CREATED)
			.count();
		assertEquals(4, created);
		EventRecord first = events().stream()
			.filter(event -> event.type() == EventType.GOAL_TASK_CREATED)
			.findFirst().orElseThrow();
		assertEquals(goal.getGoalId(), first.aggregateId());
		assertEquals(goal.getGoalId() + "-task-1", first.metadata().get("taskId"));
		assertTrue(first.metadata().containsKey("milestoneId"));
	}

	@Test
	void fallbackTemplateWithoutPlannerProducesFourTasks() {
		when(planningService.createPlan(any(OrchestrationTask.class))).thenReturn(null);
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		List<GoalTask> tasks = service.generateTasks(goal.getGoalId());

		assertEquals(4, tasks.size());
		assertEquals(goal.getGoalId() + "-task-1", tasks.get(0).getTaskId());
		assertEquals(goal.getGoalId() + "-task-4", tasks.get(3).getTaskId());
		assertEquals("SUB_TASK", tasks.get(0).getRelationType());
		verify(orchestratorService, times(4))
			.submitTask(anyString(), anyString(), any(TaskPriority.class), anyList());
	}

	@Test
	void generateTasksIsIdempotent() {
		when(planningService.createPlan(any(OrchestrationTask.class))).thenReturn(null);
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		List<GoalTask> first = service.generateTasks(goal.getGoalId());
		List<GoalTask> second = service.generateTasks(goal.getGoalId());

		assertEquals(first.size(), second.size());
		assertEquals(first.get(0).getTaskId(), second.get(0).getTaskId());
		verify(orchestratorService, times(4))
			.submitTask(anyString(), anyString(), any(TaskPriority.class), anyList());
	}

	@Test
	void generationRegistersTaskRecordsWithTaskCenter() {
		when(planningService.createPlan(any(OrchestrationTask.class))).thenReturn(null);
		when(taskCenterService.getTask(anyString())).thenReturn(Optional.empty());
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		service.generateTasks(goal.getGoalId());

		verify(taskCenterService, times(4)).registerTask(any());
	}

	private Plan planWithSteps() {
		List<PlanStep> steps = List.of(
			new PlanStep("step-1", "规划实现方案", AgentType.HERMES, List.of("memory"),
				List.of()),
			new PlanStep("step-2", "实现代码", AgentType.CODEX, List.of("git", "filesystem"),
				List.of("step-1")),
			new PlanStep("step-3", "运行测试", AgentType.TEST_AGENT, List.of("terminal"),
				List.of("step-2")));
		return new Plan("plan-1", "goal-under-test", "开发用户管理模块", steps,
			List.of("HERMES", "CODEX", "TEST_AGENT"), 100.0, RiskLevel.LOW, 85.0,
			Instant.now());
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
