package com.aidevos.orchestrator.observability;

import java.time.Duration;
import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.metrics.agent.TaskExecutionMetrics;
import com.aidevos.orchestrator.metrics.tool.ToolMetricsService;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.observability.usage.UsageSummary;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.timeline.TimelineService;
import org.springframework.stereotype.Service;

/**
 * Internal observability aggregation for tasks, projects and agents. All data
 * is derived on demand from the existing traces, execution records, audit
 * timeline and usage records; nothing is stored twice.
 */
@Service
public class ObservabilityService {

	private final TaskCenterService taskCenterService;
	private final ExecutionRecordManager executionRecordManager;
	private final AgentMetricsService agentMetricsService;
	private final ExecutionTraceService traceService;
	private final UsageService usageService;
	private final ToolMetricsService toolMetricsService;
	private final TimelineService timelineService;

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService) {
		this.taskCenterService = taskCenterService;
		this.executionRecordManager = executionRecordManager;
		this.agentMetricsService = agentMetricsService;
		this.traceService = traceService;
		this.usageService = usageService;
		this.toolMetricsService = toolMetricsService;
		this.timelineService = timelineService;
	}

	public TaskObservability taskObservability(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
		List<TraceRecord> traces = traceService.listByTask(taskId);
		List<TraceRecord> toolTraces = traces.stream()
			.filter(trace -> trace.getToolId() != null)
			.toList();
		TaskExecutionMetrics agent = agentMetricsService.getTaskMetrics(taskId);
		UsageSummary usage = usageService.getTaskUsage(taskId);
		return new TaskObservability(taskId, task.getStatus().name(),
			timelineService.timeline(taskId), traces, agent, toolTraces, usage);
	}

	public ProjectObservability projectObservability(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("Project id is required");
		}
		List<TaskRecord> tasks = taskCenterService.listTasks().stream()
			.filter(task -> projectId.equals(task.getProjectId()))
			.toList();
		int success = 0;
		int failed = 0;
		for (TaskRecord task : tasks) {
			if (task.getStatus() == TaskStatus.COMPLETED
				|| task.getStatus() == TaskStatus.SUCCESS) {
				success++;
			}
			else if (task.getStatus() == TaskStatus.FAILED) {
				failed++;
			}
		}
		long average = averageDuration(tasks);
		UsageSummary usage = usageService.getProjectUsage(projectId);
		double successRate = tasks.isEmpty() ? 0.0
			: (double) success / tasks.size();
		double failureRate = tasks.isEmpty() ? 0.0
			: (double) failed / tasks.size();
		return new ProjectObservability(projectId, tasks.size(), success, failed,
			successRate, failureRate, average, usage.totalTokens(), usage.estimatedCost());
	}

	public AgentObservability agentObservability(String agentType) {
		if (agentType == null || agentType.isBlank()) {
			throw new IllegalArgumentException("Agent type is required");
		}
		List<ExecutionRecord> records = executionRecordManager.getAll().stream()
			.filter(record -> agentType.equals(record.getAgentName()))
			.toList();
		int success = 0;
		int failed = 0;
		long totalDuration = 0;
		int measurable = 0;
		for (ExecutionRecord record : records) {
			if ("SUCCESS".equalsIgnoreCase(record.getStatus())) {
				success++;
			}
			else if ("FAILED".equalsIgnoreCase(record.getStatus())) {
				failed++;
			}
			if (record.getStartedAt() != null && record.getCompletedAt() != null
					&& !record.getCompletedAt().isBefore(record.getStartedAt())) {
				totalDuration += Duration.between(record.getStartedAt(),
					record.getCompletedAt()).toMillis();
				measurable++;
			}
		}
		long average = measurable == 0 ? 0 : totalDuration / measurable;
		UsageSummary usage = usageService.getAgentUsage(agentType);
		double successRate = records.isEmpty() ? 0.0
			: (double) success / records.size();
		return new AgentObservability(agentType, records.size(), success, failed,
			successRate, average, usage.totalTokens(), usage.estimatedCost());
	}

	private long averageDuration(List<TaskRecord> tasks) {
		long total = 0;
		int measurable = 0;
		for (TaskRecord task : tasks) {
			for (ExecutionRecord record : executionRecordManager.getAll()) {
				if (record.getTaskId() == null || !record.getTaskId().equals(task.getTaskId())
						|| record.getStartedAt() == null || record.getCompletedAt() == null
						|| record.getCompletedAt().isBefore(record.getStartedAt())) {
					continue;
				}
				total += Duration.between(record.getStartedAt(),
					record.getCompletedAt()).toMillis();
				measurable++;
			}
		}
		return measurable == 0 ? 0 : total / measurable;
	}
}
