package com.aidevos.orchestrator.orchestration;

import java.util.UUID;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.repair.RepairPolicy;
import org.springframework.stereotype.Component;

/**
 * Builds the execution graph for a task category. Ordinary development tasks
 * get HERMES_PLANNING -> CODEX_IMPLEMENTATION -> TEST_AGENT_VERIFY; a repair
 * task gets TEST_AGENT_ANALYZE -> REPAIR_AGENT_ANALYZE -> CODEX_FIX ->
 * TEST_AGENT_VERIFY with a bounded loop (REPAIR_AGENT_ANALYZE ->
 * TEST_AGENT_VERIFY) capped by the existing RepairPolicy.MAX_RETRY, so a
 * failed verification re-enters the repair analysis without ever looping
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
			"TEST_AGENT_VERIFY", RepairPolicy.MAX_RETRY);
	}

	private ExecutionNode node(String nodeId, AgentType agentType, String... dependencies) {
		ExecutionNode node = new ExecutionNode(nodeId, agentType);
		for (String dependency : dependencies) {
			node.addDependency(dependency);
		}
		return node;
	}
}
