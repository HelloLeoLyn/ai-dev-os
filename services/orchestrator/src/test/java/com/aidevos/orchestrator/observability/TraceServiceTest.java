package com.aidevos.orchestrator.observability;

import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit verification of the execution trace lifecycle: creation, node/tool
 * binding and the SUCCESS / FAILED transitions with audit events.
 */
class TraceServiceTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private InMemoryTraceRepository traceRepository;
	private ExecutionTraceService traceService;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		traceRepository = new InMemoryTraceRepository();
		traceService = new ExecutionTraceService(traceRepository, auditService);
	}

	@Test
	void shouldCreateRunningNodeTrace() {
		TraceRecord trace = traceService.startNode("task-1", "project-1", "graph-1",
			"CODEX_IMPLEMENTATION", "CODEX");

		assertNotNull(trace.getTraceId());
		assertEquals("task-1", trace.getTaskId());
		assertEquals("project-1", trace.getProjectId());
		assertEquals("graph-1", trace.getGraphId());
		assertEquals("CODEX_IMPLEMENTATION", trace.getNodeId());
		assertEquals("CODEX", trace.getAgentType());
		assertEquals(TraceStatus.RUNNING, trace.getStatus());
		assertTrue(events(EventType.TRACE_STARTED).stream()
			.anyMatch(event -> trace.getTraceId().equals(event.aggregateId())
				&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldCompleteNodeTrace() {
		TraceRecord trace = traceService.startNode("task-1", "project-1", "graph-1",
			"TEST_AGENT_VERIFY", "TEST_AGENT");

		TraceRecord completed = traceService.completeNode(trace.getTraceId()).orElseThrow();

		assertEquals(TraceStatus.SUCCESS, completed.getStatus());
		assertNotNull(completed.getEndTime());
		assertTrue(completed.getDuration() >= 0);
		assertTrue(events(EventType.TRACE_COMPLETED).stream()
			.anyMatch(event -> trace.getTraceId().equals(event.aggregateId())));
	}

	@Test
	void shouldFailNodeTraceWithError() {
		TraceRecord trace = traceService.startNode("task-1", null, "graph-1",
			"CODEX_IMPLEMENTATION", "CODEX");

		TraceRecord failed = traceService.failNode(trace.getTraceId(), "boom").orElseThrow();

		assertEquals(TraceStatus.FAILED, failed.getStatus());
		assertEquals("boom", failed.getErrorMessage());
		assertTrue(events(EventType.TRACE_FAILED).stream()
			.anyMatch(event -> trace.getTraceId().equals(event.aggregateId())));
	}

	@Test
	void shouldStartToolTraceAndListByTask() {
		TraceRecord toolTrace = traceService.startTool("task-1", "project-1", "git", "CODEX");
		assertEquals("git", toolTrace.getToolId());
		assertEquals("CODEX", toolTrace.getAgentType());

		List<TraceRecord> traces = traceService.listByTask("task-1");
		assertEquals(1, traces.size());
		assertEquals(toolTrace.getTraceId(), traces.get(0).getTraceId());
		assertEquals(1, traceService.listByProject("project-1").size());
		assertTrue(traceService.listByTask("other-task").isEmpty());
	}

	private List<EventRecord> events(EventType type) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type).toList();
	}
}
