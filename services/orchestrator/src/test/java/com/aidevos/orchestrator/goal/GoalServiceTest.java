package com.aidevos.orchestrator.goal;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryContext;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Goal service verification: goal creation, analysis (planning), milestone
 * decomposition and the GOAL_* audit trail.
 */
class GoalServiceTest {

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
	void createGoalCreatesAndAudits() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.HIGH);

		assertNotNull(goal.getGoalId());
		assertEquals("project-x", goal.getProjectId());
		assertEquals("开发用户管理模块", goal.getTitle());
		assertEquals(GoalStatus.CREATED, goal.getStatus());
		assertEquals(GoalPriority.HIGH, goal.getPriority());
		assertEquals(0.0, goal.getProgress());
		assertTrue(service.getGoal(goal.getGoalId()).isPresent());
		EventRecord event = events().stream()
			.filter(item -> item.type() == EventType.GOAL_CREATED)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing GOAL_CREATED"));
		assertEquals(goal.getGoalId(), event.aggregateId());
		assertEquals("HIGH", event.metadata().get("priority"));
	}

	@Test
	void createGoalRejectsBlankTitle() {
		assertThrows(IllegalArgumentException.class,
			() -> service.createGoal("project-x", " ", "desc", GoalPriority.NORMAL));
	}

	@Test
	void analyzeGoalMarksPlanningAndAudits() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		MemoryContext context = service.analyzeGoal(goal.getGoalId());

		assertEquals(GoalStatus.PLANNING, goal.getStatus());
		assertNotNull(context);
		assertEvent(EventType.GOAL_PLANNING_STARTED);
		EventRecord event = lastEvent(EventType.GOAL_PLANNING_STARTED);
		assertEquals(goal.getGoalId(), event.aggregateId());
	}

	@Test
	void decomposeGoalCreatesMilestonesAndAudits() {
		Goal goal = service.createGoal("project-x", "开发用户管理模块", "开发用户管理模块",
			GoalPriority.NORMAL);

		List<GoalMilestone> milestones = service.decomposeGoal(goal.getGoalId());

		assertEquals(3, milestones.size());
		assertEquals(GoalStatus.RUNNING, goal.getStatus());
		assertEquals(3, service.getMilestones(goal.getGoalId()).size());
		assertEvent(EventType.GOAL_DECOMPOSED);
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
	void getGoalReturnsEmptyForUnknownGoal() {
		assertEquals(Optional.empty(), service.getGoal("missing"));
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
