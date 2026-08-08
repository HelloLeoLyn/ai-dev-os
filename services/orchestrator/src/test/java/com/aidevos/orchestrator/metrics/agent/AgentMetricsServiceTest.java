package com.aidevos.orchestrator.metrics.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.repair.RepairTask;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit verification of agent observability aggregation: per-agent ranking,
 * success/failure counts, average duration, repair/retry/change attribution
 * and per-task execution statistics, all derived from existing records.
 */
class AgentMetricsServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryExecutionRecordRepository recordRepository;
	private InMemoryAuditRepository auditRepository;
	private AgentManager agentManager;
	private RepairCoordinator repairCoordinator;
	private ChangeService changeService;
	private TaskCenterService taskCenterService;
	private AgentMetricsService agentMetricsService;

	@BeforeEach
	void setUp() {
		recordRepository = new InMemoryExecutionRecordRepository();
		auditRepository = new InMemoryAuditRepository();
		agentManager = mock(AgentManager.class);
		repairCoordinator = mock(RepairCoordinator.class);
		changeService = mock(ChangeService.class);
		taskCenterService = mock(TaskCenterService.class);
		when(agentManager.getAllAgents()).thenReturn(
			List.of(agent("planner"), agent("coder"), agent("tester")));
		when(repairCoordinator.listRepairs()).thenReturn(List.of());
		when(changeService.listChanges()).thenReturn(List.of());
		agentMetricsService = new AgentMetricsService(new ExecutionRecordManager(recordRepository),
			agentManager, new AuditService(auditRepository), repairCoordinator, changeService,
			taskCenterService);
	}

	@Test
	void shouldAggregateAgentRankingWithDurations() {
		recordRepository.save(record("exec-1", "task-1", "coder", "SUCCESS",
			NOW, NOW.plusSeconds(10)));
		recordRepository.save(record("exec-2", "task-2", "coder", "SUCCESS",
			NOW.plusSeconds(1), NOW.plusSeconds(6)));
		recordRepository.save(record("exec-3", "task-3", "coder", "FAILED",
			NOW.plusSeconds(2), NOW.plusSeconds(7)));
		recordRepository.save(record("exec-4", "task-4", "tester", "SUCCESS",
			NOW.plusSeconds(3), NOW.plusSeconds(5)));

		List<AgentMetrics> metrics = agentMetricsService.listAgentMetrics();

		AgentMetrics coder = metrics.stream()
			.filter(item -> "coder".equals(item.agentId())).findFirst().orElseThrow();
		assertEquals(3, coder.taskCount());
		assertEquals(2, coder.successCount());
		assertEquals(1, coder.failedCount());
		assertEquals(6666, coder.averageDuration());
		assertEquals(NOW.plusSeconds(10), coder.lastExecutedAt());
		AgentMetrics planner = metrics.stream()
			.filter(item -> "planner".equals(item.agentId())).findFirst().orElseThrow();
		assertEquals(0, planner.taskCount());
		assertEquals("coder", metrics.get(0).agentId());
	}

	@Test
	void shouldAttributeRepairRetryAndChangeCounts() {
		recordRepository.save(record("exec-1", "task-1", "coder", "SUCCESS",
			NOW, NOW.plusSeconds(5)));
		AuditService auditService = new AuditService(auditRepository);
		auditService.repairEvent(EventType.REPAIR_STARTED, "task-1", "repair-1", null,
			"PENDING", "started", Map.of());
		when(repairCoordinator.listRepairs()).thenReturn(
			List.of(repair("repair-1", "task-1", 2)));
		when(changeService.listChanges()).thenReturn(List.of(
			change("change-1", "task-1", ChangeStatus.APPROVED),
			change("change-2", "task-2", ChangeStatus.REJECTED)));

		AgentMetrics coder = agentMetricsService.listAgentMetrics().stream()
			.filter(item -> "coder".equals(item.agentId())).findFirst().orElseThrow();

		assertEquals(1, coder.repairCount());
		assertEquals(2, coder.retryCount());
		assertEquals(1, coder.changeCount());
	}

	@Test
	void shouldReturnAgentDetailWithExecutions() {
		recordRepository.save(record("exec-1", "task-1", "coder", "SUCCESS",
			NOW, NOW.plusSeconds(10)));
		recordRepository.save(record("exec-2", "task-2", "coder", "FAILED",
			NOW.plusSeconds(20), NOW.plusSeconds(30)));

		AgentMetricsDetail detail = agentMetricsService.getAgentDetail("coder");

		assertEquals("coder", detail.metrics().agentId());
		assertEquals(2, detail.metrics().taskCount());
		assertEquals(2, detail.executions().size());
		AgentExecutionMetric latest = detail.executions().get(0);
		assertEquals("task-2", latest.taskId());
		assertEquals(10000, latest.durationMillis());
		assertEquals("FAILED", latest.status());
	}

	@Test
	void shouldReturnTaskExecutionMetrics() {
		recordRepository.save(record("exec-1", "task-1", "coder", "SUCCESS",
			NOW, NOW.plusSeconds(10)));
		recordRepository.save(record("exec-2", "task-1", "tester", "FAILED",
			NOW.plusSeconds(20), NOW.plusSeconds(25)));
		when(taskCenterService.getTask("task-1")).thenReturn(
			Optional.of(new TaskRecord("task-1", "name", "desc", "project-a", "workspace-1")));
		when(changeService.listChanges()).thenReturn(List.of(
			change("change-1", "task-1", ChangeStatus.APPROVED),
			change("change-2", "task-1", ChangeStatus.REJECTED)));

		TaskExecutionMetrics metrics = agentMetricsService.getTaskMetrics("task-1");

		assertEquals(TaskStatus.CREATED.name(), metrics.taskStatus());
		assertEquals(2, metrics.executionCount());
		assertEquals(1, metrics.successCount());
		assertEquals(1, metrics.failedCount());
		assertEquals(15000, metrics.totalDurationMillis());
		assertEquals(7500, metrics.averageDurationMillis());
		assertEquals(2, metrics.changeCount());
		assertEquals(1, metrics.approvedChanges());
		assertEquals(1, metrics.rejectedChanges());
		assertEquals(0.5, metrics.reviewPassRate());
		assertEquals(2, metrics.executions().size());
	}

	@Test
	void shouldThrowForUnknownAgentAndTask() {
		assertThrows(ResourceNotFoundException.class,
			() -> agentMetricsService.getAgentDetail("missing"));
		assertThrows(ResourceNotFoundException.class,
			() -> agentMetricsService.getTaskMetrics("missing"));
	}

	private ExecutionRecord record(String id, String taskId, String agent, String status,
			Instant startedAt, Instant completedAt) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setExecutionId(id);
		record.setTaskId(taskId);
		record.setAgentName(agent);
		record.setStatus(status);
		record.setStartedAt(startedAt);
		record.setCompletedAt(completedAt);
		return record;
	}

	private RepairTask repair(String repairId, String taskId, int retryCount) {
		FailureContext context = new FailureContext(taskId, "workspace-1", "test-1",
			"error", "trace", "report", "", NOW);
		RepairTask repair = new RepairTask(repairId, taskId, "workspace-1", context);
		for (int i = 0; i < retryCount; i++) {
			repair.incrementRetry();
		}
		return repair;
	}

	private ChangeSet change(String changeId, String taskId, ChangeStatus status) {
		ChangeSet change = new ChangeSet(changeId, taskId, "workspace-1", "project-a", "exec-1",
			"main", "diff", "stat", 1, 1, 0, 1, 0, 0, NOW);
		if (status == ChangeStatus.APPROVED || status == ChangeStatus.REJECTED) {
			change.markReviewing();
			if (status == ChangeStatus.APPROVED) {
				change.markApproved("user-1");
			}
			else {
				change.markRejected("user-1");
			}
		}
		return change;
	}

	private AgentDefinition agent(String name) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		return definition;
	}
}
