package com.aidevos.orchestrator.metrics.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.repair.RepairTask;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Service;

/**
 * Agent observability: aggregates per-agent and per-task execution metrics
 * from the existing ExecutionRecords, audit events (REPAIR_STARTED), repair
 * tasks and change sets. Read-only, computed on demand; no business data is
 * duplicated and no external metrics dependency is introduced.
 */
@Service
public class AgentMetricsService {

	private final ExecutionRecordManager executionRecordManager;
	private final AgentManager agentManager;
	private final AuditService auditService;
	private final RepairCoordinator repairCoordinator;
	private final ChangeService changeService;
	private final TaskCenterService taskCenterService;

	public AgentMetricsService(ExecutionRecordManager executionRecordManager,
			AgentManager agentManager, AuditService auditService,
			RepairCoordinator repairCoordinator, ChangeService changeService,
			TaskCenterService taskCenterService) {
		this.executionRecordManager = executionRecordManager;
		this.agentManager = agentManager;
		this.auditService = auditService;
		this.repairCoordinator = repairCoordinator;
		this.changeService = changeService;
		this.taskCenterService = taskCenterService;
	}

	/** Agent ranking by execution count (descending). */
	public List<AgentMetrics> listAgentMetrics() {
		return aggregate(executionRecordManager.getAll());
	}

	/**
	 * Agent ranking restricted to the executions of one project. Records are
	 * filtered through the task's projectId so projects never mix metrics.
	 */
	public List<AgentMetrics> listProjectAgentMetrics(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			return List.of();
		}
		List<ExecutionRecord> records = executionRecordManager.getAll().stream()
			.filter(record -> belongsToProject(record, projectId))
			.toList();
		return aggregate(records);
	}

	private boolean belongsToProject(ExecutionRecord record, String projectId) {
		return record.getTaskId() != null && !record.getTaskId().isBlank()
			&& taskCenterService.getTask(record.getTaskId())
				.map(task -> projectId.equals(task.getProjectId()))
				.orElse(false);
	}

	private List<AgentMetrics> aggregate(List<ExecutionRecord> records) {
		Map<String, AgentAggregate> aggregates = seedAgents();
		Map<String, String> taskAgent = taskToAgent(records);
		for (ExecutionRecord record : records) {
			aggregate(agentName(record), aggregates).add(record);
		}
		applyRepairCounts(aggregates, taskAgent);
		applyRetryCounts(aggregates, taskAgent);
		applyChangeCounts(aggregates, taskAgent);
		List<AgentMetrics> result = new ArrayList<>();
		for (AgentAggregate aggregate : aggregates.values()) {
			result.add(aggregate.toMetrics());
		}
		result.sort(Comparator.comparingInt(AgentMetrics::taskCount).reversed()
			.thenComparing(AgentMetrics::agentName));
		return result;
	}

	public AgentMetricsDetail getAgentDetail(String agentId) {
		if (agentId == null || agentId.isBlank()) {
			throw new ResourceNotFoundException("Agent", agentId);
		}
		AgentMetrics metrics = listAgentMetrics().stream()
			.filter(candidate -> agentId.equals(candidate.agentId()))
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Agent", agentId));
		List<AgentExecutionMetric> executions = executionRecordManager.getAll().stream()
			.filter(record -> agentId.equals(agentName(record)))
			.sorted(Comparator.comparing(this::executedAt,
				Comparator.nullsLast(Comparator.naturalOrder())).reversed())
			.map(this::executionMetric)
			.toList();
		return new AgentMetricsDetail(metrics, executions);
	}

	public TaskExecutionMetrics getTaskMetrics(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			throw new ResourceNotFoundException("Task", taskId);
		}
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
		List<ExecutionRecord> records = executionRecordManager.getAll().stream()
			.filter(record -> taskId.equals(record.getTaskId()))
			.toList();
		List<AgentExecutionMetric> executions = records.stream()
			.sorted(Comparator.comparing(this::executedAt,
				Comparator.nullsLast(Comparator.naturalOrder())).reversed())
			.map(this::executionMetric)
			.toList();
		long total = records.stream().mapToLong(this::durationMillis).sum();
		int measurable = (int) records.stream().filter(this::hasDuration).count();
		long average = measurable == 0 ? 0 : total / measurable;
		int success = (int) records.stream().filter(this::isSuccess).count();
		int failed = (int) records.stream().filter(this::isFailed).count();
		int repairCount = repairEvents().stream()
			.filter(event -> taskId.equals(event.taskId()))
			.toList().size();
		int retryCount = repairCoordinator.listRepairs().stream()
			.filter(repair -> taskId.equals(repair.getTaskId()))
			.mapToInt(RepairTask::getRetryCount)
			.sum();
		List<ChangeSet> changes = changeService.listChanges().stream()
			.filter(change -> taskId.equals(change.getTaskId()))
			.toList();
		int approved = (int) changes.stream()
			.filter(change -> change.getStatus() == ChangeStatus.APPROVED).count();
		int rejected = (int) changes.stream()
			.filter(change -> change.getStatus() == ChangeStatus.REJECTED).count();
		double passRate = approved + rejected == 0 ? 0.0
			: (double) approved / (approved + rejected);
		return new TaskExecutionMetrics(taskId, task.getStatus().name(), records.size(),
			success, failed, total, average, repairCount, retryCount, changes.size(),
			approved, rejected, passRate, executions);
	}

	private Map<String, AgentAggregate> seedAgents() {
		Map<String, AgentAggregate> aggregates = new LinkedHashMap<>();
		for (AgentDefinition agent : agentManager.getAllAgents()) {
			aggregates.computeIfAbsent(agent.getName(), AgentAggregate::new);
		}
		return aggregates;
	}

	private AgentAggregate aggregate(String name, Map<String, AgentAggregate> aggregates) {
		return aggregates.computeIfAbsent(name, AgentAggregate::new);
	}

	private Map<String, String> taskToAgent(List<ExecutionRecord> records) {
		Map<String, String> taskAgent = new HashMap<>();
		for (ExecutionRecord record : records) {
			if (record.getTaskId() != null && !record.getTaskId().isBlank()) {
				taskAgent.putIfAbsent(record.getTaskId(), agentName(record));
			}
		}
		return taskAgent;
	}

	private void applyRepairCounts(Map<String, AgentAggregate> aggregates,
			Map<String, String> taskAgent) {
		for (EventRecord event : repairEvents()) {
			String agent = taskAgent.get(event.taskId());
			if (agent != null) {
				aggregates.computeIfAbsent(agent, AgentAggregate::new).repairCount++;
			}
		}
	}

	private void applyRetryCounts(Map<String, AgentAggregate> aggregates,
			Map<String, String> taskAgent) {
		for (RepairTask repair : repairCoordinator.listRepairs()) {
			String agent = taskAgent.get(repair.getTaskId());
			if (agent != null) {
				aggregates.computeIfAbsent(agent, AgentAggregate::new).retryCount
					+= repair.getRetryCount();
			}
		}
	}

	private void applyChangeCounts(Map<String, AgentAggregate> aggregates,
			Map<String, String> taskAgent) {
		for (ChangeSet change : changeService.listChanges()) {
			String agent = taskAgent.get(change.getTaskId());
			if (agent != null) {
				aggregates.computeIfAbsent(agent, AgentAggregate::new).changeCount++;
			}
		}
	}

	private List<EventRecord> repairEvents() {
		return auditService.query(new EventQuery(null, null, null, null, null, null, null,
			null, null, null, Set.of(EventType.REPAIR_STARTED), null, null, 0,
			EventQuery.MAX_LIMIT));
	}

	private AgentExecutionMetric executionMetric(ExecutionRecord record) {
		return new AgentExecutionMetric(record.getTaskId(), agentName(record),
			record.getExecutionId() == null ? record.getId() : record.getExecutionId(),
			durationMillis(record), record.getStatus(), executedAt(record));
	}

	private long durationMillis(ExecutionRecord record) {
		if (!hasDuration(record)) {
			return 0;
		}
		return Duration.between(record.getStartedAt(), record.getCompletedAt()).toMillis();
	}

	private boolean hasDuration(ExecutionRecord record) {
		return record.getStartedAt() != null && record.getCompletedAt() != null
			&& !record.getCompletedAt().isBefore(record.getStartedAt());
	}

	private Instant executedAt(ExecutionRecord record) {
		if (record.getCompletedAt() != null) {
			return record.getCompletedAt();
		}
		return record.getStartedAt();
	}

	private boolean isSuccess(ExecutionRecord record) {
		return "SUCCESS".equalsIgnoreCase(record.getStatus());
	}

	private boolean isFailed(ExecutionRecord record) {
		return "FAILED".equalsIgnoreCase(record.getStatus());
	}

	private String agentName(ExecutionRecord record) {
		return record.getAgentName() == null ? "unknown" : record.getAgentName();
	}

	private static final class AgentAggregate {

		private final String name;
		private int taskCount;
		private int successCount;
		private int failedCount;
		private int retryCount;
		private long totalDuration;
		private int measurableDurationCount;
		private Instant lastExecutedAt;
		private int repairCount;
		private int changeCount;

		private AgentAggregate(String name) {
			this.name = name;
		}

		private void add(ExecutionRecord record) {
			taskCount++;
			if ("SUCCESS".equalsIgnoreCase(record.getStatus())) {
				successCount++;
			}
			else if ("FAILED".equalsIgnoreCase(record.getStatus())) {
				failedCount++;
			}
			if (record.getStartedAt() != null && record.getCompletedAt() != null
					&& !record.getCompletedAt().isBefore(record.getStartedAt())) {
				totalDuration += Duration.between(record.getStartedAt(),
					record.getCompletedAt()).toMillis();
				measurableDurationCount++;
			}
			Instant executedAt = record.getCompletedAt() != null
				? record.getCompletedAt() : record.getStartedAt();
			if (executedAt != null
					&& (lastExecutedAt == null || executedAt.isAfter(lastExecutedAt))) {
				lastExecutedAt = executedAt;
			}
		}

		private AgentMetrics toMetrics() {
			long average = measurableDurationCount == 0 ? 0
				: totalDuration / measurableDurationCount;
			return new AgentMetrics(name, name, taskCount, successCount, failedCount,
				retryCount, average, lastExecutedAt, repairCount, changeCount);
		}
	}
}
