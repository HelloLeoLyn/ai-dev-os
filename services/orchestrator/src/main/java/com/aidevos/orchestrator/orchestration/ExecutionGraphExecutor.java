package com.aidevos.orchestrator.orchestration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.ToolDefinition;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.human.HumanCollaborationService;
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
	private final AgentCollaborationService collaborationService;
	private final ObjectProvider<HumanCollaborationService> humanCollaborationServiceProvider;

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

	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService,
			McpToolRouter toolRouter, ExecutionTraceService traceService,
			ObjectProvider<AgentRuntimeService> runtimeServiceProvider) {
		this(agentExecutors, auditService, taskCenterService, toolRouter, traceService,
			runtimeServiceProvider, null);
	}

	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService,
			McpToolRouter toolRouter, ExecutionTraceService traceService,
			ObjectProvider<AgentRuntimeService> runtimeServiceProvider,
			AgentCollaborationService collaborationService) {
		this(agentExecutors, auditService, taskCenterService, toolRouter, traceService,
			runtimeServiceProvider, collaborationService, null);
	}

	@Autowired
	public ExecutionGraphExecutor(List<AgentExecutor> agentExecutors,
			AuditService auditService, TaskCenterService taskCenterService,
			McpToolRouter toolRouter, ExecutionTraceService traceService,
			ObjectProvider<AgentRuntimeService> runtimeServiceProvider,
			AgentCollaborationService collaborationService,
			ObjectProvider<HumanCollaborationService> humanCollaborationServiceProvider) {
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
		this.collaborationService = collaborationService;
		this.humanCollaborationServiceProvider = humanCollaborationServiceProvider;
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
		AgentTeam team = null;
		if (runtime != null) {
			session = runtime.ensureSession(graph.getTaskId(), graph.getGraphId());
			if (session != null) {
				recover(graph, session);
				runtime.markRunning(session.getSessionId());
				runtime.registerGraph(session.getSessionId(), graph, context);
				if (collaborationService != null) {
					team = collaborationService.createTeam(graph.getTaskId(),
						session.getSessionId());
				}
			}
		}
		MemoryContext memory = memoryHints(context, graph);
		int attempts = 0;
		NodeFailure lastFailure = null;
		while (attempts < graph.getMaxAttempts()) {
			attempts++;
			NodeFailure failure = runPass(graph, context, runtime, session, team, memory);
			if (failure == null) {
				if (session != null) {
					runtime.completeSession(session.getSessionId());
				}
				if (team != null) {
					collaborationService.completeTeam(team.getTeamId());
				}
				return graph;
			}
			if (failure.paused) {
				// Human gate: the session stays PAUSED until the approval
				// resumes it; nothing is finalized here.
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
		if (team != null && lastFailure != null) {
			collaborationService.failTeam(team.getTeamId(), lastFailure.error);
		}
		return graph;
	}

	private NodeFailure runPass(ExecutionGraph graph, AgentExecutionContext context,
			AgentRuntimeService runtime, AgentSession session, AgentTeam team,
			MemoryContext memory) {
		List<String> order = graph.getTopologicalOrder();
		for (int i = 0; i < order.size(); i++) {
			String nodeId = order.get(i);
			ExecutionNode node = graph.getNode(nodeId);
			if (node.getStatus() != ExecutionNodeStatus.PENDING) {
				continue;
			}
			String previousAgent = i == 0 ? null
				: graph.getNode(order.get(i - 1)).getAgentType().name();
			if (node.isHumanGate()) {
				return pauseAtHumanGate(graph, node, previousAgent, session, team);
			}
			node.markRunning();
			String traceId = startNodeTrace(graph, context, node);
			auditService.graphEvent(EventType.NODE_STARTED, graph.getGraphId(),
				graph.getTaskId(), node.getNodeId(), node.getAgentType().name(),
				ExecutionNodeStatus.RUNNING.name(), "Node started",
				Map.of("graphId", graph.getGraphId(), "nodeId", node.getNodeId(),
					"agentType", node.getAgentType().name()));
			if (collaborationService != null && team != null) {
				collaborationService.addAgent(team.getTeamId(), node.getAgentType().name());
				if (previousAgent != null) {
					collaborationService.sendMessage(team.getTeamId(), previousAgent,
						node.getAgentType().name(), AgentMessageType.REQUEST,
						"Requesting " + node.getNodeId() + " from " + previousAgent);
					collaborationService.handoff(team.getTeamId(), previousAgent,
						node.getAgentType().name(), memory);
				}
			}
			AgentExecutor executor = executors.get(node.getAgentType());
			if (executor == null) {
				failNodeTrace(traceId, "No executor registered for agent: "
					+ node.getAgentType());
				return fail(graph, node, "No executor registered for agent: "
					+ node.getAgentType(), runtime, session, team, memory, context);
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
				if (collaborationService != null && team != null) {
					String nextAgent = nextAgentAfter(order, i, graph);
					if (nextAgent != null) {
						collaborationService.sendMessage(team.getTeamId(),
							node.getAgentType().name(), nextAgent,
							AgentMessageType.RESULT, result.output());
					}
				}
			}
			else {
				failNodeTrace(traceId, result.error());
				return fail(graph, node, result.error(), runtime, session, team, memory,
					nodeContext);
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
			AgentTeam team, MemoryContext memory,
			AgentExecutionContext nodeContext) {
		node.markFailed(error == null ? "Node failed" : error);
		auditService.graphEvent(EventType.NODE_FAILED, graph.getGraphId(), graph.getTaskId(),
			node.getNodeId(), node.getAgentType().name(), ExecutionNodeStatus.FAILED.name(),
			"Node failed: " + value(error), nodeMetadata(graph, node));
		if (session != null && runtime != null) {
			runtime.failCheckpoint(session.getSessionId(), node.getNodeId(), nodeContext, error);
		}
		if (collaborationService != null && team != null) {
			String fixAgent = fixAgent(graph, node);
			collaborationService.sendMessage(team.getTeamId(), node.getAgentType().name(),
				fixAgent, AgentMessageType.ERROR, error);
		}
		return NodeFailure.failed(node.getNodeId(), error);
	}

	/**
	 * Human gate handling: requests human approval for the gate node, pauses
	 * the runtime session at this node and tells the team that human input is
	 * required. The gate node stays PENDING; an approved approval resumes the
	 * session which then passes the gate (recover marks it completed) and
	 * continues with the downstream nodes.
	 */
	private NodeFailure pauseAtHumanGate(ExecutionGraph graph, ExecutionNode node,
			String previousAgent, AgentSession session, AgentTeam team) {
		String requester = previousAgent != null ? previousAgent
			: node.getAgentType().name();
		AgentRuntimeService runtime = runtimeService();
		HumanCollaborationService humanService = humanService();
		if (humanService != null) {
			humanService.requestApproval(graph.getTaskId(),
				session == null ? null : session.getSessionId(),
				team == null ? null : team.getTeamId(), node.getNodeId(), requester);
		}
		if (session != null && runtime != null) {
			session.setCurrentNodeId(node.getNodeId());
			runtime.pauseSession(session.getSessionId());
		}
		if (collaborationService != null && team != null) {
			collaborationService.sendMessage(team.getTeamId(), requester, "HUMAN",
				AgentMessageType.HUMAN_REQUEST,
				"Human approval required at " + node.getNodeId());
		}
		return NodeFailure.paused(node.getNodeId());
	}

	/**
	 * The agent that receives the ERROR message when a node fails: the next
	 * non-completed agent in the topology, or the loop-start agent when the
	 * failed node is the end of a repair loop (TEST_AGENT_VERIFY -> the
	 * repair agent that will analyze the failure).
	 */
	private String fixAgent(ExecutionGraph graph, ExecutionNode node) {
		if (graph.hasLoop() && graph.getLoopEndNodeId().equals(node.getNodeId())) {
			ExecutionNode loopStart = graph.getNode(graph.getLoopStartNodeId());
			if (loopStart != null) {
				return loopStart.getAgentType().name();
			}
		}
		List<String> order = graph.getTopologicalOrder();
		int index = order.indexOf(node.getNodeId());
		return index < 0 ? null : nextAgentAfter(order, index, graph);
	}

	private String nextAgentAfter(List<String> order, int index, ExecutionGraph graph) {
		for (int i = index + 1; i < order.size(); i++) {
			ExecutionNode next = graph.getNode(order.get(i));
			if (next.getStatus() != ExecutionNodeStatus.COMPLETED) {
				return next.getAgentType().name();
			}
		}
		return null;
	}

	private MemoryContext memoryHints(AgentExecutionContext context, ExecutionGraph graph) {
		if (context != null && context.getMemoryHints() != null) {
			return context.getMemoryHints();
		}
		return graph.getMemoryContext();
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

	private HumanCollaborationService humanService() {
		return humanCollaborationServiceProvider == null
			? null : humanCollaborationServiceProvider.getIfAvailable();
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

	private record NodeFailure(String nodeId, String error, boolean paused) {

		static NodeFailure failed(String nodeId, String error) {
			return new NodeFailure(nodeId, error, false);
		}

		static NodeFailure paused(String nodeId) {
			return new NodeFailure(nodeId, null, true);
		}
	}
}
