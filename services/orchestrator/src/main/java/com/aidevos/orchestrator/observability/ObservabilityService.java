package com.aidevos.orchestrator.observability;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.adaptive.AdaptiveExecutionService;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.goal.Goal;
import com.aidevos.orchestrator.goal.GoalEvaluation;
import com.aidevos.orchestrator.goal.GoalManagementService;
import com.aidevos.orchestrator.goal.GoalMilestone;
import com.aidevos.orchestrator.goal.GoalTask;
import com.aidevos.orchestrator.collaboration.AgentMessage;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanCollaborationService;
import com.aidevos.orchestrator.human.HumanFeedback;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.metrics.agent.TaskExecutionMetrics;
import com.aidevos.orchestrator.metrics.tool.ToolMetricsService;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.observability.usage.UsageSummary;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.OrchestratorService;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.timeline.TimelineService;
import org.springframework.beans.factory.annotation.Autowired;
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
	private final AgentRuntimeService runtimeService;
	private final AgentCollaborationService collaborationService;
	private final HumanCollaborationService humanCollaborationService;
	private final OptimizationService optimizationService;
	private final OrchestratorService orchestratorService;
	private final PlanningService planningService;
	private final AdaptiveExecutionService adaptiveService;
	private final GoalManagementService goalManagementService;

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService,
			collaborationService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService,
			HumanCollaborationService humanCollaborationService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService,
			collaborationService, humanCollaborationService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService,
			HumanCollaborationService humanCollaborationService,
			OptimizationService optimizationService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService,
			collaborationService, humanCollaborationService, optimizationService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService,
			HumanCollaborationService humanCollaborationService,
			OptimizationService optimizationService,
			OrchestratorService orchestratorService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService,
			collaborationService, humanCollaborationService, optimizationService,
			orchestratorService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService,
			HumanCollaborationService humanCollaborationService,
			OptimizationService optimizationService,
			OrchestratorService orchestratorService,
			PlanningService planningService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService,
			collaborationService, humanCollaborationService, optimizationService,
			orchestratorService, planningService, null);
	}

	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService,
			HumanCollaborationService humanCollaborationService,
			OptimizationService optimizationService,
			OrchestratorService orchestratorService,
			PlanningService planningService,
			AdaptiveExecutionService adaptiveService) {
		this(taskCenterService, executionRecordManager, agentMetricsService, traceService,
			usageService, toolMetricsService, timelineService, runtimeService,
			collaborationService, humanCollaborationService, optimizationService,
			orchestratorService, planningService, adaptiveService, null);
	}

	@Autowired
	public ObservabilityService(TaskCenterService taskCenterService,
			ExecutionRecordManager executionRecordManager,
			AgentMetricsService agentMetricsService, ExecutionTraceService traceService,
			UsageService usageService, ToolMetricsService toolMetricsService,
			TimelineService timelineService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService,
			HumanCollaborationService humanCollaborationService,
			OptimizationService optimizationService,
			OrchestratorService orchestratorService,
			PlanningService planningService,
			AdaptiveExecutionService adaptiveService,
			GoalManagementService goalManagementService) {
		this.taskCenterService = taskCenterService;
		this.executionRecordManager = executionRecordManager;
		this.agentMetricsService = agentMetricsService;
		this.traceService = traceService;
		this.usageService = usageService;
		this.toolMetricsService = toolMetricsService;
		this.timelineService = timelineService;
		this.runtimeService = runtimeService;
		this.collaborationService = collaborationService;
		this.humanCollaborationService = humanCollaborationService;
		this.optimizationService = optimizationService;
		this.orchestratorService = orchestratorService;
		this.planningService = planningService;
		this.adaptiveService = adaptiveService;
		this.goalManagementService = goalManagementService;
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
		List<AgentSession> sessions = runtimeService == null
			? List.of() : runtimeService.sessionsForTask(taskId);
		String teamId = null;
		List<String> agents = List.of();
		List<AgentMessage> messages = List.of();
		List<String> handoffs = List.of();
		if (collaborationService != null) {
			Optional<AgentTeam> team = collaborationService.teamForTask(taskId);
			if (team.isPresent()) {
				teamId = team.get().getTeamId();
				agents = team.get().getAgents();
				messages = collaborationService.messages(teamId);
				handoffs = collaborationService.handoffs(teamId);
			}
		}
		List<HumanApproval> approvals = humanCollaborationService == null
			? List.of() : humanCollaborationService.getTaskApprovals(taskId);
		List<HumanFeedback> feedbacks = humanCollaborationService == null
			? List.of() : humanCollaborationService.getFeedbacks(taskId);
		List<OptimizationRecord> optimizations = optimizationService == null
			? List.of() : optimizationService.getRecommendations(taskId);
		List<String> recommendations = optimizations.stream()
			.map(OptimizationRecord::getRecommendation)
			.toList();
		String priority = null;
		List<String> assignedAgents = List.of();
		String orchestrationStatus = null;
		if (orchestratorService != null) {
			java.util.Optional<OrchestrationTask> orchestrated =
				orchestratorService.getTask(taskId);
			if (orchestrated.isPresent()) {
				OrchestrationTask orchestrationTask = orchestrated.get();
				priority = orchestrationTask.getPriority() == null ? null
					: orchestrationTask.getPriority().name();
				assignedAgents = orchestrationTask.getAssignedAgents();
				orchestrationStatus = orchestrationTask.getStatus() == null ? null
					: orchestrationTask.getStatus().name();
			}
		}
		String planId = null;
		String riskLevel = null;
		Double estimatedCost = null;
		if (planningService != null) {
			java.util.Optional<Plan> planned = planningService.getPlanByTaskId(taskId);
			if (planned.isPresent()) {
				Plan plan = planned.get();
				planId = plan.planId();
				riskLevel = plan.riskLevel() == null ? null : plan.riskLevel().name();
				estimatedCost = plan.estimatedCost();
			}
		}
		java.util.List<com.aidevos.orchestrator.adaptive.ExecutionFeedback> feedback =
			adaptiveService == null ? java.util.List.of()
				: adaptiveService.feedbacksForTask(taskId);
		java.util.List<com.aidevos.orchestrator.adaptive.AdaptationDecision> adaptations =
			adaptiveService == null ? java.util.List.of()
				: adaptiveService.decisionsForTask(taskId);
		java.util.List<String> replans = adaptiveService == null
			? java.util.List.of() : adaptiveService.replansForTask(taskId);
		String goalId = goalManagementService == null ? null
			: goalManagementService.goalIdForTask(taskId).orElse(null);
		String milestoneId = goalManagementService == null ? null
			: goalManagementService.milestoneIdForTask(taskId).orElse(null);
		return new TaskObservability(taskId, task.getStatus().name(),
			timelineService.timeline(taskId), traces, agent, toolTraces, usage, sessions,
			teamId, agents, messages, handoffs, approvals, feedbacks, optimizations,
			recommendations, priority, assignedAgents, orchestrationStatus, planId,
			riskLevel, estimatedCost, feedback, adaptations, replans, goalId,
			milestoneId);
	}

	/**
	 * Goal-level observability: the goal status and progress with its
	 * milestones, generated tasks and completion evaluation. Used by the
	 * observability API for the Goal -> Milestone -> Task -> Runtime ->
	 * Result -> Progress timeline.
	 */
	public GoalObservability goalObservability(String goalId) {
		if (goalId == null || goalId.isBlank()) {
			throw new IllegalArgumentException("Goal id is required");
		}
		if (goalManagementService == null) {
			throw new IllegalStateException("Goal management is not available");
		}
		Goal goal = goalManagementService.getGoal(goalId)
			.orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));
		List<GoalMilestone> milestones = goalManagementService.getMilestones(goalId);
		List<GoalTask> tasks = goalManagementService.getTasks(goalId);
		GoalEvaluation evaluation = goalManagementService.getEvaluation(goalId);
		return new GoalObservability(goal.getGoalId(), goal.getStatus().name(),
			goal.getProgress(), milestones, tasks, evaluation);
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
