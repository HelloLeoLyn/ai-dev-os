package com.aidevos.orchestrator.goal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.OrchestrationTaskStatus;
import com.aidevos.orchestrator.orchestrator.OrchestratorService;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanStep;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Autonomous goal management: turns a long-lived goal into milestones and
 * generated tasks. The goal is first analyzed through the dynamic planner
 * (Goal -> PlanningService), decomposed into milestones (planning /
 * implementation / verification), then tasks are generated into the
 * orchestrator task pool (Plan -> Tasks -> Orchestrator). Progress is
 * recomputed from the orchestration task outcomes, the runtime sessions and
 * the optimization recommendations (Progress Evaluation). Everything reuses
 * the existing Orchestrator, Planner, Memory, Optimization, Runtime, Audit
 * and Observability modules; the Scheduler / Worker / ExecutionEngine are
 * never touched.
 */
@Service
public class GoalManagementService {

	private final GoalRepository goalRepository;
	private final GoalMilestoneRepository milestoneRepository;
	private final GoalTaskRepository goalTaskRepository;
	private final PlanningService planningService;
	private final OrchestratorService orchestratorService;
	private final TaskCenterService taskCenterService;
	private final MemoryService memoryService;
	private final OptimizationService optimizationService;
	private final AgentRuntimeService runtimeService;
	private final AuditService auditService;
	/** taskId -> milestoneId derived when the tasks are generated. */
	private final Map<String, String> taskMilestones = new ConcurrentHashMap<>();

	@Autowired
	public GoalManagementService(GoalRepository goalRepository,
			GoalMilestoneRepository milestoneRepository, GoalTaskRepository goalTaskRepository,
			PlanningService planningService, OrchestratorService orchestratorService,
			TaskCenterService taskCenterService, MemoryService memoryService,
			OptimizationService optimizationService, AgentRuntimeService runtimeService,
			AuditService auditService) {
		this.goalRepository = goalRepository;
		this.milestoneRepository = milestoneRepository;
		this.goalTaskRepository = goalTaskRepository;
		this.planningService = planningService;
		this.orchestratorService = orchestratorService;
		this.taskCenterService = taskCenterService;
		this.memoryService = memoryService;
		this.optimizationService = optimizationService;
		this.runtimeService = runtimeService;
		this.auditService = auditService;
	}

	/**
	 * Creates a goal in the CREATED state and audits GOAL_CREATED with the
	 * priority / project metadata.
	 */
	public Goal createGoal(String projectId, String title, String description,
			GoalPriority priority) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Goal title is required");
		}
		Goal goal = new Goal("goal-" + UUID.randomUUID(), projectId, title, description,
			priority);
		goalRepository.save(goal);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("projectId", goal.getProjectId());
		metadata.put("priority", goal.getPriority().name());
		metadata.put("title", goal.getTitle());
		auditService.goalEvent(EventType.GOAL_CREATED, goal.getGoalId(),
			GoalStatus.CREATED.name(), GoalStatus.CREATED.name(),
			"Goal created: " + goal.getTitle(), Map.copyOf(metadata));
		return goal;
	}

	/**
	 * Analyzes the goal against the long-term memory and the optimization
	 * recommendations (PlanningService stage), marks it PLANNING and audits
	 * GOAL_PLANNING_STARTED. The returned memory context is used by the task
	 * generation.
	 */
	public MemoryContext analyzeGoal(String goalId) {
		Goal goal = require(goalId);
		String from = goal.getStatus().name();
		goal.markPlanning();
		MemoryContext context = analyzeContext(goal);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("similarTaskCount", context.getSimilarTasks().size());
		metadata.put("solutionCount", context.getSolutions().size());
		metadata.put("warningCount", context.getWarnings().size());
		metadata.put("recommendationCount", context.getRecommendations().size());
		auditService.goalEvent(EventType.GOAL_PLANNING_STARTED, goalId, from,
			GoalStatus.PLANNING.name(), "Goal planning started", Map.copyOf(metadata));
		return context;
	}

	/**
	 * Creates the goal milestones (planning / implementation / verification)
	 * and returns them. Idempotent: existing milestones are returned.
	 */
	public List<GoalMilestone> createMilestones(String goalId) {
		require(goalId);
		List<GoalMilestone> existing = milestoneRepository.listByGoal(goalId);
		if (!existing.isEmpty()) {
			return existing;
		}
		List<GoalMilestone> milestones = List.of(
			new GoalMilestone(goalId + "-milestone-plan", goalId, "规划",
				"目标规划与方案设计"),
			new GoalMilestone(goalId + "-milestone-implement", goalId, "实现",
				"核心功能实现"),
			new GoalMilestone(goalId + "-milestone-verify", goalId, "验证",
				"测试与验证"));
		milestones.forEach(milestoneRepository::save);
		return milestones;
	}

	/**
	 * Decomposes the goal into milestones, marks it RUNNING and audits
	 * GOAL_DECOMPOSED.
	 */
	public List<GoalMilestone> decomposeGoal(String goalId) {
		Goal goal = require(goalId);
		List<GoalMilestone> milestones = createMilestones(goalId);
		String from = goal.getStatus().name();
		goal.markRunning();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("milestoneCount", milestones.size());
		auditService.goalEvent(EventType.GOAL_DECOMPOSED, goalId, from,
			GoalStatus.RUNNING.name(), "Goal decomposed into " + milestones.size()
				+ " milestones", Map.copyOf(metadata));
		return milestones;
	}

	/**
	 * Generates the goal tasks: the goal is planned through the dynamic
	 * planner (or falls back to the default template when the planner is not
	 * wired) and the plan steps are turned into tasks that are registered
	 * with the task center and submitted to the orchestrator task pool. Each
	 * task audits GOAL_TASK_CREATED and is linked to the goal and its
	 * milestone. Idempotent: existing links are returned.
	 */
	public List<GoalTask> generateTasks(String goalId) {
		Goal goal = require(goalId);
		List<GoalTask> existing = goalTaskRepository.listByGoal(goalId);
		if (!existing.isEmpty()) {
			return existing;
		}
		MemoryContext context = analyzeContext(goal);
		List<PlanStep> steps = planSteps(goal);
		List<TaskSpec> specs = taskSpecs(goal, context, steps);
		List<GoalTask> created = new ArrayList<>();
		for (TaskSpec spec : specs) {
			TaskRecord record = new TaskRecord(spec.taskId(), spec.name(), spec.description(),
				goal.getProjectId());
			if (taskCenterService != null) {
				taskCenterService.registerTask(record);
			}
			orchestratorService.submitTask(spec.taskId(), spec.taskType(),
				priorityOf(goal), spec.requiredAgents());
			GoalTask link = new GoalTask(goalId, spec.taskId(), "SUB_TASK");
			goalTaskRepository.save(link);
			taskMilestones.put(spec.taskId(), spec.milestoneId());
			created.add(link);
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("taskId", spec.taskId());
			metadata.put("taskType", spec.taskType());
			metadata.put("milestoneId", spec.milestoneId());
			metadata.put("priority", goal.getPriority().name());
			auditService.goalEvent(EventType.GOAL_TASK_CREATED, goalId,
				goal.getStatus().name(), goal.getStatus().name(),
				"Task generated: " + spec.name(), Map.copyOf(metadata));
		}
		return created;
	}

	/**
	 * Recomputes the goal progress from the orchestration task outcomes,
	 * updates the milestone progress and audits GOAL_PROGRESS_UPDATED.
	 */
	public double updateProgress(String goalId) {
		Goal goal = require(goalId);
		String from = goal.getStatus().name();
		List<GoalTask> links = goalTaskRepository.listByGoal(goalId);
		List<OrchestrationTask> tasks = orchestrationTasks(links);
		long total = tasks.size();
		long completed = tasks.stream()
			.filter(task -> task.getStatus() == OrchestrationTaskStatus.COMPLETED)
			.count();
		long failed = tasks.stream()
			.filter(task -> task.getStatus() == OrchestrationTaskStatus.FAILED)
			.count();
		double progress = total == 0 ? 0.0 : completed * 100.0 / total;
		goal.setProgress(progress);
		if (goal.getStatus() == GoalStatus.CREATED
			|| goal.getStatus() == GoalStatus.PLANNING) {
			goal.markRunning();
		}
		updateMilestoneProgress(goalId);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("completed", completed);
		metadata.put("total", total);
		metadata.put("failed", failed);
		metadata.put("progress", progress);
		auditService.goalEvent(EventType.GOAL_PROGRESS_UPDATED, goalId, from,
			goal.getStatus().name(), "Goal progress updated to " + Math.round(progress)
				+ "%", Map.copyOf(metadata));
		return progress;
	}

	/**
	 * Evaluates the goal completion: recomputes the progress and produces a
	 * GoalEvaluation (completed / total / progress / remaining work /
	 * confidence). When every generated task is completed the goal is marked
	 * COMPLETED (GOAL_COMPLETED); when any task failed it is marked FAILED
	 * (GOAL_FAILED).
	 */
	public GoalEvaluation evaluateCompletion(String goalId) {
		Goal goal = require(goalId);
		double progress = updateProgress(goalId);
		List<GoalTask> links = goalTaskRepository.listByGoal(goalId);
		List<OrchestrationTask> tasks = orchestrationTasks(links);
		long total = tasks.size();
		long completed = tasks.stream()
			.filter(task -> task.getStatus() == OrchestrationTaskStatus.COMPLETED)
			.count();
		long failed = tasks.stream()
			.filter(task -> task.getStatus() == OrchestrationTaskStatus.FAILED)
			.count();
		int remainingWork = (int) (total - completed);
		double confidence = confidence(links, total, completed);
		GoalEvaluation evaluation = new GoalEvaluation(goalId, (int) completed, (int) total,
			progress, remainingWork, confidence);
		String from = goal.getStatus().name();
		if (total > 0 && completed == total) {
			goal.markCompleted();
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("completed", completed);
			metadata.put("total", total);
			metadata.put("progress", 100.0);
			auditService.goalEvent(EventType.GOAL_COMPLETED, goalId, from,
				GoalStatus.COMPLETED.name(), "Goal completed", Map.copyOf(metadata));
		}
		else if (failed > 0) {
			goal.markFailed();
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("completed", completed);
			metadata.put("failed", failed);
			metadata.put("total", total);
			auditService.goalEvent(EventType.GOAL_FAILED, goalId, from,
				GoalStatus.FAILED.name(), "Goal failed", Map.copyOf(metadata));
		}
		return evaluation;
	}

	public Optional<Goal> getGoal(String goalId) {
		if (goalId == null || goalId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(goalRepository.get(goalId));
	}

	public List<GoalMilestone> getMilestones(String goalId) {
		return require(goalId) == null ? List.of() : milestoneRepository.listByGoal(goalId);
	}

	public List<GoalTask> getTasks(String goalId) {
		return require(goalId) == null ? List.of() : goalTaskRepository.listByGoal(goalId);
	}

	public GoalEvaluation getEvaluation(String goalId) {
		return evaluateCompletion(goalId);
	}

	/** The goal id a generated task belongs to, if any. */
	public Optional<String> goalIdForTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		GoalTask link = goalTaskRepository.findByTaskId(taskId);
		return link == null ? Optional.empty() : Optional.of(link.getGoalId());
	}

	/** The milestone a generated task was assigned to, if any. */
	public Optional<String> milestoneIdForTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(taskMilestones.get(taskId));
	}

	private Goal require(String goalId) {
		if (goalId == null || goalId.isBlank()) {
			throw new IllegalArgumentException("Goal id is required");
		}
		Goal goal = goalRepository.get(goalId);
		if (goal == null) {
			throw new IllegalArgumentException("Goal not found: " + goalId);
		}
		return goal;
	}

	private MemoryContext analyzeContext(Goal goal) {
		String query = goal.getDescription() == null || goal.getDescription().isBlank()
			? goal.getTitle() : goal.getDescription();
		List<MemoryMatch> similar = memoryService == null ? List.of()
			: memoryService.findSimilarTasks(goal.getProjectId(), query, 5);
		List<MemoryMatch> solutions = memoryService == null ? List.of()
			: memoryService.findSolutions(goal.getProjectId(), query, 5);
		List<OptimizationRecord> recommendations = optimizationService == null ? List.of()
			: optimizationService.getRecommendations(goal.getGoalId());
		List<String> warnings = recommendations.stream()
			.filter(record -> record.getType() == OptimizationType.FAILURE_PATTERN)
			.map(OptimizationRecord::getRecommendation)
			.toList();
		List<String> recommendationText = recommendations.stream()
			.map(OptimizationRecord::getRecommendation)
			.toList();
		return new MemoryContext(similar, solutions, warnings, recommendationText);
	}

	/** Plans the goal through the dynamic planner so its steps shape the
	 * generated tasks. Falls back to an empty step list when the planner is
	 * not wired. */
	private List<PlanStep> planSteps(Goal goal) {
		if (planningService == null) {
			return List.of();
		}
		OrchestrationTask orchestrationTask = new OrchestrationTask(goal.getGoalId(),
			"CODE_GENERATION", priorityOf(goal), List.of());
		Plan plan = planningService.createPlan(orchestrationTask);
		if (plan == null) {
			return List.of();
		}
		Plan evaluated = planningService.evaluatePlan(plan);
		return planningService.optimizePlan(evaluated).steps();
	}

	private List<TaskSpec> taskSpecs(Goal goal, MemoryContext context, List<PlanStep> steps) {
		String subject = goal.getTitle() == null || goal.getTitle().isBlank()
			? goal.getGoalId() : goal.getTitle();
		String plan = goal.getGoalId() + "-milestone-plan";
		String implement = goal.getGoalId() + "-milestone-implement";
		String verify = goal.getGoalId() + "-milestone-verify";
		List<TaskSpec> specs = new ArrayList<>();
		boolean backendAdded = false;
		boolean frontendAdded = false;
		boolean verifyAdded = false;
		boolean repairAdded = false;
		int taskNumber = 1;
		for (PlanStep step : steps) {
			AgentType agentType = step.agentType();
			if (agentType == AgentType.HERMES) {
				specs.add(new TaskSpec(goal.getGoalId() + "-task-" + (taskNumber++),
					"设计 " + subject, "数据库设计与模块结构", "TASK_ANALYSIS", plan,
					List.of("hermes")));
			}
			else if (agentType == AgentType.CODEX) {
				if (!backendAdded) {
					specs.add(new TaskSpec(goal.getGoalId() + "-task-" + (taskNumber++),
						"实现 " + subject + " 后端 API", "后端接口与核心逻辑", "CODE_GENERATION",
						implement, List.of("codex")));
					backendAdded = true;
				}
				if (!frontendAdded) {
					specs.add(new TaskSpec(goal.getGoalId() + "-task-" + (taskNumber++),
						"实现 " + subject + " 前端页面", "前端页面与交互", "CODE_GENERATION",
						implement, List.of("codex")));
					frontendAdded = true;
				}
			}
			else if (agentType == AgentType.TEST_AGENT && !verifyAdded) {
				specs.add(new TaskSpec(goal.getGoalId() + "-task-" + (taskNumber++),
					"验证 " + subject, "编写并运行测试", "TEST_VERIFY", verify,
					List.of("test-agent")));
				verifyAdded = true;
			}
			else if (agentType == AgentType.REPAIR_AGENT && !repairAdded) {
				specs.add(new TaskSpec(goal.getGoalId() + "-task-" + (taskNumber++),
					"修复 " + subject + " 已知问题", "根据历史失败模式修复问题", "REPAIR_TASK",
					implement, List.of("repair-agent")));
				repairAdded = true;
			}
		}
		if (specs.isEmpty()) {
			specs.add(new TaskSpec(goal.getGoalId() + "-task-1", "设计 " + subject,
				"数据库设计与模块结构", "TASK_ANALYSIS", plan, List.of("hermes")));
			specs.add(new TaskSpec(goal.getGoalId() + "-task-2", "实现 " + subject
				+ " 后端 API", "后端接口与核心逻辑", "CODE_GENERATION", implement,
				List.of("codex")));
			specs.add(new TaskSpec(goal.getGoalId() + "-task-3", "实现 " + subject
				+ " 前端页面", "前端页面与交互", "CODE_GENERATION", implement,
				List.of("codex")));
			specs.add(new TaskSpec(goal.getGoalId() + "-task-4", "验证 " + subject,
				"编写并运行测试", "TEST_VERIFY", verify, List.of("test-agent")));
		}
		if (!context.getWarnings().isEmpty() && !repairAdded) {
			specs.add(new TaskSpec(goal.getGoalId() + "-task-" + (taskNumber++),
				"修复 " + subject + " 已知问题", "根据历史失败模式修复问题", "REPAIR_TASK",
				implement, List.of("repair-agent")));
		}
		return specs;
	}

	private List<OrchestrationTask> orchestrationTasks(List<GoalTask> links) {
		List<OrchestrationTask> tasks = new ArrayList<>();
		for (GoalTask link : links) {
			if (orchestratorService != null) {
				orchestratorService.getTask(link.getTaskId())
					.ifPresent(tasks::add);
			}
		}
		return tasks;
	}

	private void updateMilestoneProgress(String goalId) {
		for (GoalMilestone milestone : milestoneRepository.listByGoal(goalId)) {
			List<GoalTask> links = goalTaskRepository.listByGoal(goalId).stream()
				.filter(link -> milestone.getMilestoneId()
					.equals(taskMilestones.get(link.getTaskId())))
				.toList();
			long total = links.size();
			long done = 0;
			for (GoalTask link : links) {
				OrchestrationTask task = orchestratorService == null ? null
					: orchestratorService.getTask(link.getTaskId()).orElse(null);
				if (task != null && task.getStatus() == OrchestrationTaskStatus.COMPLETED) {
					done++;
				}
				else if (task != null
					&& task.getStatus() == OrchestrationTaskStatus.FAILED) {
					milestone.markFailed();
				}
			}
			milestone.setProgress(total == 0 ? 0.0 : done * 100.0 / total);
		}
	}

	private double confidence(List<GoalTask> links, long total, long completed) {
		if (total == 0) {
			return 0.5;
		}
		long sessionCompleted = links.stream()
			.filter(link -> runtimeService != null
				&& runtimeService.sessionsForTask(link.getTaskId()).stream()
					.anyMatch(session -> session.getStatus() == AgentSessionStatus.COMPLETED))
			.count();
		double base = 0.3 + completed * 0.2 / total + sessionCompleted * 0.5 / total;
		return Math.max(0.0, Math.min(1.0, base));
	}

	private TaskPriority priorityOf(Goal goal) {
		return switch (goal.getPriority() == null ? GoalPriority.NORMAL : goal.getPriority()) {
			case LOW -> TaskPriority.LOW;
			case NORMAL -> TaskPriority.NORMAL;
			case HIGH -> TaskPriority.HIGH;
			case CRITICAL -> TaskPriority.CRITICAL;
		};
	}

	private record TaskSpec(String taskId, String name, String description, String taskType,
			String milestoneId, List<String> requiredAgents) {
	}
}
