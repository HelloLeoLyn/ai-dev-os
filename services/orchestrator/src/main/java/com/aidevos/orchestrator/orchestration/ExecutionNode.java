package com.aidevos.orchestrator.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * One node of an execution graph: the agent that should run, its lifecycle
 * status and the node ids it depends on (topology edges).
 */
public class ExecutionNode {

	private final String nodeId;
	private final AgentType agentType;
	private final List<String> dependencies = new ArrayList<>();
	private volatile ExecutionNodeStatus status = ExecutionNodeStatus.PENDING;
	private volatile String result;
	private volatile Instant startedAt;
	private volatile Instant finishedAt;

	public ExecutionNode(String nodeId, AgentType agentType) {
		this.nodeId = nodeId;
		this.agentType = agentType;
	}

	public void addDependency(String dependencyNodeId) {
		if (dependencyNodeId != null && !dependencyNodeId.isBlank()
			&& !dependencies.contains(dependencyNodeId)) {
			dependencies.add(dependencyNodeId);
		}
	}

	public synchronized void markRunning() {
		this.status = ExecutionNodeStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	public synchronized void markCompleted(String result) {
		this.status = ExecutionNodeStatus.COMPLETED;
		this.result = result;
		this.finishedAt = Instant.now();
	}

	public synchronized void markFailed(String error) {
		this.status = ExecutionNodeStatus.FAILED;
		this.result = error;
		this.finishedAt = Instant.now();
	}

	public synchronized void reset() {
		this.status = ExecutionNodeStatus.PENDING;
		this.result = null;
		this.startedAt = null;
		this.finishedAt = null;
	}

	public String getNodeId() {
		return nodeId;
	}

	public AgentType getAgentType() {
		return agentType;
	}

	public List<String> getDependencies() {
		return List.copyOf(dependencies);
	}

	public ExecutionNodeStatus getStatus() {
		return status;
	}

	public String getResult() {
		return result;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}
}
