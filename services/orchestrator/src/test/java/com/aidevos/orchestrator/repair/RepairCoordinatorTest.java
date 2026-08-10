package com.aidevos.orchestrator.repair;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestStatus;
import com.aidevos.orchestrator.testagent.TestType;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit verification of the repair state machine: PENDING -> ANALYZING ->
 * FIXING -> VERIFYING -> SUCCESS | FAILED, bounded retries, audit events and
 * memory (BUG_RECORD resolved flag / AGENT_EXPERIENCE).
 */
class RepairCoordinatorTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private TaskCenterService taskCenterService;
	private TestAgentService testAgentService;
	private PlannerService plannerService;
	private CodexExecutor codexExecutor;
	private WorkspaceService workspaceService;
	private InMemoryMemoryRepository memoryRepository;
	private MemoryService memoryService;
	private InMemoryAuditRepository auditRepository;
	private RepairCoordinator coordinator;

	@BeforeEach
	void setUp() {
		taskCenterService = mock(TaskCenterService.class);
		testAgentService = mock(TestAgentService.class);
		plannerService = mock(PlannerService.class);
		codexExecutor = mock(CodexExecutor.class);
		workspaceService = mock(WorkspaceService.class);
		memoryRepository = new InMemoryMemoryRepository();
		memoryService = new MemoryService(memoryRepository);
		auditRepository = new InMemoryAuditRepository();
		coordinator = new RepairCoordinator(taskCenterService, testAgentService, plannerService,
			codexExecutor, workspaceService, memoryService, new AuditService(auditRepository),
			mock(ChangeService.class));

		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(task()));
		when(testAgentService.listTests()).thenReturn(List.of(failedTest()));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, NOW)));
		when(workspaceService.getWorkspace("workspace-1")).thenReturn(
			Optional.of(new Workspace("workspace-1", "project-a", "/tmp/repo", "main",
				WorkspaceStatus.READY, NOW, NOW)));
		when(workspaceService.getGitDiff("workspace-1")).thenReturn(
			new GitDiff(1, 2, 1, "1 file changed, 2 insertions(+), 1 deletion(-)"));
	}

	@Test
	void shouldRunRepairToSuccessAndPersistResolvedBug() {
		when(codexExecutor.execute(any())).thenReturn(success());
		when(testAgentService.createTest(any(CreateTestRequest.class))).thenReturn(passingTest());

		RepairTask repair = coordinator.start("task-1");

		assertEquals(RepairStatus.SUCCESS, repair.getStatus());
		assertEquals(0, repair.getRetryCount());
		assertTrue(repair.getLastResult().contains("attempt"));
		assertEquals("workspace-1", repair.getWorkspaceId());
		assertEquals("test-1", repair.getFailureContext().testId());

		// Audit: full state machine with taskId.
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_STARTED
			&& "task-1".equals(event.taskId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_ANALYZING));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_FIXING));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_VERIFYING));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_SUCCESS));

		// Memory: BUG_RECORD resolved + AGENT_EXPERIENCE.
		MemoryRecord bug = memoryRepository.list("project-a", MemoryType.BUG_RECORD).stream()
			.filter(record -> ("bug:repair:task-1").equals(record.getKey()))
			.findFirst().orElseThrow();
		assertEquals(Boolean.TRUE, bug.getResolved());
		assertTrue(bug.getSolution() != null && bug.getSolution().contains("attempt"));
		assertTrue(memoryRepository.list("project-a", MemoryType.AGENT_EXPERIENCE).stream()
			.anyMatch(record -> ("experience:repair:task-1").equals(record.getKey())));
	}

	@Test
	void shouldFailAfterBoundedRetriesAndPersistUnresolvedBug() {
		when(codexExecutor.execute(any())).thenReturn(success());
		when(testAgentService.createTest(any(CreateTestRequest.class))).thenReturn(failingTest());

		RepairTask repair = coordinator.start("task-1");

		assertEquals(RepairStatus.FAILED, repair.getStatus());
		assertEquals(RepairPolicy.MAX_RETRY, repair.getRetryCount());
		verify(testAgentService, times(RepairPolicy.MAX_RETRY)).createTest(any());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_FAILED));

		MemoryRecord bug = memoryRepository.list("project-a", MemoryType.BUG_RECORD).stream()
			.filter(record -> ("bug:repair:task-1").equals(record.getKey()))
			.findFirst().orElseThrow();
		assertEquals(Boolean.FALSE, bug.getResolved());
	}

	@Test
	void shouldFailWhenNoWorkspaceAndNeverInvokeCodex() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(new TaskRecord("task-1", "name", "desc", "project-a")));
		when(testAgentService.createTest(any(CreateTestRequest.class))).thenReturn(failingTest());

		RepairTask repair = coordinator.start("task-1");

		assertEquals(RepairStatus.FAILED, repair.getStatus());
		verifyNoInteractions(codexExecutor);
	}

	@Test
	void shouldRejectTaskWithoutFailedTest() {
		when(testAgentService.listTests()).thenReturn(List.of());

		assertThrows(IllegalArgumentException.class, () -> coordinator.start("task-1"));
		assertFalse(coordinator.get("task-1").isPresent());
	}

	private TaskRecord task() {
		return new TaskRecord("task-1", "Implement login", "Login flow", "project-a",
			"workspace-1");
	}

	private TestPlan failedTest() {
		TestPlan plan = new TestPlan("test-1", "task-1", TestType.UNIT_TEST, "mvn test",
			"project-a", "exec-1");
		plan.markFailed("exit code 1", "BUILD FAILURE");
		return plan;
	}

	private TestPlan passingTest() {
		TestPlan plan = new TestPlan("test-2", "task-1", TestType.UNIT_TEST, "mvn test",
			"project-a", "exec-2");
		plan.markSuccess("exit code 0", "BUILD SUCCESS");
		return plan;
	}

	private TestPlan failingTest() {
		TestPlan plan = new TestPlan("test-3", "task-1", TestType.UNIT_TEST, "mvn test",
			"project-a", "exec-3");
		plan.markFailed("exit code 1", "BUILD FAILURE");
		return plan;
	}

	private ExecutionResult success() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Repair applied");
		result.setOutput("fixed");
		return result;
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
