package com.aidevos.orchestrator.orchestrator;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestrator service verification: submission, priority scheduling,
 * prioritization, agent assignment, start (runtime + dynamic graph) and
 * pause, plus the ORCHESTRATOR_STARTED / TASK_QUEUED / TASK_PRIORITIZED /
   DYNAMIC_GRAPH_CREATED audit trail.
 */
class OrchestratorServiceTest {

	private final InMemoryTaskQueueRepository queueRepository =
		new InMemoryTaskQueueRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final AgentAutoSelectionService autoSelectionService =
		mock(AgentAutoSelectionService.class);
	private final com.aidevos.orchestrator.optimization.OptimizationService optimizationService =
		mock(com.aidevos.orchestrator.optimization.OptimizationService.class);
	private final AgentRuntimeService runtimeService = mock(AgentRuntimeService.class);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final OrchestratorService service = new OrchestratorService(queueRepository,
		auditService, taskCenterService, autoSelectionService, optimizationService,
		runtimeService, new ExecutionGraphBuilder(), memoryService);

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(task("task-1")));
		when(taskCenterService.getTask("task-2"))
			.thenReturn(Optional.of(task("task-2")));
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
		when(autoSelectionService.selectAgents(anyString(), anyString(), any()))
			.thenReturn(List.of(AgentType.HERMES, AgentType.CODEX, AgentType.TEST_AGENT));
	}

	@Test
	void submitTaskQueuesAndStartsOrchestrator() {
		OrchestrationTask task = service.submitTask("task-1", "CODE_GENERATION",
			TaskPriority.HIGH, List.of("codex"));

		assertEquals(OrchestrationTaskStatus.QUEUED, task.getStatus());
		assertEquals(TaskPriority.HIGH, task.getPriority());
		assertEquals(List.of("codex"), task.getRequiredAgents());
		assertTrue(service.getPool().contains("task-1"));
		assertEquals(TaskPoolStatus.RUNNING, service.getPool().getStatus());
		assertEquals("task-1", queueRepository.next().getTaskId());
		assertEvent(EventType.ORCHESTRATOR_STARTED);
		assertEvent(EventType.TASK_QUEUED);
	}

	@Test
	void submitTaskRejectsDuplicates() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);

		assertThrows(IllegalStateException.class,
			() -> service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null));
	}

	@Test
	void scheduleNextTaskPicksHighestPriority() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);
		service.submitTask("task-2", "CODE_GENERATION", TaskPriority.CRITICAL, null);

		OrchestrationTask next = service.scheduleNextTask().orElseThrow();

		assertEquals("task-2", next.getTaskId());
		assertEquals(OrchestrationTaskStatus.RUNNING, next.getStatus());
		assertNotNull(next.getStartedAt());
		assertEquals(OrchestrationTaskStatus.RUNNING,
			service.getPool().get("task-2").getStatus());
	}

	@Test
	void prioritizeTasksSortsByPriorityAndAudits() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.LOW, null);
		service.submitTask("task-2", "CODE_GENERATION", TaskPriority.CRITICAL, null);
		service.submitTask("task-1x", "CODE_GENERATION", TaskPriority.NORMAL, null);

		List<OrchestrationTask> sorted = service.prioritizeTasks();

		assertEquals(List.of("task-2", "task-1x", "task-1"),
			sorted.stream().map(OrchestrationTask::getTaskId).toList());
		assertEvent(EventType.TASK_PRIORITIZED);
	}

	@Test
	void assignAgentsStoresAutoSelectedFlow() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);

		List<String> assigned = service.assignAgents("task-1");

		assertEquals(List.of("HERMES", "CODEX", "TEST_AGENT"), assigned);
		assertEquals(assigned, service.getPool().get("task-1").getAssignedAgents());
		verify(autoSelectionService).selectAgents(eq("task-1"), eq("CODE_GENERATION"), any());
	}

	@Test
	void startTaskRunsRuntimeAndCompletesOrchestration() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.HIGH, null);
		AgentSession session = new AgentSession("session-1", "task-1", "graph-1");
		session.markCompleted();
		when(runtimeService.startSession(eq("task-1"), any(ExecutionGraph.class)))
			.thenReturn(session);

		OrchestrationTask task = service.startTask("task-1");

		assertEquals(OrchestrationTaskStatus.COMPLETED, task.getStatus());
		assertNotNull(task.getStartedAt());
		assertNotNull(task.getCompletedAt());
		assertEquals(TaskPoolStatus.COMPLETED, service.getPool().getStatus());
		verify(runtimeService).startSession(eq("task-1"), any(ExecutionGraph.class));
		var graphEvent = lastEvent(EventType.DYNAMIC_GRAPH_CREATED);
		assertEquals("task-1", graphEvent.taskId());
		assertEquals("CODE_GENERATION", graphEvent.metadata().get("taskType"));
		assertTrue(graphEvent.metadata().containsKey("graphId"));
	}

	@Test
	void startTaskMarksFailedWhenSessionFails() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);
		AgentSession session = new AgentSession("session-1", "task-1", "graph-1");
		session.markFailed();
		when(runtimeService.startSession(eq("task-1"), any(ExecutionGraph.class)))
			.thenReturn(session);

		OrchestrationTask task = service.startTask("task-1");

		assertEquals(OrchestrationTaskStatus.FAILED, task.getStatus());
		assertTrue(task.getErrorMessage().contains("Session failed"));
	}

	@Test
	void pauseTaskPausesOrchestrationAndRuntimeSession() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);
		service.scheduleNextTask();
		AgentSession session = new AgentSession("session-1", "task-1", "graph-1");
		session.markRunning();
		when(runtimeService.sessionsForTask("task-1")).thenReturn(List.of(session));

		OrchestrationTask task = service.pauseTask("task-1");

		assertEquals(OrchestrationTaskStatus.PAUSED, task.getStatus());
		assertEquals(TaskPoolStatus.PAUSED, service.getPool().getStatus());
		verify(runtimeService).pauseSession("session-1");
	}

	@Test
	void pauseRejectsFinishedTasks() {
		service.submitTask("task-1", "CODE_GENERATION", TaskPriority.NORMAL, null);
		service.scheduleNextTask();
		AgentSession session = new AgentSession("session-1", "task-1", "graph-1");
		session.markCompleted();
		when(runtimeService.startSession(eq("task-1"), any(ExecutionGraph.class)))
			.thenReturn(session);
		service.startTask("task-1");

		assertThrows(IllegalStateException.class, () -> service.pauseTask("task-1"));
		verify(runtimeService, never()).pauseSession(anyString());
	}

	private TaskRecord task(String taskId) {
		return new TaskRecord(taskId, "Implement login", "Append a line to a.txt",
			"project-x", "workspace-1");
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
