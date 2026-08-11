package com.aidevos.orchestrator.goal;

import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestrator.OrchestratorService;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Goal decomposition verification: milestones are created idempotently, the
 * goal moves CREATED -> PLANNING -> RUNNING and the GOAL_DECOMPOSED audit
 * event carries the milestone count.
 */
class GoalDecompositionTest {

	private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();
	private final InMemoryGoalMilestoneRepository milestoneRepository =
		new InMemoryGoalMilestoneRepository();
	private final InMemoryGoalTaskRepository goalTaskRepository =
		new InMemoryGoalTaskRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final OptimizationService optimizationService = mock(OptimizationService.class);
	private final GoalManagementService service = new GoalManagementService(goalRepository,
		milestoneRepository, goalTaskRepository, mock(PlanningService.class),
		mock(OrchestratorService.class), mock(TaskCenterService.class), memoryService,
		optimizationService, mock(AgentRuntimeService.class), auditService);

	@BeforeEach
	void setUp() {
		when(memoryService.findSimilarTasks(anyString(), anyString(), anyInt()))
			.thenReturn(List.of());
		when(memoryService.findSolutions(anyString(), anyString(), anyInt()))
			.thenReturn(List.of());
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
	}

	@Test
	void decomposeCreatesPlanningImplementVerifyMilestones() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.HIGH);

		List<GoalMilestone> milestones = service.decomposeGoal(goal.getGoalId());

		assertEquals(3, milestones.size());
		assertEquals("规划", milestones.get(0).getTitle());
		assertEquals("实现", milestones.get(1).getTitle());
		assertEquals("验证", milestones.get(2).getTitle());
		assertEquals(MilestoneStatus.CREATED, milestones.get(0).getStatus());
		assertEquals(0.0, milestones.get(0).getProgress());
		assertNotNull(milestones.get(0).getMilestoneId());
		assertEquals(goal.getGoalId(), milestones.get(0).getGoalId());
	}

	@Test
	void decomposeMarksGoalRunning() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);
		assertEquals(GoalStatus.CREATED, goal.getStatus());

		service.analyzeGoal(goal.getGoalId());
		assertEquals(GoalStatus.PLANNING, goal.getStatus());

		service.decomposeGoal(goal.getGoalId());
		assertEquals(GoalStatus.RUNNING, goal.getStatus());
	}

	@Test
	void createMilestonesIsIdempotent() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		List<GoalMilestone> first = service.createMilestones(goal.getGoalId());
		List<GoalMilestone> second = service.createMilestones(goal.getGoalId());

		assertEquals(first.size(), second.size());
		assertEquals(first.get(0).getMilestoneId(), second.get(0).getMilestoneId());
		assertEquals(3, milestoneRepository.listByGoal(goal.getGoalId()).size());
	}

	@Test
	void decomposeAuditsGoalDecomposedWithMilestoneCount() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.CRITICAL);

		service.decomposeGoal(goal.getGoalId());

		EventRecord event = lastEvent(EventType.GOAL_DECOMPOSED);
		assertEquals(goal.getGoalId(), event.aggregateId());
		assertEquals(3, event.metadata().get("milestoneCount"));
		assertEquals("goal", event.aggregateType());
	}

	@Test
	void decomposeUnknownGoalFails() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
			() -> service.decomposeGoal("missing"));
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
