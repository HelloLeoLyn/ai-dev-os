package com.aidevos.orchestrator.planner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.search.MemoryMatch;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestrator.OrchestrationTask;
import com.aidevos.orchestrator.orchestrator.TaskPriority;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Dynamic planning engine. Before an orchestrated task starts, the planner
 * creates an execution plan (goal, steps, agents, estimated cost, risk),
 * analyzes the task against the long-term memory and the optimization
 * recommendations, evaluates the plan into a score, optimizes it (repair
 * agent / agent replacement / tool suggestions are applied to the plan only)
 * and converts the final plan into an execution graph. Everything reuses the
 * existing MemoryService, OptimizationService, AgentOptimizationService,
 * AgentSelector, ExecutionGraphBuilder and AuditService; the plan is a
 * suggestion layer and never rewrites an existing graph.
 */
@Service
public class PlanningService {

	/** Maximum number of similar historical tasks taken into account. */
	static final int SIMILAR_TASK_LIMIT = 5;

	/** Maximum number of known solutions taken into account. */
	static final int SOLUTION_LIMIT = 5;

	private final TaskCenterService taskCenterService;
	private final MemoryService memoryService;
	private final OptimizationService optimizationService;
	private final AgentOptimizationService agentOptimizationService;
	private final AgentSelector agentSelector;
	private final ExecutionGraphBuilder graphBuilder;
	private final AuditService auditService;
	private final Map<String, Plan> plans = new ConcurrentHashMap<>();
	private final Map<String, TaskAnalysis> analyses = new ConcurrentHashMap<>();

	@Autowired
	public PlanningService(TaskCenterService taskCenterService, MemoryService memoryService,
			OptimizationService optimizationService,
			AgentOptimizationService agentOptimizationService, AgentSelector agentSelector,
			ExecutionGraphBuilder graphBuilder, AuditService auditService) {
		this.taskCenterService = taskCenterService;
		this.memoryService = memoryService;
		this.optimizationService = optimizationService;
		this.agentOptimizationService = agentOptimizationService;
		this.agentSelector = agentSelector;
		this.graphBuilder = graphBuilder;
		this.auditService = auditService;
	}

	/**
	 * Creates an execution plan for an orchestrated task: resolves the task
	 * record for the goal and memory analysis, derives the base steps from
	 * the task category, estimates the cost and the initial risk and audits
	 * PLAN_CREATED. The plan is stored so it can be evaluated, optimized and
	 * converted to a graph later.
	 */
	public Plan createPlan(OrchestrationTask task) {
		if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
			throw new IllegalArgumentException("Task is required");
		}
		TaskRecord record = taskCenterService.getTask(task.getTaskId()).orElse(null);
		return createAndStore(task, goalOf(record, task), baseSteps(task.getTaskType()));
	}

	/**
	 * Re-plans a task from its current execution steps (used by the adaptive
	 * executor when it decides to REPLAN): a fresh plan is created with the
	 * same topology, then evaluated and optimized again against the current
	 * memory and optimization recommendations so the new plan can carry the
	 * learned failure patterns. The plan is audited as PLAN_CREATED like any
	 * other plan; the adaptive service adds GRAPH_REPLANNED on top.
	 */
	public Plan replan(String taskId, List<PlanStep> currentSteps) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		OrchestrationTask task = new OrchestrationTask(taskId, "GENERAL",
			TaskPriority.NORMAL, List.of());
		TaskRecord record = taskCenterService.getTask(taskId).orElse(null);
		List<PlanStep> steps = currentSteps == null || currentSteps.isEmpty()
			? baseSteps("GENERAL") : currentSteps;
		return createAndStore(task, goalOf(record, task), steps);
	}

	private Plan createAndStore(OrchestrationTask task, String goal, List<PlanStep> steps) {
		List<String> selectedAgents = steps.stream()
			.map(PlanStep::agentType)
			.map(AgentType::name)
			.distinct()
			.toList();
		MemoryContext context = analyzeTask(task);
		RiskLevel risk = initialRisk(context);
		double estimatedCost = estimateCost(steps, context, risk);
		Plan plan = new Plan("plan-" + UUID.randomUUID(), task.getTaskId(), goal, steps,
			selectedAgents, estimatedCost, risk, 0.0, Instant.now());
		plans.put(plan.planId(), plan);
		analyses.put(plan.planId(), new TaskAnalysis(context, recommendations(task.getTaskId()),
			scores()));
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("taskType", value(task.getTaskType()));
		metadata.put("stepCount", steps.size());
		metadata.put("riskLevel", risk.name());
		metadata.put("estimatedCost", estimatedCost);
		metadata.put("selectedAgents", selectedAgents);
		auditService.plannerEvent(EventType.PLAN_CREATED, plan.planId(), plan.taskId(),
			null, null, "Plan created for task " + plan.taskId(), Map.copyOf(metadata));
		return plan;
	}

	/**
	 * Analyzes the task against the long-term memory and the optimization
	 * recommendations: similar historical tasks, known solutions, warnings
	 * (failure patterns) and recommendation text. The context is carried into
	 * the execution graph so the agents plan with the historical experience.
	 */
	public MemoryContext analyzeTask(OrchestrationTask task) {
		if (task == null) {
			return new MemoryContext();
		}
		TaskRecord record = taskCenterService.getTask(task.getTaskId()).orElse(null);
		if (record == null) {
			return new MemoryContext();
		}
		String query = record.getDescription() == null || record.getDescription().isBlank()
			? record.getName() : record.getDescription();
		List<MemoryMatch> similar = memoryService == null ? List.of()
			: memoryService.findSimilarTasks(record.getProjectId(), query, SIMILAR_TASK_LIMIT);
		List<MemoryMatch> solutions = memoryService == null ? List.of()
			: memoryService.findSolutions(record.getProjectId(), query, SOLUTION_LIMIT);
		List<OptimizationRecord> recommendations = recommendations(task.getTaskId());
		List<String> warnings = recommendations.stream()
			.filter(recordItem -> recordItem.getType() == OptimizationType.FAILURE_PATTERN)
			.map(OptimizationRecord::getRecommendation)
			.toList();
		List<String> recommendationText = recommendations.stream()
			.map(OptimizationRecord::getRecommendation)
			.toList();
		return new MemoryContext(similar, solutions, warnings, recommendationText);
	}

	/**
	 * Evaluates the plan into a score in [0, 100] and audits PLAN_EVALUATED.
	 * Similar historical tasks and solutions raise the score, warnings, a
	 * high risk level and a high estimated cost lower it and the historical
	 * agent scores contribute their average composite. The evaluated plan is
	 * stored and returned.
	 */
	public Plan evaluatePlan(Plan plan) {
		if (plan == null) {
			throw new IllegalArgumentException("Plan is required");
		}
		TaskAnalysis analysis = analysisOf(plan);
		double score = 50.0;
		int similar = analysis.context.getSimilarTasks().size();
		score += Math.min(20.0, similar * 5.0);
		int solutions = analysis.context.getSolutions().size();
		score += Math.min(10.0, solutions * 2.5);
		int warnings = analysis.context.getWarnings().size();
		score -= Math.min(10.0, warnings * 3.0);
		double agentContribution = 0.0;
		if (!analysis.scores.isEmpty()) {
			double total = 0.0;
			int matched = 0;
			for (AgentScore agentScore : analysis.scores) {
				if (plan.selectedAgents().contains(agentScore.agentType())
						&& agentScore.totalExecutions() > 0) {
					total += agentScore.composite();
					matched++;
				}
			}
			if (matched > 0) {
				agentContribution = Math.min(25.0, (total / matched) / 4.0);
			}
		}
		score += agentContribution;
		score -= riskPenalty(plan.riskLevel());
		score -= Math.min(15.0, plan.estimatedCost() * 0.1);
		double evaluated = clamp(Math.round(score * 10.0) / 10.0, 0.0, 100.0);
		Plan evaluatedPlan = plan.withScore(evaluated);
		plans.put(plan.planId(), evaluatedPlan);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("score", evaluated);
		metadata.put("riskLevel", plan.riskLevel().name());
		metadata.put("estimatedCost", plan.estimatedCost());
		metadata.put("similarTasks", similar);
		metadata.put("solutions", solutions);
		metadata.put("warnings", warnings);
		auditService.plannerEvent(EventType.PLAN_EVALUATED, plan.planId(), plan.taskId(),
			null, null, "Plan evaluated with score " + evaluated, Map.copyOf(metadata));
		return evaluatedPlan;
	}

	/**
	 * Optimizes the plan: a FAILURE_PATTERN / GRAPH_FLOW recommendation adds a
	 * repair agent before the verifier and raises the risk, an
	 * AGENT_SELECTION recommendation replaces the primary execution agent with
	 * the best-scored agent and a TOOL_USAGE recommendation attaches default
	 * tools to the steps. Only the plan changes (PLAN_OPTIMIZED); no graph is
	 * modified automatically.
	 */
	public Plan optimizePlan(Plan plan) {
		if (plan == null) {
			throw new IllegalArgumentException("Plan is required");
		}
		TaskAnalysis analysis = analysisOf(plan);
		List<PlanStep> steps = new ArrayList<>(plan.steps());
		RiskLevel risk = plan.riskLevel();
		boolean repairHint = anyType(analysis.recommendations, OptimizationType.FAILURE_PATTERN)
			|| anyType(analysis.recommendations, OptimizationType.GRAPH_FLOW);
		if (repairHint && !containsAgent(steps, AgentType.REPAIR_AGENT)) {
			steps = insertRepairBeforeVerifier(steps);
			risk = higher(risk, RiskLevel.HIGH);
		}
		AgentScore best = bestAgent(analysis);
		if (best != null && anyType(analysis.recommendations,
				OptimizationType.AGENT_SELECTION)
				&& !containsAgent(steps, agentOf(best.agentType()))) {
			AgentType primary = primaryExecutionAgent(steps);
			AgentType replacement = agentOf(best.agentType());
			if (primary != null && replacement != null && primary != replacement) {
				steps = replace(steps, primary, replacement);
			}
		}
		if (anyType(analysis.recommendations, OptimizationType.TOOL_USAGE)
				&& !hasTools(steps)) {
			steps = withDefaultTools(steps);
		}
		List<String> selectedAgents = steps.stream()
			.map(PlanStep::agentType)
			.map(AgentType::name)
			.distinct()
			.toList();
		Plan optimized = new Plan(plan.planId(), plan.taskId(), plan.goal(), steps,
			selectedAgents, plan.estimatedCost(), risk, plan.score(), plan.createdAt());
		plans.put(plan.planId(), optimized);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("stepCount", steps.size());
		metadata.put("riskLevel", risk.name());
		metadata.put("selectedAgents", selectedAgents);
		metadata.put("repairAdded", repairHint);
		auditService.plannerEvent(EventType.PLAN_OPTIMIZED, plan.planId(), plan.taskId(),
			null, null, "Plan optimized (" + steps.size() + " steps)",
			Map.copyOf(metadata));
		return optimized;
	}

	/**
	 * Converts an optimized plan into an execution graph through the existing
	 * ExecutionGraphBuilder (step ids become node ids, step dependencies the
	 * graph edges) and audits GRAPH_GENERATED. The memory context analyzed
	 * for the task is attached to the graph.
	 */
	public ExecutionGraph generateGraph(Plan plan) {
		if (plan == null) {
			throw new IllegalArgumentException("Plan is required");
		}
		MemoryContext context = analysisOf(plan).context;
		ExecutionGraph graph = graphBuilder.buildFromPlan(plan, context);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("graphId", graph.getGraphId());
		metadata.put("nodeCount", graph.getNodes().size());
		metadata.put("planId", plan.planId());
		auditService.plannerEvent(EventType.GRAPH_GENERATED, plan.planId(), plan.taskId(),
			null, null, "Execution graph generated from plan " + plan.planId(),
			Map.copyOf(metadata));
		return graph;
	}

	public Optional<Plan> getPlan(String planId) {
		if (planId == null || planId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(plans.get(planId));
	}

	/** The latest plan generated for a task, if any. */
	public Optional<Plan> getPlanByTaskId(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return Optional.empty();
		}
		return plans.values().stream()
			.filter(plan -> taskId.equals(plan.taskId()))
			.max(Comparator.comparing(Plan::createdAt));
	}

	public List<Plan> listPlans() {
		return plans.values().stream()
			.sorted(Comparator.comparing(Plan::createdAt))
			.toList();
	}

	private TaskAnalysis analysisOf(Plan plan) {
		TaskAnalysis analysis = analyses.get(plan.planId());
		return analysis == null
			? new TaskAnalysis(new MemoryContext(), List.of(), List.of()) : analysis;
	}

	private List<OptimizationRecord> recommendations(String taskId) {
		return optimizationService == null ? List.of()
			: optimizationService.getRecommendations(taskId);
	}

	private List<AgentScore> scores() {
		return agentOptimizationService == null ? List.of()
			: agentOptimizationService.scoreAllAgents();
	}

	private boolean anyType(List<OptimizationRecord> recommendations, OptimizationType type) {
		if (recommendations == null) {
			return false;
		}
		return recommendations.stream().anyMatch(record -> record != null
			&& record.getType() == type);
	}

	private boolean containsAgent(List<PlanStep> steps, AgentType agentType) {
		return steps.stream().anyMatch(step -> step.agentType() == agentType);
	}

	/** Inserts a repair step just before the final verifier step and rewires
	 * the verifier to depend on the repair so the fix is verified. */
	private List<PlanStep> insertRepairBeforeVerifier(List<PlanStep> steps) {
		List<PlanStep> extended = new ArrayList<>(steps);
		int insertAt = Math.max(0, extended.size() - 1);
		String repairStepId = "step-repair";
		PlanStep repair = new PlanStep(repairStepId, "Diagnose the failure and propose a fix",
			AgentType.REPAIR_AGENT, List.of("git", "filesystem"),
			insertAt == 0 ? List.of() : List.of(extended.get(insertAt - 1).stepId()));
		extended.add(insertAt, repair);
		if (insertAt > 0) {
			PlanStep verifier = extended.get(extended.size() - 1);
			PlanStep rewired = new PlanStep(verifier.stepId(), verifier.description(),
				verifier.agentType(), verifier.tools(), List.of(repairStepId));
			extended.set(extended.size() - 1, rewired);
		}
		return extended;
	}

	private List<PlanStep> replace(List<PlanStep> steps, AgentType from, AgentType to) {
		List<PlanStep> replaced = new ArrayList<>();
		for (PlanStep step : steps) {
			replaced.add(step.agentType() == from ? step.withAgentType(to) : step);
		}
		return replaced;
	}

	/** The agent that performs the main work: second step for flows of three
	 * or more, otherwise the only step. */
	private AgentType primaryExecutionAgent(List<PlanStep> steps) {
		return steps.size() >= 3 ? steps.get(1).agentType() : steps.get(0).agentType();
	}

	private boolean hasTools(List<PlanStep> steps) {
		return steps.stream().anyMatch(step -> step.tools() != null && !step.tools().isEmpty());
	}

	private List<PlanStep> withDefaultTools(List<PlanStep> steps) {
		List<PlanStep> enriched = new ArrayList<>();
		for (PlanStep step : steps) {
			if (step.tools() == null || step.tools().isEmpty()) {
				enriched.add(step.withTools(defaultTools(step.agentType())));
			}
			else {
				enriched.add(step);
			}
		}
		return enriched;
	}

	private List<String> defaultTools(AgentType agentType) {
		return switch (agentType == null ? AgentType.HERMES : agentType) {
			case HERMES -> List.of("memory");
			case OPENCLAW -> List.of("browser");
			case TEST_AGENT -> List.of("terminal");
			default -> List.of("git", "filesystem");
		};
	}

	private AgentScore bestAgent(TaskAnalysis analysis) {
		return analysis.scores.stream()
			.filter(score -> score.totalExecutions() > 0)
			.max(Comparator.comparingDouble(AgentScore::composite))
			.orElse(null);
	}

	private AgentType agentOf(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return AgentType.valueOf(name.trim().toUpperCase());
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** Base steps for a task category; the node order mirrors the execution
	 * graph templates so the generated graph keeps the familiar flow. */
	List<PlanStep> baseSteps(String taskType) {
		return switch (categoryOf(taskType)) {
			case "BROWSER_TASK" -> List.of(
				step("step-1", "Plan the browser scenario", AgentType.HERMES,
					List.of("memory"), List.of()),
				step("step-2", "Execute the browser flow", AgentType.OPENCLAW,
					List.of("browser"), List.of("step-1")),
				step("step-3", "Verify the browser outcome", AgentType.TEST_AGENT,
					List.of("terminal"), List.of("step-2")));
			case "TEST_TASK" -> List.of(
				step("step-1", "Verify with tests", AgentType.TEST_AGENT,
					List.of("terminal"), List.of()));
			case "REPAIR_TASK" -> List.of(
				step("step-1", "Analyze the failure", AgentType.TEST_AGENT,
					List.of("terminal"), List.of()),
				step("step-2", "Diagnose the root cause", AgentType.REPAIR_AGENT,
					List.of("git", "filesystem"), List.of("step-1")),
				step("step-3", "Fix the code", AgentType.CODEX,
					List.of("git", "filesystem"), List.of("step-2")),
				step("step-4", "Verify the fix", AgentType.TEST_AGENT,
					List.of("terminal"), List.of("step-3")));
			default -> List.of(
				step("step-1", "Plan the implementation approach", AgentType.HERMES,
					List.of("memory"), List.of()),
				step("step-2", "Implement the changes", AgentType.CODEX,
					List.of("git", "filesystem"), List.of("step-1")),
				step("step-3", "Verify with tests", AgentType.TEST_AGENT,
					List.of("terminal"), List.of("step-2")));
		};
	}

	private PlanStep step(String stepId, String description, AgentType agentType,
			List<String> tools, List<String> dependencies) {
		return new PlanStep(stepId, description, agentType, tools, dependencies);
	}

	private String categoryOf(String taskType) {
		String type = taskType == null || taskType.isBlank()
			? "GENERAL" : taskType.trim().toUpperCase();
		return switch (type) {
			case "BROWSER_TASK", "BROWSER_TEST", "BROWSER" -> "BROWSER_TASK";
			case "TEST_TASK", "TEST_VERIFY", "TESTING" -> "TEST_TASK";
			case "REPAIR_TASK", "REPAIR" -> "REPAIR_TASK";
			case "CODE_TASK", "CODE_GENERATION", "CODING" -> "CODE_TASK";
			default -> "CODE_TASK";
		};
	}

	/**
	 * The primary agent for the category through the existing AgentSelector;
	 * used to weight the estimated cost with the historical scores of the
	 * agent that will perform the main work.
	 */
	AgentType primaryAgent(String taskType) {
		return agentSelector == null ? AgentType.HERMES
			: agentSelector.selectType(categoryOf(taskType));
	}

	private String goalOf(TaskRecord record, OrchestrationTask task) {
		if (record != null) {
			if (record.getDescription() != null && !record.getDescription().isBlank()) {
				return record.getDescription();
			}
			if (record.getName() != null && !record.getName().isBlank()) {
				return record.getName();
			}
		}
		return "Autonomous task " + task.getTaskId() + " (" + task.getTaskType() + ")";
	}

	private RiskLevel initialRisk(MemoryContext context) {
		if (context != null && !context.getWarnings().isEmpty()) {
			return RiskLevel.HIGH;
		}
		if (context != null && context.getSimilarTasks().isEmpty()
				&& context.getSolutions().isEmpty()) {
			return RiskLevel.MEDIUM;
		}
		return RiskLevel.LOW;
	}

	private double estimateCost(List<PlanStep> steps, MemoryContext context, RiskLevel risk) {
		double base = steps.size() * 10.0;
		double memoryFactor = 0.0;
		if (context != null) {
			memoryFactor = context.getSimilarTasks().size() * 2.0
				+ context.getSolutions().size() * 1.5
				+ context.getWarnings().size() * 3.0;
		}
		double riskFactor = switch (risk) {
			case LOW -> 5.0;
			case MEDIUM -> 10.0;
			case HIGH -> 18.0;
			case CRITICAL -> 25.0;
		};
		return Math.round((base + memoryFactor + riskFactor) * 100.0) / 100.0;
	}

	private double riskPenalty(RiskLevel risk) {
		return switch (risk == null ? RiskLevel.MEDIUM : risk) {
			case LOW -> 0.0;
			case MEDIUM -> 5.0;
			case HIGH -> 12.0;
			case CRITICAL -> 20.0;
		};
	}

	private RiskLevel higher(RiskLevel current, RiskLevel candidate) {
		if (current == null) {
			return candidate;
		}
		return current.ordinal() >= candidate.ordinal() ? current : candidate;
	}

	private double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	/** The analysis inputs stored for one plan: the memory context, the
	 * optimization recommendations and the historical agent scores. */
	private record TaskAnalysis(MemoryContext context, List<OptimizationRecord> recommendations,
			List<AgentScore> scores) {
	}
}
