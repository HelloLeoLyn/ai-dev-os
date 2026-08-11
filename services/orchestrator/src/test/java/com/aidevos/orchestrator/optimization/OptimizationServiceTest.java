package com.aidevos.orchestrator.optimization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.observability.TraceStatus;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Optimization service verification: the learning loop (analyze) auditing
 * OPTIMIZATION_STARTED / OPTIMIZATION_RECOMMENDED / OPTIMIZATION_COMPLETED,
 * recommendation generation from failed traces and agent scores, the
 * AGENT_EXPERIENCE memory write and the session-scoped analysis.
 */
class OptimizationServiceTest {

	private final InMemoryOptimizationRepository repository =
		new InMemoryOptimizationRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
	private final MemoryService memoryService = new MemoryService(memoryRepository);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
	private final ExecutionTraceService traceService =
		new ExecutionTraceService(traceRepository);
	private final AgentRuntimeService runtimeService = mock(AgentRuntimeService.class);
	private final AgentCollaborationService collaborationService =
		mock(AgentCollaborationService.class);
	private final OptimizationService service = new OptimizationService(repository,
		auditService, memoryService, taskCenterService, agentOptimizationService,
		traceService, runtimeService, collaborationService);

	@Test
	void analyzeTaskRecordsRecommendationsAuditsAndWritesExperience() {
		task("task-1");
		stubBestAgent();
		traceRepository.save(restoredTrace("trace-1", "CODEX_IMPLEMENTATION", "CODEX",
			TraceStatus.FAILED, 500));

		List<OptimizationRecord> records = service.analyzeTask("task-1");

		assertFalse(records.isEmpty());
		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.FAILURE_PATTERN));
		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.AGENT_SELECTION));
		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.TOOL_USAGE));
		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.GRAPH_FLOW));
		assertEvent(EventType.OPTIMIZATION_STARTED);
		assertEvent(EventType.OPTIMIZATION_RECOMMENDED);
		assertEvent(EventType.OPTIMIZATION_COMPLETED);

		List<MemoryRecord> experience = memoryService.list("project-x",
			MemoryType.AGENT_EXPERIENCE);
		assertFalse(experience.isEmpty());
		assertTrue(experience.get(0).getContent().contains("failedPattern"));
		assertTrue(experience.get(0).getContent().contains("recommendation"));
	}

	@Test
	void generateRecommendationsOnCleanTaskOnlyRecommendsAgentSelection() {
		stubBestAgent();

		List<OptimizationRecord> records = service.generateRecommendations("task-1", null);

		assertEquals(1, records.size());
		assertEquals(OptimizationType.AGENT_SELECTION, records.get(0).getType());
		assertTrue(records.get(0).getRecommendation().contains("CODEX"));
	}

	@Test
	void recordOptimizationStoresAndAudits() {
		OptimizationRecord record = service.recordOptimization("task-1", "session-1",
			OptimizationType.PERFORMANCE, "Split the slow node", 0.7);

		assertNotNull(record.getId());
		assertEquals("task-1", record.getTaskId());
		assertEquals("session-1", record.getSessionId());
		assertEquals(OptimizationType.PERFORMANCE, record.getType());
		assertEquals(0.7, record.getConfidence());
		assertNotNull(record.getCreatedAt());
		assertEquals(record.getId(), repository.get(record.getId()).getId());
		var event = lastEvent(EventType.OPTIMIZATION_RECOMMENDED);
		assertEquals(record.getId(), event.metadata().get("optimizationId"));
		assertEquals("task-1", event.taskId());
		assertEquals("session-1", event.metadata().get("sessionId"));
	}

	@Test
	void getRecommendationsFiltersByTask() {
		service.recordOptimization("task-1", null, OptimizationType.GRAPH_FLOW,
			"Reorder nodes", 0.6);
		service.recordOptimization("task-2", null, OptimizationType.TOOL_USAGE,
			"Enable tools", 0.5);

		List<OptimizationRecord> taskOne = service.getRecommendations("task-1");
		assertEquals(1, taskOne.size());
		assertEquals(OptimizationType.GRAPH_FLOW, taskOne.get(0).getType());
		assertEquals(2, service.getAllRecommendations().size());
	}

	@Test
	void analyzeSessionScopesRecommendationsToSession() {
		task("task-1");
		stubBestAgent();
		when(runtimeService.getSession("session-1"))
			.thenReturn(Optional.of(new AgentSession("session-1", "task-1", "graph-1")));

		List<OptimizationRecord> records = service.analyzeSession("session-1");

		assertFalse(records.isEmpty());
		assertTrue(records.stream().allMatch(record ->
			"session-1".equals(record.getSessionId())));
		var completed = lastEvent(EventType.OPTIMIZATION_COMPLETED);
		assertEquals("session-1", completed.metadata().get("sessionId"));
	}

	@Test
	void analyzeSessionRejectsUnknownSession() {
		when(runtimeService.getSession(anyString())).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
			() -> service.analyzeSession("missing-session"));
	}

	@Test
	void suggestGraphOptimizationsProducesReadOnlySuggestions() {
		traceRepository.save(restoredTrace("trace-1", "CODEX_IMPLEMENTATION", "CODEX",
			TraceStatus.FAILED, 500));
		traceRepository.save(restoredTrace("trace-2", "TEST_AGENT_VERIFY", "TEST_AGENT",
			TraceStatus.SUCCESS, 15_000));
		stubBestAgent();

		List<GraphOptimizationSuggestion> suggestions =
			service.suggestGraphOptimizations("task-1");

		assertTrue(suggestions.stream().anyMatch(suggestion ->
			GraphOptimizationSuggestion.AGENT_REPLACEMENT.equals(suggestion.type())
				&& "CODEX".equals(suggestion.currentAgent())));
		assertTrue(suggestions.stream().anyMatch(suggestion ->
			GraphOptimizationSuggestion.ORDER.equals(suggestion.type())
				&& "TEST_AGENT_VERIFY".equals(suggestion.nodeId())));
		assertTrue(suggestions.stream().anyMatch(suggestion ->
			GraphOptimizationSuggestion.TOOL_REPLACEMENT.equals(suggestion.type())));
	}

	private TaskRecord task(String taskId) {
		TaskRecord task = new TaskRecord(taskId, "Implement login", "Append a line to a.txt",
			"project-x", "workspace-1");
		when(taskCenterService.getTask(taskId)).thenReturn(Optional.of(task));
		return task;
	}

	private void stubBestAgent() {
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of(
			new AgentScore("CODEX", 10, 90.0, 1200, 10.0, 50.0, 80.0)));
	}

	private TraceRecord restoredTrace(String traceId, String nodeId, String agentType,
			TraceStatus status, long duration) {
		Instant start = Instant.now().minusSeconds(duration / 1000 + 1);
		return TraceRecord.restore(traceId, "task-1", "project-x", "graph-1", nodeId,
			agentType, null, status, start, start.plusMillis(duration), duration,
			status == TraceStatus.FAILED ? "boom" : null);
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
