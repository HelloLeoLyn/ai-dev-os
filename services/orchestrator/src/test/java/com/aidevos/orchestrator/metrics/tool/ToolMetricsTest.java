package com.aidevos.orchestrator.metrics.tool;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit verification of tool observability: the TOOL_* mcp-tool audit events
 * are aggregated into execution/success/failure/denial counts and the average
 * duration.
 */
class ToolMetricsTest {

	private AuditService auditService;
	private ToolMetricsService toolMetricsService;

	@BeforeEach
	void setUp() {
		auditService = new AuditService(new InMemoryAuditRepository());
		toolMetricsService = new ToolMetricsService(auditService);
	}

	@Test
	void shouldAggregateToolStatisticsFromAuditEvents() {
		auditService.toolExecutionEvent(EventType.TOOL_STARTED, "git", "CODEX", "task-1",
			"STARTED", "started", Map.of("toolId", "git", "duration", 0));
		auditService.toolExecutionEvent(EventType.TOOL_COMPLETED, "git", "CODEX", "task-1",
			"COMPLETED", "completed", Map.of("toolId", "git", "duration", 100));
		auditService.toolExecutionEvent(EventType.TOOL_COMPLETED, "git", "CODEX", "task-2",
			"COMPLETED", "completed", Map.of("toolId", "git", "duration", 300));
		auditService.toolExecutionEvent(EventType.TOOL_FAILED, "terminal", "TEST_AGENT",
			"task-3", "FAILED", "failed", Map.of("toolId", "terminal", "duration", 50));
		auditService.toolExecutionEvent(EventType.TOOL_DENIED, "docker", "CODEX", "task-4",
			"DENIED", "denied", Map.of("toolId", "docker", "duration", 0));

		List<ToolMetrics> metrics = toolMetricsService.listToolMetrics();

		ToolMetrics git = metrics.stream().filter(item -> "git".equals(item.toolId()))
			.findFirst().orElseThrow();
		assertEquals(1, git.executeCount());
		assertEquals(2, git.successCount());
		assertEquals(0, git.failedCount());
		assertEquals(0, git.deniedCount());
		assertEquals(200, git.averageDurationMillis());

		ToolMetrics terminal = metrics.stream().filter(item -> "terminal".equals(item.toolId()))
			.findFirst().orElseThrow();
		assertEquals(0, terminal.executeCount());
		assertEquals(1, terminal.failedCount());
		assertEquals(50, terminal.averageDurationMillis());

		ToolMetrics docker = metrics.stream().filter(item -> "docker".equals(item.toolId()))
			.findFirst().orElseThrow();
		assertEquals(1, docker.deniedCount());
	}

	@Test
	void shouldReturnZeroesForUnknownTool() {
		ToolMetrics metrics = toolMetricsService.getToolMetrics("missing");
		assertEquals("missing", metrics.toolId());
		assertEquals(0, metrics.executeCount());
		assertEquals(0, metrics.successCount());
		assertEquals(0, metrics.failedCount());
		assertEquals(0, metrics.deniedCount());
	}
}
