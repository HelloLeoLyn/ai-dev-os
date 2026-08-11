package com.aidevos.orchestrator.goal;

import java.util.List;
import java.util.Optional;

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
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Goal progress verification: the completion is derived from the
 * orchestration task outcomes (Progress Evaluation) and the goal moves to
 * COMPLETED or FAILED with GOAL_COMPLETED / GOAL_FAILED.
 */
class GoalProgressTest {

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
		when(planningService.createPlan(any(com.aidevos.orchestrator.orchestrator.OrchestrationTask.class)))
			.thenReturn(null);
	}

	@Test
	void completedTasksCompleteTheGoal() {
		Goal goal = preparedGoal();
		linkTasks(goal, status -> {
			status.markCompleted();
			return status;
		});

		GoalEvaluation evaluation = service.evaluateCompletion(goal.getGoalId());

		assertEquals(GoalStatus.COMPLETED, goal.getStatus());
		assertEquals(100.0, goal.getProgress());
		assertEquals(4, evaluation.completedTasks());
		assertEquals(4, evaluation.totalTasks());
		assertEquals(100.0, evaluation.progress());
		assertEquals(0, evaluation.remainingWork());
		assertEquals(0.5, evaluation.confidence());
		assertEvent(EventType.GOAL_COMPLETED);
		assertEvent(EventType.GOAL_PROGRESS_UPDATED);
	}

	@Test
	void failedTaskFailsTheGoal() {
		Goal goal = preparedGoal();
		List<GoalTask> links = service.getTasks(goal.getGoalId());
		when(orchestratorService.getTask(links.get(0).getTaskId()))
			.thenReturn(Optional.of(task(links.get(0).getTaskId(), true)));
		when(orchestratorService.getTask(links.get(1).getTaskId()))
			.thenReturn(Optional.of(task(links.get(1).getTaskId(), false)));
		when(orchestratorService.getTask(links.get(2).getTaskId()))
			.thenReturn(Optional.of(task(links.get(2).getTaskId(), true)));
		when(orchestratorService.getTask(links.get(3).getTaskId()))
			.thenReturn(Optional.of(task(links.get(3).getTaskId(), true)));

		GoalEvaluation evaluation = service.evaluateCompletion(goal.getGoalId());

		assertEquals(GoalStatus.FAILED, goal.getStatus());
		assertEquals(75.0, evaluation.progress());
		assertEquals(3, evaluation.completedTasks());
		assertEquals(4, evaluation.totalTasks());
		assertEquals(1, evaluation.remainingWork());
		assertEvent(EventType.GOAL_FAILED);
	}

	@Test
	void updateProgressRecomputesFromTaskOutcomes() {
		Goal goal = preparedGoal();
		List<GoalTask> links = service.getTasks(goal.getGoalId());
		stubOutcomes(links, true, true, true, false);

		double progress = service.updateProgress(goal.getGoalId());

		assertEquals(75.0, progress);
		assertEquals(75.0, goal.getProgress());
		assertEquals(GoalStatus.RUNNING, goal.getStatus());
		EventRecord event = lastEvent(EventType.GOAL_PROGRESS_UPDATED);
		assertEquals(goal.getGoalId(), event.aggregateId());
		assertEquals(75.0, event.metadata().get("progress"));
	}

	@Test
	void evaluationIsDerivedFromOrchestrationTasks() {
		Goal goal = preparedGoal();
		List<GoalTask> links = service.getTasks(goal.getGoalId());
		stubOutcomes(links, true, true, false, false);

		GoalEvaluation evaluation = service.evaluateCompletion(goal.getGoalId());

		assertEquals(50.0, evaluation.progress());
		assertEquals(2, evaluation.completedTasks());
		assertEquals(4, evaluation.totalTasks());
		assertEquals(2, evaluation.remainingWork());
		assertEquals(0.4, evaluation.confidence());
	}

	private Goal preparedGoal() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);
		service.analyzeGoal(goal.getGoalId());
		service.decomposeGoal(goal.getGoalId());
		service.generateTasks(goal.getGoalId());
		return goal;
	}

	private void linkTasks(Goal goal,
			java.util.function.UnaryOperator<OrchestrationTask> outcome) {
		for (GoalTask link : service.getTasks(goal.getGoalId())) {
			OrchestrationTask orchestrationTask = new OrchestrationTask(link.getTaskId(),
				"CODE_GENERATION", TaskPriority.NORMAL, List.of());
			when(orchestratorService.getTask(link.getTaskId()))
				.thenReturn(Optional.of(outcome.apply(orchestrationTask)));
		}
	}

	/** Stubs every generated task; completed=true marks COMPLETED, false leaves
	 * the task QUEUED. */
	private void stubOutcomes(List<GoalTask> links, boolean... completed) {
		for (int i = 0; i < links.size(); i++) {
			OrchestrationTask orchestrationTask = new OrchestrationTask(links.get(i).getTaskId(),
				"CODE_GENERATION", TaskPriority.NORMAL, List.of());
			if (i < completed.length && completed[i]) {
				orchestrationTask.markCompleted();
			}
			when(orchestratorService.getTask(links.get(i).getTaskId()))
				.thenReturn(Optional.of(orchestrationTask));
		}
	}

	private OrchestrationTask task(String taskId, boolean completed) {
		OrchestrationTask task = new OrchestrationTask(taskId, "CODE_GENERATION",
			TaskPriority.NORMAL, List.of());
		if (completed) {
			task.markCompleted();
		}
		else {
			task.markFailed("compile error");
		}
		return task;
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
