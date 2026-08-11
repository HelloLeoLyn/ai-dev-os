package com.aidevos.orchestrator.adaptive;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import com.aidevos.orchestrator.optimization.OptimizationService;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionNode;
import com.aidevos.orchestrator.planner.Plan;
import com.aidevos.orchestrator.planner.PlanStep;
import com.aidevos.orchestrator.planner.PlanningService;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Adaptive execution. After a graph node fails, the executor collects
 * execution feedback and asks this service to decide what to do: retry the
 * node, switch its agent, modify the graph (add a repair step), change the
 * tools or replan the task through the dynamic planner. The decision history,
 * feedback and replans stay in-memory; everything reuses the existing
 * AuditService, PlanningService, OptimizationService, MemoryService,
 * AgentOptimizationService and the runtime session. The Scheduler / Worker /
 * ExecutionEngine are never touched.
 */
@Service
public class AdaptiveExecutionService {

	/** Failures after which the primary implementation agent is switched. */
	static final int SWITCH_AFTER_FAILURES = 2;

	/** Failures after which the task is replanned through the planner. */
	static final int REPLAN_AFTER_FAILURES = 3;

	private final AuditService auditService;
	private final TaskCenterService taskCenterService;
	private final AgentOptimizationService agentOptimizationService;
	private final OptimizationService optimizationService;
	private final MemoryService memoryService;
	private final PlanningService planningService;
	private final AgentRuntimeService runtimeService;
	private final Map<String, ExecutionFeedback> feedbacks = new ConcurrentHashMap<>();
	private final Map<String, AdaptationDecision> decisions = new ConcurrentHashMap<>();
	private final Map<String, String> replans = new ConcurrentHashMap<>();

	@Autowired
	public AdaptiveExecutionService(AuditService auditService,
			TaskCenterService taskCenterService,
			AgentOptimizationService agentOptimizationService,
			OptimizationService optimizationService, MemoryService memoryService,
			PlanningService planningService, AgentRuntimeService runtimeService) {
		this.auditService = auditService;
		this.taskCenterService = taskCenterService;
		this.agentOptimizationService = agentOptimizationService;
		this.optimizationService = optimizationService;
		this.memoryService = memoryService;
		this.planningService = planningService;
		this.runtimeService = runtimeService;
	}

	/**
	 * Collects one execution feedback record and audits
	 * EXECUTION_FEEDBACK_RECEIVED with the node / agent / status / error /
	 * duration metadata.
	 */
	public ExecutionFeedback collectFeedback(String taskId, String sessionId, String nodeId,
			String agentType, String status, String error, long duration) {
		ExecutionFeedback feedback = new ExecutionFeedback("feedback-" + UUID.randomUUID(),
			taskId, sessionId, nodeId, agentType, status, error, duration, Instant.now());
		feedbacks.put(feedback.getFeedbackId(), feedback);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("status", value(status));
		metadata.put("error", value(error));
		metadata.put("duration", duration);
		auditService.adaptiveEvent(EventType.EXECUTION_FEEDBACK_RECEIVED,
			feedback.getFeedbackId(), taskId, sessionId, nodeId, agentType,
			"Execution feedback received for node " + value(nodeId), Map.copyOf(metadata));
		return feedback;
	}

	/**
	 * Analyzes a whole runtime session: audits ADAPTATION_STARTED with the
	 * current feedback count plus the memory / optimization context of the
	 * task and returns the decision derived from the latest feedback (null
	 * when the session has no feedback yet).
	 */
	public AdaptationDecision analyzeExecution(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("Session id is required");
		}
		AgentSession session = runtimeService == null ? null
			: runtimeService.getSession(sessionId).orElse(null);
		if (session == null) {
			throw new IllegalArgumentException("Session not found: " + sessionId);
		}
		List<ExecutionFeedback> history = feedbacksForSession(sessionId);
		ExecutionFeedback latest = history.isEmpty() ? null : history.get(history.size() - 1);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("failureCount", history.size());
		if (optimizationService != null) {
			metadata.put("recommendationCount",
				optimizationService.getRecommendations(session.getTaskId()).size());
		}
		TaskRecord task = taskCenterService == null ? null
			: taskCenterService.getTask(session.getTaskId()).orElse(null);
		if (task != null && memoryService != null) {
			String query = task.getDescription() == null || task.getDescription().isBlank()
				? task.getName() : task.getDescription();
			metadata.put("similarTaskCount",
				memoryService.findSimilarTasks(task.getProjectId(), query, 5).size());
		}
		auditService.adaptiveEvent(EventType.ADAPTATION_STARTED, null, session.getTaskId(),
			sessionId, latest == null ? null : latest.getNodeId(),
			latest == null ? null : latest.getAgentType(),
			"Analyzing execution of session " + sessionId, Map.copyOf(metadata));
		if (latest == null) {
			return null;
		}
		return decide(session.getTaskId(), sessionId, latest.getNodeId(),
			latest.getAgentType(), latest.getError(), history.size());
	}

	/**
	 * Decides the adaptation for a failed node from its failure count: first
	 * failure (or a tool-related error) is retried / tool-changed, the second
	 * switches the agent when a better-scored one exists (otherwise a repair
	 * step is added) and the third replans the task. The decision is stored
	 * and audited as ADAPTATION_DECIDED.
	 */
	public AdaptationDecision decide(String taskId, String sessionId, String nodeId,
			String agentType, String error, int failureCount) {
		AdaptationAction action;
		String reason;
		double confidence;
		String targetAgent = null;
		String toolId = null;
		if (isToolRelated(error)) {
			action = AdaptationAction.CHANGE_TOOL;
			toolId = "mcp-router";
			reason = "Node " + value(nodeId) + " failed with a tool-related error; "
				+ "retry with MCP tools";
			confidence = 0.6;
		}
		else if (failureCount >= REPLAN_AFTER_FAILURES && planningService != null) {
			action = AdaptationAction.REPLAN;
			reason = "Node " + value(nodeId) + " failed " + failureCount
				+ " times; replan the task through the dynamic planner";
			confidence = 0.8;
		}
		else if (failureCount >= SWITCH_AFTER_FAILURES) {
			String best = bestOtherAgent(agentType);
			if (best != null) {
				action = AdaptationAction.SWITCH_AGENT;
				targetAgent = best;
				reason = "Node " + value(nodeId) + " failed twice; switch "
					+ value(agentType) + " to " + best;
				confidence = 0.7;
			}
			else {
				action = AdaptationAction.MODIFY_GRAPH;
				reason = "Node " + value(nodeId) + " failed twice and no better agent "
					+ "exists; add a repair step";
				confidence = 0.65;
			}
		}
		else {
			action = AdaptationAction.RETRY;
			reason = "Transient failure of node " + value(nodeId) + "; retry once";
			confidence = 0.5;
		}
		AdaptationDecision decision = new AdaptationDecision("decision-" + UUID.randomUUID(),
			taskId, nodeId, reason, action, confidence, targetAgent, toolId);
		decisions.put(decision.getDecisionId(), decision);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("action", action.name());
		metadata.put("nodeId", value(nodeId));
		metadata.put("agentType", value(agentType));
		metadata.put("confidence", confidence);
		metadata.put("failureCount", failureCount);
		if (targetAgent != null) {
			metadata.put("targetAgent", targetAgent);
		}
		if (toolId != null) {
			metadata.put("toolId", toolId);
		}
		auditService.adaptiveEvent(EventType.ADAPTATION_DECIDED, decision.getDecisionId(),
			taskId, sessionId, nodeId, agentType, "Adaptation decided: " + action.name(),
			Map.copyOf(metadata));
		return decision;
	}

	/**
	 * Applies a decision to the execution graph: RETRY and CHANGE_TOOL reset
	 * the failed node (a new graph for SWITCH_AGENT / MODIFY_GRAPH, a fresh
	 * graph for REPLAN). The returned graph is the graph the executor should
	 * run next; it is the same instance for in-place actions so completed
	 * nodes are never re-run.
	 */
	public ExecutionGraph applyDecision(AdaptationDecision decision, ExecutionGraph graph,
			AgentSession session) {
		if (decision == null || graph == null) {
			return graph;
		}
		return switch (decision.getAction()) {
			case SWITCH_AGENT -> decision.getTargetAgent() == null
				|| decision.getTargetAgent().isBlank()
					? resetAndReturn(graph, decision.getNodeId())
					: replaceAgent(graph, decision.getNodeId(), decision.getTargetAgent());
			case MODIFY_GRAPH -> insertRepair(graph, decision.getNodeId());
			case REPLAN -> replan(session == null ? graph.getTaskId() : session.getTaskId(),
				graph);
			case RETRY, CHANGE_TOOL -> resetAndReturn(graph, decision.getNodeId());
			default -> resetAndReturn(graph, decision.getNodeId());
		};
	}

	/**
	 * Executor-facing entry: collects the failure feedback, audits
	 * ADAPTATION_STARTED, derives the decision from the session's failure
	 * history and applies it. Returns the graph to run next, or the same
	 * graph when nothing can be adapted.
	 */
	public ExecutionGraph adapt(ExecutionGraph graph, AgentSession session, String nodeId,
			String agentType, String error, long duration, int adaptationIndex) {
		if (graph == null || session == null || nodeId == null || nodeId.isBlank()) {
			return graph;
		}
		String taskId = session.getTaskId();
		collectFeedback(taskId, session.getSessionId(), nodeId, agentType, "FAILED", error,
			duration);
		int failureCount = feedbacksForSession(session.getSessionId()).size();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("failureCount", failureCount);
		metadata.put("adaptationIndex", adaptationIndex);
		auditService.adaptiveEvent(EventType.ADAPTATION_STARTED, null, taskId,
			session.getSessionId(), nodeId, agentType,
			"Adapting execution after node " + value(nodeId) + " failed",
			Map.copyOf(metadata));
		AdaptationDecision decision = decide(taskId, session.getSessionId(), nodeId, agentType,
			error, failureCount);
		return applyDecision(decision, graph, session);
	}

	/**
	 * Re-plans the task through the dynamic planner: the current graph is
	 * converted into plan steps, a fresh plan is created / evaluated /
	 * optimized and a new graph is generated. The replan is audited as
	 * GRAPH_REPLANNED with the new plan and graph ids.
	 */
	public ExecutionGraph replan(String taskId, ExecutionGraph previousGraph) {
		if (planningService == null || previousGraph == null) {
			return previousGraph;
		}
		List<PlanStep> steps = stepsFromGraph(previousGraph);
		Plan plan = planningService.replan(taskId, steps);
		ExecutionGraph newGraph = planningService.generateGraph(plan);
		replans.put(taskId, plan.planId());
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("planId", plan.planId());
		metadata.put("graphId", newGraph.getGraphId());
		metadata.put("stepCount", newGraph.getNodes().size());
		auditService.adaptiveEvent(EventType.GRAPH_REPLANNED, null, taskId, null, null, null,
			"Task replanned with plan " + plan.planId(), Map.copyOf(metadata));
		return newGraph;
	}

	public Optional<ExecutionFeedback> getFeedback(String feedbackId) {
		if (feedbackId == null || feedbackId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(feedbacks.get(feedbackId));
	}

	public Optional<AdaptationDecision> getDecision(String decisionId) {
		if (decisionId == null || decisionId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(decisions.get(decisionId));
	}

	public List<ExecutionFeedback> feedbacksForTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		return feedbacks.values().stream()
			.filter(feedback -> taskId.equals(feedback.getTaskId()))
			.sorted(Comparator.comparing(ExecutionFeedback::getCreatedAt))
			.toList();
	}

	public List<AdaptationDecision> decisionsForTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		return decisions.values().stream()
			.filter(decision -> taskId.equals(decision.getTaskId()))
			.sorted(Comparator.comparing(AdaptationDecision::getDecisionId))
			.toList();
	}

	public List<String> replansForTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		return replans.entrySet().stream()
			.filter(entry -> taskId.equals(entry.getKey()))
			.map(Map.Entry::getValue)
			.toList();
	}

	private List<ExecutionFeedback> feedbacksForSession(String sessionId) {
		return feedbacks.values().stream()
			.filter(feedback -> sessionId.equals(feedback.getSessionId()))
			.sorted(Comparator.comparing(ExecutionFeedback::getCreatedAt))
			.toList();
	}

	private String bestOtherAgent(String agentType) {
		if (agentOptimizationService == null) {
			return null;
		}
		return agentOptimizationService.scoreAllAgents().stream()
			.filter(score -> score.totalExecutions() > 0)
			.filter(score -> !score.agentType().equals(agentType))
			.max(Comparator.comparingDouble(AgentScore::composite))
			.map(AgentScore::agentType)
			.orElse(null);
	}

	private boolean isToolRelated(String error) {
		if (error == null) {
			return false;
		}
		String lower = error.toLowerCase();
		return lower.contains("tool") || lower.contains("mcp")
			|| lower.contains("no executor");
	}

	private ExecutionGraph resetAndReturn(ExecutionGraph graph, String nodeId) {
		ExecutionNode node = graph == null || nodeId == null ? null : graph.getNode(nodeId);
		if (node != null) {
			node.reset();
		}
		return graph;
	}

	/** Rebuilds a graph with the failed node handled by a new agent; other
	 * nodes keep their instances and statuses so completed work is not
	 * re-run. */
	private ExecutionGraph replaceAgent(ExecutionGraph graph, String nodeId,
			String targetAgent) {
		AgentType replacementType = agentOf(targetAgent);
		if (replacementType == null) {
			return resetAndReturn(graph, nodeId);
		}
		List<ExecutionNode> nodes = new ArrayList<>();
		boolean replaced = false;
		for (ExecutionNode node : graph.getNodes()) {
			if (node.getNodeId().equals(nodeId)) {
				ExecutionNode replacement = new ExecutionNode(node.getNodeId(),
					replacementType);
				for (String dependency : node.getDependencies()) {
					replacement.addDependency(dependency);
				}
				if (node.isHumanGate()) {
					replacement.setHumanGate(true);
				}
				nodes.add(replacement);
				replaced = true;
			}
			else {
				nodes.add(node);
			}
		}
		return replaced ? rebuild(graph, nodes) : graph;
	}

	/** Inserts a repair step that must run before the failed node retries. */
	private ExecutionGraph insertRepair(ExecutionGraph graph, String failedNodeId) {
		ExecutionNode failed = graph == null || failedNodeId == null
			? null : graph.getNode(failedNodeId);
		if (failed == null) {
			return graph;
		}
		failed.reset();
		String repairId = "REPAIR_" + failedNodeId;
		ExecutionNode repair = new ExecutionNode(repairId, AgentType.REPAIR_AGENT);
		failed.addDependency(repairId);
		List<ExecutionNode> nodes = new ArrayList<>(graph.getNodes());
		nodes.add(repair);
		return rebuild(graph, nodes);
	}

	private ExecutionGraph rebuild(ExecutionGraph graph, List<ExecutionNode> nodes) {
		ExecutionGraph updated = new ExecutionGraph(graph.getGraphId(), graph.getTaskId(),
			nodes, graph.getLoopStartNodeId(), graph.getLoopEndNodeId(),
			graph.getMaxAttempts());
		updated.setMemoryContext(graph.getMemoryContext());
		return updated;
	}

	private List<PlanStep> stepsFromGraph(ExecutionGraph graph) {
		List<PlanStep> steps = new ArrayList<>();
		for (ExecutionNode node : graph.getNodes()) {
			steps.add(new PlanStep(node.getNodeId(), "Replanned step " + node.getNodeId(),
				node.getAgentType(), List.of(), node.getDependencies()));
		}
		return steps;
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

	private String value(String value) {
		return value == null ? "" : value;
	}
}
