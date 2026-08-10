package com.aidevos.orchestrator.observability;

import java.time.Instant;
import java.time.Duration;

/**
 * One observability trace: a graph node or tool execution for a task.
 * Captures the task/project scope, the graph node and agent, the tool (for
 * tool traces) and the timing plus error of the execution.
 */
public class TraceRecord {

	private final String traceId;
	private final String taskId;
	private final String projectId;
	private final String graphId;
	private volatile String nodeId;
	private volatile String agentType;
	private volatile String toolId;
	private volatile TraceStatus status;
	private final Instant startTime;
	private volatile Instant endTime;
	private volatile long duration;
	private volatile String errorMessage;

	public TraceRecord(String traceId, String taskId, String projectId, String graphId,
			TraceStatus status, Instant startTime) {
		this.traceId = traceId;
		this.taskId = taskId;
		this.projectId = projectId;
		this.graphId = graphId;
		this.status = status == null ? TraceStatus.RUNNING : status;
		this.startTime = startTime == null ? Instant.now() : startTime;
	}

	public void complete() {
		this.status = TraceStatus.SUCCESS;
		this.endTime = Instant.now();
		this.duration = Duration.between(startTime, endTime).toMillis();
	}

	public void fail(String errorMessage) {
		this.status = TraceStatus.FAILED;
		this.endTime = Instant.now();
		this.duration = Duration.between(startTime, endTime).toMillis();
		this.errorMessage = errorMessage;
	}

	public String getTraceId() {
		return traceId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getGraphId() {
		return graphId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public String getAgentType() {
		return agentType;
	}

	public void setAgentType(String agentType) {
		this.agentType = agentType;
	}

	public String getToolId() {
		return toolId;
	}

	public void setToolId(String toolId) {
		this.toolId = toolId;
	}

	public TraceStatus getStatus() {
		return status;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public long getDuration() {
		return duration;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
