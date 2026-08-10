package com.aidevos.orchestrator.observability.usage;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit verification of token/cost aggregation per task, project and agent,
 * plus the USAGE_RECORDED audit event.
 */
class UsageServiceTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private InMemoryUsageRepository repository;
	private UsageService usageService;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		repository = new InMemoryUsageRepository();
		usageService = new UsageService(repository, auditService);
	}

	@Test
	void shouldRecordUsageAndAudit() {
		UsageRecord record = usageService.recordUsage("task-1", "project-1", "CODEX",
			"gpt-test", 1000, 2000);

		assertEquals(3000, record.totalTokens());
		assertEquals(1000, record.inputTokens());
		assertEquals(2000, record.outputTokens());
		// 1000 * 0.003/1k + 2000 * 0.015/1k = 0.003 + 0.03 = 0.033
		assertEquals(0.033, record.estimatedCost(), 0.000001);
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.USAGE_RECORDED
				&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldAggregateUsageByTaskProjectAndAgent() {
		usageService.recordUsage("task-1", "project-1", "CODEX", "m1", 1000, 1000);
		usageService.recordUsage("task-1", "project-1", "CODEX", "m1", 500, 500);
		usageService.recordUsage("task-2", "project-1", "TEST_AGENT", "m2", 100, 100);
		usageService.recordUsage("task-3", "project-2", "CODEX", "m1", 10, 10);

		UsageSummary task = usageService.getTaskUsage("task-1");
		assertEquals(2, task.recordCount());
		assertEquals(1500, task.inputTokens());
		assertEquals(1500, task.outputTokens());
		assertEquals(3000, task.totalTokens());

		UsageSummary project = usageService.getProjectUsage("project-1");
		assertEquals(3, project.recordCount());
		assertEquals(1600, project.inputTokens());
		assertEquals(3200, project.totalTokens());

		UsageSummary agent = usageService.getAgentUsage("CODEX");
		assertEquals(3, agent.recordCount());
		assertEquals(3020, agent.totalTokens());
		assertEquals(project.estimatedCost() + agent.estimatedCost()
			- project.estimatedCost(), agent.estimatedCost(), 0.000001);
	}

	@Test
	void shouldReturnEmptySummaryWhenNothingMatches() {
		UsageSummary summary = usageService.getTaskUsage("missing");
		assertEquals(0, summary.recordCount());
		assertEquals(0, summary.totalTokens());
		assertEquals(0.0, summary.estimatedCost());
	}
}
