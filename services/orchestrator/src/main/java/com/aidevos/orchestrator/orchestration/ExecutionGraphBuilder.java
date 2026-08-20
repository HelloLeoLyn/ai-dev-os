package com.aidevos.orchestrator.orchestration;

import java.util.List;
import java.util.UUID;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.execution.ExecutionLimits;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Component;

/**
 * Builds the execution graph for a task category. Ordinary development tasks
 * get HERMES_PLANNING -> CODEX_IMPLEMENTATION -> TEST_AGENT_VERIFY; a repair
 * task gets TEST_AGENT_ANALYZE -> REPAIR_AGENT_ANALYZE -> CODEX_FIX ->
 * TEST_AGENT_VERIFY with a bounded loop (REPAIR_AGENT_ANALYZE ->
 * TEST_AGENT_VERIFY) capped by the unified ExecutionLimits repair ceiling, so
 * a failed verification re-enters the repair analysis without ever looping
 * forever.
 */
@Component
public class ExecutionGraphBuilder {

	public ExecutionGraph build(String taskId, String taskCategory) {
		String graphId = "graph-" + UUID.randomUUID();
		String category = taskCategory == null || taskCategory.isBlank()
			? "CODE_TASK" : taskCategory.trim().toUpperCase();
		return switch (category) {
			case "BROWSER_TASK", "BROWSER_TEST", "BROWSER" -> browserGraph(graphId, taskId);
			case "TEST_TASK", "TEST_VERIFY", "TESTING" -> testGraph(graphId, taskId);
			case "REPAIR_TASK", "REPAIR" -> repairGraph(graphId, taskId);
			default -> codeGraph(graphId, taskId);
		};
	}

	public ExecutionGraph build(String taskId, TaskType taskType) {
		return build(taskId, categoryFor(taskType));
	}

	/**
	 * Memory-aware build: the graph topology follows the task category while
	 * the memory context (similar tasks / solutions / warnings) is attached
	 * to the graph and carried into the agent execution context, so the
	 * Hermes planner produces its plan with the historical experience.
	 */
	public ExecutionGraph build(TaskRecord task, TaskType taskType,
			MemoryContext memoryHints) {
		String taskId = task == null ? "task-unknown" : task.getTaskId();
		ExecutionGraph graph = build(taskId, categoryFor(taskType));
		graph.setMemoryContext(memoryHints);
		return graph;
	}

	/**
	 * Dynamic graph planning for the autonomous orchestrator: the topology is
	 * chosen from the task type plus the optimization recommendations. A code
	 * task with FAILURE_PATTERN / GRAPH_FLOW recommendations becomes a repair
	 * graph (analyze -> repair -> fix -> verify with a bounded loop) instead
	 * of the plain code flow, so learned failure patterns change the graph.
	 * Browser and test topologies keep their category shape. Memory hints are
	 * attached to the graph; nothing is persisted.
	 */
	public ExecutionGraph buildDynamic(TaskRecord task, TaskType taskType,
			MemoryContext memoryHints, List<OptimizationRecord> recommendations) {
		String taskId = task == null ? "task-unknown" : task.getTaskId();
		String category = categoryFor(taskType);
		boolean repairHints = contains(recommendations, OptimizationType.FAILURE_PATTERN)
			|| contains(recommendations, OptimizationType.GRAPH_FLOW);
		ExecutionGraph graph;
		if ("BROWSER_TASK".equals(category)) {
			graph = browserGraph("graph-" + UUID.randomUUID(), taskId);
		}
		else if ("TEST_TASK".equals(category)) {
			graph = testGraph("graph-" + UUID.randomUUID(), taskId);
		}
		else if ("REPAIR_TASK".equals(category) || repairHints) {
			graph = repairGraph("graph-" + UUID.randomUUID(), taskId);
		}
		else {
			graph = codeGraph("graph-" + UUID.randomUUID(), taskId);
		}
		graph.setMemoryContext(memoryHints);
		return graph;
	}

	private boolean contains(List<OptimizationRecord> recommendations,
			OptimizationType type) {
		if (recommendations == null) {
			return false;
		}
		return recommendations.stream().anyMatch(record -> record != null
			&& record.getType() == type);
	}

	public ExecutionGraph build(TaskRecord task, String taskCategory,
			MemoryContext memoryHints) {
		String taskId = task == null ? "task-unknown" : task.getTaskId();
		ExecutionGraph graph = build(taskId, taskCategory);
		graph.setMemoryContext(memoryHints);
		return graph;
	}

	/**
	 * Builds the execution graph from a dynamic plan: each plan step becomes
	 * a node (step id as node id, agent type and dependencies from the step)
	 * and the analyzed memory context is attached to the graph. The graph is
	 * a plain acyclic flow; repair loops are only created by the repair
	 * templates. Nothing is persisted.
	 */
	public ExecutionGraph buildFromPlan(com.aidevos.orchestrator.planner.Plan plan,
			MemoryContext memoryHints) {
		if (plan == null) {
			throw new IllegalArgumentException("Plan is required");
		}
		String graphId = "graph-" + UUID.randomUUID();
		java.util.List<ExecutionNode> nodes = new java.util.ArrayList<>();
		for (com.aidevos.orchestrator.planner.PlanStep step : plan.steps()) {
			ExecutionNode node = new ExecutionNode(step.stepId(), step.agentType());
			for (String dependency : step.dependencies()) {
				node.addDependency(dependency);
			}
			nodes.add(node);
		}
		ExecutionGraph graph = new ExecutionGraph(graphId, plan.taskId(), nodes, null, null, 1);
		graph.setMemoryContext(memoryHints);
		return graph;
	}

	/**
	 * Builds a code graph with a human gate between planning and coding:
	 * HERMES_PLANNING -> HUMAN_GATE -> CODEX_IMPLEMENTATION ->
	 * TEST_AGENT_VERIFY. The graph executor requests human approval at the
	 * gate and pauses the runtime session until it is approved.
	 */
	public ExecutionGraph codeGraphWithHumanGate(String taskId) {
		String graphId = "graph-" + UUID.randomUUID();
		ExecutionNode planning = node("HERMES_PLANNING", AgentType.HERMES);
		ExecutionNode gate = node("HUMAN_GATE", AgentType.HUMAN, "HERMES_PLANNING");
		gate.setHumanGate(true);
		ExecutionNode coding = node("CODEX_IMPLEMENTATION", AgentType.CODEX, "HUMAN_GATE");
		ExecutionNode testing = node("TEST_AGENT_VERIFY", AgentType.TEST_AGENT,
			"CODEX_IMPLEMENTATION");
		return new ExecutionGraph(graphId, taskId,
			java.util.List.of(planning, gate, coding, testing), null, null, 1);
	}

	private String categoryFor(TaskType taskType) {
		return switch (taskType == null ? TaskType.GENERAL : taskType) {
			case TASK_ANALYSIS, CODE_GENERATION -> "CODE_TASK";
			case BROWSER_TEST -> "BROWSER_TASK";
			case TEST_VERIFY -> "TEST_TASK";
			default -> "CODE_TASK";
		};
	}

	private ExecutionGraph codeGraph(String graphId, String taskId) {
		ExecutionNode planning = node("HERMES_PLANNING", AgentType.HERMES);
		ExecutionNode coding = node("CODEX_IMPLEMENTATION", AgentType.CODEX, "HERMES_PLANNING");
		ExecutionNode testing = node("TEST_AGENT_VERIFY", AgentType.TEST_AGENT,
			"CODEX_IMPLEMENTATION");
		return new ExecutionGraph(graphId, taskId,
			java.util.List.of(planning, coding, testing), null, null, 1);
	}

	private ExecutionGraph browserGraph(String graphId, String taskId) {
		ExecutionNode planning = node("HERMES_PLANNING", AgentType.HERMES);
		ExecutionNode browser = node("OPENCLAW_EXECUTE", AgentType.OPENCLAW, "HERMES_PLANNING");
		ExecutionNode testing = node("TEST_AGENT_VERIFY", AgentType.TEST_AGENT,
			"OPENCLAW_EXECUTE");
		return new ExecutionGraph(graphId, taskId,
			java.util.List.of(planning, browser, testing), null, null, 1);
	}

	private ExecutionGraph testGraph(String graphId, String taskId) {
		ExecutionNode testing = node("TEST_AGENT_VERIFY", AgentType.TEST_AGENT);
		return new ExecutionGraph(graphId, taskId, java.util.List.of(testing), null, null, 1);
	}

	private ExecutionGraph repairGraph(String graphId, String taskId) {
		ExecutionNode analyze = node("TEST_AGENT_ANALYZE", AgentType.TEST_AGENT);
		ExecutionNode repair = node("REPAIR_AGENT_ANALYZE", AgentType.REPAIR_AGENT,
			"TEST_AGENT_ANALYZE");
		ExecutionNode fix = node("CODEX_FIX", AgentType.CODEX, "REPAIR_AGENT_ANALYZE");
		ExecutionNode verify = node("TEST_AGENT_VERIFY", AgentType.TEST_AGENT, "CODEX_FIX");
		return new ExecutionGraph(graphId, taskId,
			java.util.List.of(analyze, repair, fix, verify), "REPAIR_AGENT_ANALYZE",
			"TEST_AGENT_VERIFY", ExecutionLimits.DEFAULT_MAX_REPAIR_ATTEMPTS);
	}

	private ExecutionNode node(String nodeId, AgentType agentType, String... dependencies) {
		ExecutionNode node = new ExecutionNode(nodeId, agentType);
		for (String dependency : dependencies) {
			node.addDependency(dependency);
		}
		return node;
	}
}
