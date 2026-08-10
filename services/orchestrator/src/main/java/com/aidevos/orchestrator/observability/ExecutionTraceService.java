package com.aidevos.orchestrator.observability;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Creates and completes execution traces for graph nodes and tool calls. The
 * data sources are the ExecutionGraphExecutor (node traces) and the MCP tool
 * router (tool traces); every transition is audited.
 */
@Service
public class ExecutionTraceService {

	private final TraceRepository repository;
	private final AuditService auditService;

	public ExecutionTraceService(TraceRepository repository) {
		this(repository, AuditService.noop());
	}

	@Autowired
	public ExecutionTraceService(TraceRepository repository, AuditService auditService) {
		this.repository = repository;
		this.auditService = auditService;
	}

	/**
	 * Creates a running trace for a task (used as the root of node/tool
	 * traces).
	 */
	public TraceRecord createTrace(String taskId, String projectId, String graphId) {
		TraceRecord trace = new TraceRecord("trace-" + UUID.randomUUID(), taskId, projectId,
			graphId, TraceStatus.RUNNING, Instant.now());
		repository.save(trace);
		auditService.traceEvent(EventType.TRACE_STARTED, trace.getTraceId(), taskId,
			"RUNNING", "Trace started", Map.of("traceId", trace.getTraceId(),
				"graphId", graphId == null ? "" : graphId));
		return trace;
	}

	/**
	 * Starts a node trace: createTrace + node/agent binding.
	 */
	public TraceRecord startNode(String taskId, String projectId, String graphId,
			String nodeId, String agentType) {
		TraceRecord trace = createTrace(taskId, projectId, graphId);
		trace.setNodeId(nodeId);
		trace.setAgentType(agentType);
		repository.save(trace);
		return trace;
	}

	/**
	 * Starts a tool trace bound to the task and agent that invoked it.
	 */
	public TraceRecord startTool(String taskId, String projectId, String toolId,
			String agentType) {
		TraceRecord trace = createTrace(taskId, projectId, null);
		trace.setToolId(toolId);
		trace.setAgentType(agentType);
		repository.save(trace);
		return trace;
	}

	public Optional<TraceRecord> completeNode(String traceId) {
		TraceRecord trace = repository.get(traceId);
		if (trace == null) {
			return Optional.empty();
		}
		trace.complete();
		repository.save(trace);
		auditService.traceEvent(EventType.TRACE_COMPLETED, traceId, trace.getTaskId(),
			TraceStatus.SUCCESS.name(), "Trace completed", Map.of("traceId", traceId,
				"duration", trace.getDuration()));
		return Optional.of(trace);
	}

	public Optional<TraceRecord> failNode(String traceId, String errorMessage) {
		TraceRecord trace = repository.get(traceId);
		if (trace == null) {
			return Optional.empty();
		}
		trace.fail(errorMessage);
		repository.save(trace);
		auditService.traceEvent(EventType.TRACE_FAILED, traceId, trace.getTaskId(),
			TraceStatus.FAILED.name(), "Trace failed", Map.of("traceId", traceId,
				"error", errorMessage == null ? "" : errorMessage));
		return Optional.of(trace);
	}

	public List<TraceRecord> listByTask(String taskId) {
		return repository.listByTask(taskId);
	}

	public List<TraceRecord> listByProject(String projectId) {
		return repository.listByProject(projectId);
	}
}
