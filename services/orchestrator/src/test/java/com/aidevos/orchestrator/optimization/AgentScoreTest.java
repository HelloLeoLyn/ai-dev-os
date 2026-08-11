package com.aidevos.orchestrator.optimization;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.collaboration.InMemoryAgentMessageRepository;
import com.aidevos.orchestrator.collaboration.InMemoryAgentTeamRepository;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanApprovalStatus;
import com.aidevos.orchestrator.human.InMemoryHumanApprovalRepository;
import com.aidevos.orchestrator.metrics.agent.AgentMetrics;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.observability.usage.UsageSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Agent scoring verification: composite scores derived from the existing
 * AgentMetrics plus the collaboration team and human approval stores, the
 * AGENT_SCORE_UPDATED audit event and the ranking order.
 */
class AgentScoreTest {

	private final InMemoryAgentTeamRepository teamRepository =
		new InMemoryAgentTeamRepository();
	private final InMemoryAgentMessageRepository messageRepository =
		new InMemoryAgentMessageRepository();
	private final InMemoryHumanApprovalRepository approvalRepository =
		new InMemoryHumanApprovalRepository();
	private final InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
	private final AuditService auditService = new AuditService(auditRepository);
	private final AgentMetricsService metricsService = mock(AgentMetricsService.class);
	private final ExecutionTraceService traceService =
		new ExecutionTraceService(new InMemoryTraceRepository());
	private final UsageService usageService = mock(UsageService.class);
	private final AgentOptimizationService service = new AgentOptimizationService(
		metricsService, traceService, usageService, teamRepository, messageRepository,
		approvalRepository, auditService);

	@BeforeEach
	void setUp() {
		when(usageService.getAgentUsage(anyString())).thenReturn(UsageSummary.empty());
	}

	@Test
	void scoreAgentCombinesExecutionCollaborationAndApprovalFigures() {
		when(metricsService.listAgentMetrics()).thenReturn(List.of(
			metrics("CODEX", 10, 8, 2, 1200)));

		AgentTeam first = new AgentTeam("team-1", "task-1", "session-1");
		first.addAgent("CODEX");
		first.addAgent("TEST_AGENT");
		teamRepository.save(first);
		AgentTeam second = new AgentTeam("team-2", "task-2", "session-2");
		second.addAgent("TEST_AGENT");
		teamRepository.save(second);

		approvalRepository.save(approval("approval-1", "CODEX", HumanApprovalStatus.PENDING));
		approvalRepository.save(approval("approval-2", "CODEX", HumanApprovalStatus.APPROVED));

		AgentScore score = service.scoreAgent("CODEX");

		assertEquals("CODEX", score.agentType());
		assertEquals(10, score.totalExecutions());
		assertEquals(80.0, score.successRate());
		assertEquals(20.0, score.failureRate());
		assertEquals(1200, score.avgDuration());
		assertEquals(50.0, score.collaborationScore());
		assertEquals(50.0, score.humanApprovalRate());
		assertScoreUpdatedEvent("CODEX");
	}

	@Test
	void scoreAgentWithNoHistoryIsNeutral() {
		when(metricsService.listAgentMetrics()).thenReturn(List.of());

		AgentScore score = service.scoreAgent("REPAIR_AGENT");

		assertEquals("REPAIR_AGENT", score.agentType());
		assertEquals(0, score.totalExecutions());
		assertEquals(0.0, score.successRate());
		assertEquals(0.0, score.failureRate());
		assertEquals(0.0, score.collaborationScore());
		assertEquals(0.0, score.humanApprovalRate());
	}

	@Test
	void scoreAgentRejectsBlankType() {
		assertThrows(IllegalArgumentException.class, () -> service.scoreAgent(" "));
	}

	@Test
	void scoreAllAgentsRanksByCompositeDescending() {
		when(metricsService.listAgentMetrics()).thenReturn(List.of(
			metrics("HERMES", 10, 5, 5, 1500),
			metrics("CODEX", 10, 9, 1, 1200)));

		List<AgentScore> scores = service.scoreAllAgents();

		assertEquals(2, scores.size());
		assertEquals("CODEX", scores.get(0).agentType());
		assertEquals("HERMES", scores.get(1).agentType());
		assertTrue(scores.get(0).composite() > scores.get(1).composite());
	}

	private AgentMetrics metrics(String agent, int total, int success, int failed,
			long averageDuration) {
		return new AgentMetrics("agent-" + agent, agent, total, success, failed, 0,
			averageDuration, Instant.now().minusSeconds(60), 0, 0);
	}

	private HumanApproval approval(String id, String requester,
			HumanApprovalStatus status) {
		return new HumanApproval(id, "task-1", "session-1", "team-1", "HUMAN_GATE",
			status, requester, null, null, Instant.now(), null);
	}

	private void assertScoreUpdatedEvent(String agentType) {
		List<EventRecord> events = auditRepository.query(EventQuery.all());
		assertTrue(events.stream().anyMatch(event -> event.type() == EventType.AGENT_SCORE_UPDATED
			&& agentType.equals(event.metadata().get("agentType"))),
			"missing AGENT_SCORE_UPDATED for " + agentType);
	}
}
