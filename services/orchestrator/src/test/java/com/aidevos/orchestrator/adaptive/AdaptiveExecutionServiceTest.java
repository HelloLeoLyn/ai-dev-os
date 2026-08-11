package com.aidevos.orchestrator.adaptive;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Adaptive execution service verification: feedback collection, the decision
 * rules (retry -> switch agent -> replan, tool errors -> change tool), the
 * session analysis and the in-memory lookups, all audited through the
 * existing AuditService.
 */
class AdaptiveExecutionServiceTest {

	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final TaskCenterService taskCenterService = mock(TaskCenterService.class);
	private final AgentOptimizationService agentOptimizationService =
		mock(AgentOptimizationService.class);
	private final OptimizationService optimizationService = mock(OptimizationService.class);
	private final MemoryService memoryService = mock(MemoryService.class);
	private final PlanningService planningService = mock(PlanningService.class);
	private final AgentRuntimeService runtimeService = mock(AgentRuntimeService.class);
	private final AdaptiveExecutionService service = new AdaptiveExecutionService(
		auditService, taskCenterService, agentOptimizationService, optimizationService,
		memoryService, planningService, runtimeService);

	@BeforeEach
	void setUp() {
		when(taskCenterService.getTask("task-1"))
			.thenReturn(Optional.of(new TaskRecord("task-1", "Implement login",
				"Append a line to a.txt", "project-x")));
		when(optimizationService.getRecommendations(anyString())).thenReturn(List.of());
		when(agentOptimizationService.scoreAllAgents()).thenReturn(List.of(
			new AgentScore("HERMES", 10, 80, 900, 20, 50, 60),
			new AgentScore("CODEX", 10, 40, 1500, 60, 50, 50),
			new AgentScore("OPENCLAW", 10, 90, 800, 10, 50, 80)));
	}

	@Test
	void collectFeedbackStoresAndAudits() {
		ExecutionFeedback feedback = service.collectFeedback("task-1", "session-1",
			"CODEX_IMPLEMENTATION", "CODEX", "FAILED", "compile error", 1200);

		assertNotNull(feedback.getFeedbackId());
		assertEquals("task-1", feedback.getTaskId());
		assertEquals(1200L, feedback.getDuration());
		assertTrue(service.getFeedback(feedback.getFeedbackId()).isPresent());
		EventRecord event = events().stream()
			.filter(item -> item.type() == EventType.EXECUTION_FEEDBACK_RECEIVED)
			.reduce((first, second) -> second)
			.orElseThrow(() -> new AssertionError("missing EXECUTION_FEEDBACK_RECEIVED"));
		assertEquals("CODEX_IMPLEMENTATION", event.metadata().get("nodeId"));
		assertEquals("task-1", event.taskId());
	}

	@Test
	void decideRetriesFirstFailure() {
		AdaptationDecision decision = service.decide("task-1", "session-1",
			"CODEX_IMPLEMENTATION", "CODEX", "compile error", 1);

		assertEquals(AdaptationAction.RETRY, decision.getAction());
		assertEquals("CODEX_IMPLEMENTATION", decision.getNodeId());
	}

	@Test
	void decideSwitchesAgentOnSecondFailure() {
		AdaptationDecision decision = service.decide("task-1", "session-1",
			"CODEX_IMPLEMENTATION", "CODEX", "compile error", 2);

		assertEquals(AdaptationAction.SWITCH_AGENT, decision.getAction());
		assertEquals("OPENCLAW", decision.getTargetAgent());
		assertEvent(EventType.ADAPTATION_DECIDED);
	}

	@Test
	void decideReplansAfterRepeatedFailures() {
		AdaptationDecision decision = service.decide("task-1", "session-1",
			"CODEX_IMPLEMENTATION", "CODEX", "compile error", 3);

		assertEquals(AdaptationAction.REPLAN, decision.getAction());
	}

	@Test
	void decideChangesToolOnToolError() {
		AdaptationDecision decision = service.decide("task-1", "session-1",
			"CODEX_IMPLEMENTATION", "CODEX", "mcp tool not available", 1);

		assertEquals(AdaptationAction.CHANGE_TOOL, decision.getAction());
		assertEquals("mcp-router", decision.getToolId());
	}

	@Test
	void analyzeExecutionReturnsDecisionFromFeedback() {
		AgentSession session = new AgentSession("session-1", "task-1", "graph-1");
		when(runtimeService.getSession("session-1")).thenReturn(Optional.of(session));
		service.collectFeedback("task-1", "session-1", "CODEX_IMPLEMENTATION", "CODEX",
			"FAILED", "compile error", 900);

		AdaptationDecision decision = service.analyzeExecution("session-1");

		assertEquals(AdaptationAction.RETRY, decision.getAction());
		assertEvent(EventType.ADAPTATION_STARTED);
		assertEvent(EventType.ADAPTATION_DECIDED);
		EventRecord started = lastEvent(EventType.ADAPTATION_STARTED);
		assertEquals(1, started.metadata().get("failureCount"));
	}

	@Test
	void analyzeExecutionWithoutFeedbackReturnsNull() {
		AgentSession session = new AgentSession("session-1", "task-1", "graph-1");
		when(runtimeService.getSession("session-1")).thenReturn(Optional.of(session));

		assertNull(service.analyzeExecution("session-1"));
	}

	@Test
	void analyzeExecutionRejectsUnknownSession() {
		when(runtimeService.getSession("missing")).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
			() -> service.analyzeExecution("missing"));
	}

	@Test
	void lookupsFilterByTask() {
		service.collectFeedback("task-1", "session-1", "n1", "CODEX", "FAILED", "e", 10);
		service.collectFeedback("task-2", "session-2", "n2", "CODEX", "FAILED", "e", 10);
		service.decide("task-1", "session-1", "n1", "CODEX", "e", 1);

		assertEquals(1, service.feedbacksForTask("task-1").size());
		assertEquals(1, service.feedbacksForTask("task-2").size());
		assertEquals(1, service.decisionsForTask("task-1").size());
		assertEquals(0, service.decisionsForTask("task-2").size());
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
