package com.aidevos.orchestrator.orchestration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.ToolDefinition;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Executes an execution graph in topology order. Each node selects its agent
 * through the registered AgentExecutor implementations, runs it, saves the
 * result and emits NODE_STARTED / NODE_COMPLETED / NODE_FAILED audit events
 * (with graphId / nodeId / agentType / duration metadata). A failed node
 * stops the downstream nodes; in a repair graph a failed TEST_AGENT_VERIFY
 * re-enters the bounded loop (loopStart -> loopEnd) up to maxAttempts, which
 * reuses the existing RepairPolicy bound, so it never runs forever.
 * <p>When the agent runtime is wired, executing a graph creates a runtime
 * session before the run, saves a checkpoint after every completed node and
 * a failure checkpoint on a failed node, and finalizes the session
 * (COMPLETED / FAILED). A session resumed from a checkpoint continues after
 * its current node instead of re-running the completed part.
 */
@Component
public class ExecutionGraphExecutor {

	private final Map<AgentType, AgentExecutor> executors = new LinkedHashMap<>();
	private final AuditService auditService;
	private final TaskCenterService taskCenterService;
	private final McpToolRouter toolRouter;
	private final ExecutionTraceService traceService;
	private final ObjectProvider<AgentRuntimeService> runtimeServiceProvider;

	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService) {
		this(agentExecutors, auditService, taskCenterService, null, null, null);
	}

	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService,
			McpToolRouter toolRouter) {
		this(agentExecutors, auditService, taskCenterService, toolRouter, null, null);
	}

	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService,
			McpToolRouter toolRouter, ExecutionTraceService traceService) {
		this(agentExecutors, auditService, taskCenterService, toolRouter, traceService, null);
	}

	@Autowired
	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService,
			McpToolRouter toolRouter, ExecutionTraceService traceService,
			ObjectProvider<AgentRuntimeService> runtimeServiceProvider) {
		if (agentExecutors != null) {
			for (AgentExecutor executor : agentExecutors) {
				if (executor != null && executor.type() != null) {
					executors.putIfAbsent(executor.type(), executor);
				}
			}
		}
		this.auditService = auditService;
		this.taskCenterService = taskCenterService;
		this.toolRouter = toolRouter;
		this.traceService = traceService;
		this.runtimeServiceProvider = runtimeServiceProvider;
	}

	/**
	 * Executes the graph and returns it with the terminal node statuses.
	 * Returns null when the graph or the context are not usable. When the
	 * agent runtime is available a session is created before the run (or a
	 * reusable one is picked up), checkpoints are saved per node and the
	 * session is finalized once the run finishes.
	 */
	public ExecutionGraph execute(ExecutionGraph graph, AgentExecutionContext context) {
		if (graph == null || context == null || context.getTask() == null) {
			return graph;
		}
		AgentRuntimeService runtime = runtimeService();
		AgentSession session = null;
		if (runtime != null) {
			session = runtime.ensureSession(graph.getTaskId(), graph.getGraphId());
			if (session != null) {
				recover(graph, session);
				runtime.markRunning(session.getSessionId());
			}
		}
		int attempts = 0;
		NodeFailure lastFailure = null;
		while (attempts < graph.getMaxAttempts()) {
			attempts++;
			NodeFailure failure = runPass(graph, context, runtime, session);
			if (failure == null) {
				if (session != null) {
					runtime.completeSession(session.getSessionId());
				}
				return graph;
			}
			lastFailure = failure;
			if (graph.hasLoop() && graph.getLoopEndNodeId().equals(failure.nodeId)
				&& attempts < graph.getMaxAttempts()) {
				graph.resetLoop();
				continue;
			}
			break;
		}
		if (session != null && lastFailure != null) {
			runtime.failSession(session.getSessionId(), lastFailure.nodeId);
		}
		return graph;
	}

	private NodeFailure runPass(ExecutionGraph graph, AgentExecutionContext context,
			AgentRuntimeService runtime, AgentSession session) {
		for (String nodeId : graph.getTopologicalOrder()) {
			ExecutionNode node = graph.getNode(nodeId);
			if (node.getStatus() != ExecutionNodeStatus.PENDING) {
				continue;
			}
			node.markRunning();
			String traceId = startNodeTrace(graph, context, node);
			auditService.graphEvent(EventType.NODE_STARTED, graph.getGraphId(),
				graph.getTaskId(), node.getNodeId(), node.getAgentType().name(),
				ExecutionNodeStatus.RUNNING.name(), "Node started",
				Map.of("graphId", graph.getGraphId(), "nodeId", node.getNodeId(),
					"agentType", node.getAgentType().name()));
			AgentExecutor executor = executors.get(node.getAgentType());
			if (executor == null) {
				failNodeTrace(traceId, "No executor registered for agent: "
					+ node.getAgentType());
				return fail(graph, node, "No executor registered for agent: "
					+ node.getAgentType(), runtime, session, context);
			}
			auditService.graphEvent(EventType.AGENT_SELECTED, graph.getGraphId(),
				graph.getTaskId(), node.getNodeId(), node.getAgentType().name(),
				ExecutionNodeStatus.RUNNING.name(),
				"Agent selected: " + node.getAgentType().name(),
				Map.of("graphId", graph.getGraphId(), "nodeId", node.getNodeId(),
					"agentType", node.getAgentType().name(),
					"executor", executor.getClass().getSimpleName()));
			markTaskProgress(node, context.getTask());
			AgentExecutionContext nodeContext = contextFor(node, context);
			AgentExecutionResult result;
			try {
				result = executor.execute(nodeContext);
			}
			catch (RuntimeException exception) {
				failNodeTrace(traceId, exception.getMessage());
				throw exception;
			}
			if (result.status() == ExecutionNodeStatus.COMPLETED) {
				node.markCompleted(result.output());
				completeNodeTrace(traceId);
				auditService.graphEvent(EventType.NODE_COMPLETED, graph.getGraphId(),
					graph.getTaskId(), node.getNodeId(), node.getAgentType().name(),
					ExecutionNodeStatus.COMPLETED.name(), "Node completed",
					nodeMetadata(graph, node));
				if (session != null && runtime != null) {
					runtime.checkpoint(session.getSessionId(), node.getNodeId(), nodeContext);
				}
			}
			else {
				failNodeTrace(traceId, result.error());
				return fail(graph, node, result.error(), runtime, session, nodeContext);
			}
		}
		return null;
	}

	private String startNodeTrace(ExecutionGraph graph, AgentExecutionContext context,
			ExecutionNode node) {
		if (traceService == null) {
			return null;
		}
		TraceRecord trace = traceService.startNode(graph.getTaskId(),
			context.getTask() == null ? null : context.getTask().getProjectId(),
			graph.getGraphId(), node.getNodeId(), node.getAgentType().name());
		return trace == null ? null : trace.getTraceId();
	}

	private void completeNodeTrace(String traceId) {
		if (traceId != null) {
			traceService.completeNode(traceId);
		}
	}

	private void failNodeTrace(String traceId, String error) {
		if (traceId != null) {
			traceService.failNode(traceId, error);
		}
	}

	private NodeFailure fail(ExecutionGraph graph, ExecutionNode node, String error,
			AgentRuntimeService runtime, AgentSession session,
			AgentExecutionContext nodeContext) {
		node.markFailed(error == null ? "Node failed" : error);
		auditService.graphEvent(EventType.NODE_FAILED, graph.getGraphId(), graph.getTaskId(),
			node.getNodeId(), node.getAgentType().name(), ExecutionNodeStatus.FAILED.name(),
			"Node failed: " + value(error), nodeMetadata(graph, node));
		if (session != null && runtime != null) {
			runtime.failCheckpoint(session.getSessionId(), node.getNodeId(), nodeContext, error);
		}
		return new NodeFailure(node.getNodeId());
	}

	/**
	 * Prepares a graph for resuming a session from its current node. Nodes
	 * before the current node are marked completed so they are not re-run;
	 * the current node itself is reset when the session failed (the failed
	 * node is retried) or marked completed when the session was paused.
	 */
	private void recover(ExecutionGraph graph, AgentSession session) {
		String resumeFrom = session.getCurrentNodeId();
		if (resumeFrom == null || resumeFrom.isBlank()) {
			return;
		}
		AgentSessionStatus status = session.getStatus();
		boolean reached = false;
		for (String nodeId : graph.getTopologicalOrder()) {
			ExecutionNode node = graph.getNode(nodeId);
			if (reached) {
				continue;
			}
			if (nodeId.equals(resumeFrom)) {
				reached = true;
				if (status == AgentSessionStatus.FAILED) {
					node.reset();
				}
				else if (node.getStatus() == ExecutionNodeStatus.PENDING) {
					node.markCompleted("recovered");
				}
			}
			else if (node.getStatus() == ExecutionNodeStatus.PENDING) {
				node.markCompleted("recovered");
			}
		}
	}

	private AgentRuntimeService runtimeService() {
		return runtimeServiceProvider == null ? null : runtimeServiceProvider.getIfAvailable();
	}

	/**
	 * Mirrors the legacy coordinator's task status transitions: the CODEX node
	 * marks the task CODING, the TEST_AGENT node marks it TESTING. Terminal
	 * write-back (COMPLETED / FAILED) is done by the caller after execution.
	 */
	private void markTaskProgress(ExecutionNode node, TaskRecord task) {
		if (task == null || task.getStatus() == TaskStatus.FAILED
			|| task.getStatus() == TaskStatus.COMPLETED
			|| task.getStatus() == TaskStatus.SUCCESS) {
			return;
		}
		if (node.getAgentType() == AgentType.CODEX) {
			String from = task.getStatus().name();
			task.markCoding();
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.CODING.name(), "Graph coding started",
				Map.of("nodeId", node.getNodeId()));
		}
		else if (node.getAgentType() == AgentType.TEST_AGENT) {
			String from = task.getStatus().name();
			task.markTesting();
			auditService.taskEvent(EventType.USER_OPERATION, task.getTaskId(), from,
				TaskStatus.TESTING.name(), "Graph testing started",
				Map.of("nodeId", node.getNodeId()));
		}
	}

	private AgentExecutionContext contextFor(ExecutionNode node,
			AgentExecutionContext base) {
		AgentExecutionContext copy = new AgentExecutionContext();
		copy.setTaskId(base.getTaskId());
		copy.setTask(base.getTask());
		copy.setWorkspaceId(base.getWorkspaceId());
		copy.setWorkspacePath(base.getWorkspacePath());
		copy.setGraphId(base.getGraphId());
		copy.setNodeId(node.getNodeId());
		copy.setAgentType(node.getAgentType());
		copy.setInput(base.getInput());
		copy.setPlanningResult(base.getPlanningResult());
		copy.setMemoryHints(base.getMemoryHints());
		if (toolRouter != null) {
			List<ToolDefinition> tools = toolRouter.toolsFor(node.getAgentType());
			copy.setAvailableTools(tools);
			copy.setToolRouter(toolRouter);
			if (!tools.isEmpty()) {
				auditService.toolExecutionEvent(EventType.TOOL_SELECTED, null,
					node.getAgentType().name(), base.getTaskId(), "SELECTED",
					"Tools loaded for agent " + node.getAgentType().name(),
					Map.of("toolIds", tools.stream().map(ToolDefinition::toolId).toList(),
						"agentType", node.getAgentType().name(),
						"nodeId", node.getNodeId()));
			}
		}
		return copy;
	}

	private Map<String, Object> nodeMetadata(ExecutionGraph graph, ExecutionNode node) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("graphId", graph.getGraphId());
		metadata.put("nodeId", node.getNodeId());
		metadata.put("agentType", node.getAgentType().name());
		if (node.getStartedAt() != null && node.getFinishedAt() != null) {
			metadata.put("duration",
				Duration.between(node.getStartedAt(), node.getFinishedAt()).toMillis());
		}
		return metadata;
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private record NodeFailure(String nodeId) {
	}
}
