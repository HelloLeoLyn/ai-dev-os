package com.aidevos.orchestrator.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Long-running agent runtime. A session wraps the execution graph of a task
 * with a lifecycle status and the current node; the graph executor creates a
 * session before a run, saves a checkpoint after every node (completed and
 * failed) and finalizes the session. Sessions can be paused, resumed and
 * stopped; a resumed session recovers from its current node instead of
 * re-running the completed part of the graph. State stays in the in-memory
 * session repository (no database migration); audit and trace reuse the
 * existing AuditService and ExecutionTraceService.
 */
@Service
public class AgentRuntimeService {

	private final AgentSessionRepository repository;
	private final AuditService auditService;
	private final TaskCenterService taskCenterService;
	private final ExecutionTraceService traceService;
	private final ExecutionGraphBuilder graphBuilder;
	private final ExecutionGraphExecutor graphExecutor;
	private final Map<String, ExecutionGraph> sessionGraphs = new ConcurrentHashMap<>();
	private final Map<String, AgentExecutionContext> sessionContexts = new ConcurrentHashMap<>();
	private final Map<String, String> sessionTraces = new ConcurrentHashMap<>();

	@Autowired
	public AgentRuntimeService(AgentSessionRepository repository, AuditService auditService,
			TaskCenterService taskCenterService, ExecutionTraceService traceService,
			ExecutionGraphBuilder graphBuilder, ExecutionGraphExecutor graphExecutor) {
		this.repository = repository;
		this.auditService = auditService;
		this.taskCenterService = taskCenterService;
		this.traceService = traceService;
		this.graphBuilder = graphBuilder;
		this.graphExecutor = graphExecutor;
	}

	/**
	 * Starts a runtime session for a task: creates the session, audits
	 * SESSION_STARTED, opens the session trace and runs the execution graph
	 * through the graph executor (which checkpoints every node). The session
	 * ends COMPLETED or FAILED.
	 */
	public AgentSession startSession(String taskId) {
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
		ExecutionGraph graph = graphBuilder.build(task, TaskType.GENERAL, null);
		AgentSession session = createSession(task, graph);
		graphExecutor.execute(graph, sessionContexts.get(session.getSessionId()));
		return session;
	}

	/**
	 * Pauses a RUNNING session. Execution itself stays synchronous; the pause
	 * marks the session so a later resume continues from the current node.
	 */
	public AgentSession pauseSession(String sessionId) {
		AgentSession session = require(sessionId);
		if (session.getStatus() != AgentSessionStatus.RUNNING) {
			throw new IllegalStateException(
				"Only RUNNING sessions can be paused: " + sessionId);
		}
		String from = session.getStatus().name();
		session.markPaused();
		repository.save(session);
		auditService.sessionEvent(EventType.SESSION_PAUSED, sessionId, session.getTaskId(),
			from, AgentSessionStatus.PAUSED.name(), "Session paused",
			sessionMetadata(session));
		return session;
	}

	/**
	 * Resumes a PAUSED or FAILED session: audits SESSION_RESUMED and runs the
	 * remaining graph through the executor, which recovers from the session's
	 * current node (re-running a failed node on retry) and finalizes the
	 * session when the run completes.
	 */
	public AgentSession resumeSession(String sessionId) {
		AgentSession session = require(sessionId);
		if (session.getStatus() != AgentSessionStatus.PAUSED
			&& session.getStatus() != AgentSessionStatus.FAILED) {
			throw new IllegalStateException(
				"Only PAUSED or FAILED sessions can be resumed: " + sessionId);
		}
		auditService.sessionEvent(EventType.SESSION_RESUMED, sessionId, session.getTaskId(),
			session.getStatus().name(), AgentSessionStatus.RUNNING.name(),
			"Session resumed", sessionMetadata(session));
		TaskRecord task = taskOf(session);
		ExecutionGraph graph = sessionGraphs.computeIfAbsent(sessionId,
			ignored -> graphBuilder.build(task, TaskType.GENERAL, null));
		AgentExecutionContext context = sessionContexts.computeIfAbsent(sessionId,
			ignored -> buildContext(task, graph));
		graphExecutor.execute(graph, context);
		return session;
	}

	/**
	 * Stops a session that has not finished: RUNNING/PAUSED -> STOPPED.
	 */
	public AgentSession stopSession(String sessionId) {
		AgentSession session = require(sessionId);
		if (isTerminal(session.getStatus())) {
			throw new IllegalStateException(
				"Session already finished: " + sessionId);
		}
		String from = session.getStatus().name();
		session.markStopped();
		repository.save(session);
		auditService.sessionEvent(EventType.SESSION_STOPPED, sessionId, session.getTaskId(),
			from, AgentSessionStatus.STOPPED.name(), "Session stopped",
			sessionMetadata(session));
		return session;
	}

	public Optional<AgentSession> getSession(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(sessionId));
	}

	/**
	 * Snapshots the current state of the session (its current node and the
	 * execution context of that node) as a checkpoint.
	 */
	public AgentCheckpoint checkpoint(String sessionId) {
		AgentSession session = require(sessionId);
		return saveCheckpoint(session, session.getCurrentNodeId(),
			currentContext(session), false, null);
	}

	/**
	 * Builds the execution context of the session's current node: the latest
	 * checkpoint carries the node-specific context (nodeId, agentType, node
	 * results); before the first checkpoint the base session context is used.
	 */
	private AgentExecutionContext currentContext(AgentSession session) {
		AgentExecutionContext context = null;
		List<AgentCheckpoint> checkpoints = repository.listCheckpoints(session.getSessionId());
		if (!checkpoints.isEmpty()) {
			AgentCheckpoint latest = checkpoints.get(checkpoints.size() - 1);
			if (latest.getExecutionContext() != null) {
				context = copyOf(latest.getExecutionContext());
			}
		}
		if (context == null) {
			context = copyOf(sessionContexts.get(session.getSessionId()));
		}
		if (context != null) {
			context.setNodeId(session.getCurrentNodeId());
		}
		return context;
	}

	public List<AgentSession> sessionsForTask(String taskId) {
		return repository.listByTask(taskId);
	}

	/**
	 * Registers the graph and base context of a session so a later resume
	 * recovers the same topology instead of rebuilding a default graph. The
	 * execution graph executor calls this before every run; a session created
	 * through startSession already has its graph registered.
	 */
	public void registerGraph(String sessionId, ExecutionGraph graph,
			AgentExecutionContext context) {
		if (sessionId == null || graph == null) {
			return;
		}
		sessionGraphs.put(sessionId, graph);
		if (context != null) {
			sessionContexts.put(sessionId, copyOf(context));
		}
	}

	/**
	 * Returns the reusable session for a task (CREATED/RUNNING/PAUSED/FAILED)
	 * or creates a new RUNNING session before a graph run. Called by the
	 * execution graph executor so every graph execution is wrapped in a
	 * runtime session; a failed session is reused so a retry recovers from
	 * its current node.
	 */
	public AgentSession ensureSession(String taskId, String graphId) {
		for (AgentSession session : repository.listByTask(taskId)) {
			if (isReusable(session.getStatus())) {
				return session;
			}
		}
		TaskRecord task = taskCenterService.getTask(taskId).orElse(null);
		AgentSession session = new AgentSession("session-" + UUID.randomUUID(),
			taskId, graphId);
		session.markRunning();
		repository.save(session);
		auditService.sessionEvent(EventType.SESSION_STARTED, session.getSessionId(), taskId,
			AgentSessionStatus.CREATED.name(), AgentSessionStatus.RUNNING.name(),
			"Session started", sessionMetadata(session));
		if (traceService != null && task != null) {
			TraceRecord trace = traceService.createTrace(taskId, task.getProjectId(), graphId);
			if (trace != null) {
				sessionTraces.put(session.getSessionId(), trace.getTraceId());
			}
		}
		return session;
	}

	/**
	 * Syncs a session to RUNNING without an audit event. The executor calls
	 * this when it starts executing a session that was created or reused
	 * outside resumeSession (e.g. a paused session picked up by a graph run).
	 */
	public AgentSession markRunning(String sessionId) {
		AgentSession session = require(sessionId);
		if (!isTerminal(session.getStatus())
			&& session.getStatus() != AgentSessionStatus.RUNNING) {
			session.markRunning();
			repository.save(session);
		}
		return session;
	}

	/**
	 * Saves a checkpoint after a node completed and advances the session's
	 * current node. Called by the graph executor.
	 */
	public AgentCheckpoint checkpoint(String sessionId, String nodeId,
			AgentExecutionContext context) {
		AgentSession session = require(sessionId);
		session.setCurrentNodeId(nodeId);
		repository.save(session);
		return saveCheckpoint(session, nodeId, context, false, null);
	}

	/**
	 * Saves a failure checkpoint for the failed node and advances the
	 * session's current node. Called by the graph executor on a node failure.
	 */
	public AgentCheckpoint failCheckpoint(String sessionId, String nodeId,
			AgentExecutionContext context, String error) {
		AgentSession session = require(sessionId);
		session.setCurrentNodeId(nodeId);
		repository.save(session);
		return saveCheckpoint(session, nodeId, context, true, error);
	}

	public AgentSession completeSession(String sessionId) {
		AgentSession session = require(sessionId);
		String from = session.getStatus().name();
		session.markCompleted();
		repository.save(session);
		auditService.sessionEvent(EventType.SESSION_COMPLETED, sessionId,
			session.getTaskId(), from, AgentSessionStatus.COMPLETED.name(),
			"Session completed", sessionMetadata(session));
		completeTrace(sessionId);
		return session;
	}

	public AgentSession failSession(String sessionId, String failedNodeId) {
		AgentSession session = require(sessionId);
		String from = session.getStatus().name();
		session.markFailed();
		session.setCurrentNodeId(failedNodeId);
		repository.save(session);
		auditService.sessionEvent(EventType.SESSION_FAILED, sessionId,
			session.getTaskId(), from, AgentSessionStatus.FAILED.name(),
			"Session failed at node " + value(failedNodeId), sessionMetadata(session));
		failTrace(sessionId, failedNodeId);
		return session;
	}

	private AgentSession createSession(TaskRecord task, ExecutionGraph graph) {
		AgentSession session = new AgentSession("session-" + UUID.randomUUID(),
			task.getTaskId(), graph.getGraphId());
		session.markRunning();
		repository.save(session);
		auditService.sessionEvent(EventType.SESSION_STARTED, session.getSessionId(),
			task.getTaskId(), AgentSessionStatus.CREATED.name(),
			AgentSessionStatus.RUNNING.name(), "Session started", sessionMetadata(session));
		if (traceService != null) {
			TraceRecord trace = traceService.createTrace(task.getTaskId(),
				task.getProjectId(), graph.getGraphId());
			if (trace != null) {
				sessionTraces.put(session.getSessionId(), trace.getTraceId());
			}
		}
		sessionGraphs.put(session.getSessionId(), graph);
		sessionContexts.put(session.getSessionId(), buildContext(task, graph));
		return session;
	}

	private AgentCheckpoint saveCheckpoint(AgentSession session, String nodeId,
			AgentExecutionContext context, boolean failed, String error) {
		AgentCheckpoint checkpoint = new AgentCheckpoint(session.getSessionId(), nodeId,
			copyOf(context), Instant.now());
		repository.saveCheckpoint(checkpoint);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("nodeId", value(nodeId));
		metadata.put("failed", failed);
		if (error != null) {
			metadata.put("error", error);
		}
		auditService.sessionEvent(EventType.CHECKPOINT_CREATED, session.getSessionId(),
			session.getTaskId(), session.getStatus().name(), session.getStatus().name(),
			failed ? "Failure checkpoint saved" : "Checkpoint saved", metadata);
		return checkpoint;
	}

	private void completeTrace(String sessionId) {
		String traceId = sessionTraces.get(sessionId);
		if (traceId != null && traceService != null) {
			traceService.completeNode(traceId);
		}
	}

	private void failTrace(String sessionId, String nodeId) {
		String traceId = sessionTraces.get(sessionId);
		if (traceId != null && traceService != null) {
			traceService.failNode(traceId, "Session failed at node " + value(nodeId));
		}
	}

	private AgentExecutionContext buildContext(TaskRecord task, ExecutionGraph graph) {
		AgentExecutionContext context = new AgentExecutionContext();
		context.setTaskId(task.getTaskId());
		context.setTask(task);
		context.setWorkspaceId(task.getWorkspaceId());
		context.setGraphId(graph.getGraphId());
		context.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		return context;
	}

	private AgentExecutionContext copyOf(AgentExecutionContext context) {
		if (context == null) {
			return null;
		}
		AgentExecutionContext copy = new AgentExecutionContext();
		copy.setTaskId(context.getTaskId());
		copy.setTask(context.getTask());
		copy.setWorkspaceId(context.getWorkspaceId());
		copy.setWorkspacePath(context.getWorkspacePath());
		copy.setGraphId(context.getGraphId());
		copy.setNodeId(context.getNodeId());
		copy.setAgentType(context.getAgentType());
		copy.setInput(context.getInput());
		copy.setPlanningResult(context.getPlanningResult());
		copy.setMemoryHints(context.getMemoryHints());
		copy.setAvailableTools(context.getAvailableTools());
		copy.setToolRouter(context.getToolRouter());
		return copy;
	}

	private TaskRecord taskOf(AgentSession session) {
		return taskCenterService.getTask(session.getTaskId())
			.orElseThrow(() -> new IllegalArgumentException(
				"Task not found: " + session.getTaskId()));
	}

	private AgentSession require(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("Session id is required");
		}
		AgentSession session = repository.get(sessionId);
		if (session == null) {
			throw new IllegalArgumentException("Session not found: " + sessionId);
		}
		return session;
	}

	private Map<String, Object> sessionMetadata(AgentSession session) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("graphId", value(session.getGraphId()));
		if (session.getCurrentNodeId() != null) {
			metadata.put("currentNodeId", session.getCurrentNodeId());
		}
		return metadata;
	}

	private boolean isReusable(AgentSessionStatus status) {
		return status == AgentSessionStatus.CREATED || status == AgentSessionStatus.RUNNING
			|| status == AgentSessionStatus.PAUSED || status == AgentSessionStatus.FAILED;
	}

	private boolean isTerminal(AgentSessionStatus status) {
		return status == AgentSessionStatus.COMPLETED || status == AgentSessionStatus.FAILED
			|| status == AgentSessionStatus.STOPPED;
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
