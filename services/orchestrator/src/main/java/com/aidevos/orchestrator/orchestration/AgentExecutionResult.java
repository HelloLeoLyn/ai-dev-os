package com.aidevos.orchestrator.orchestration;

import java.time.Instant;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * Outcome of one agent execution inside a graph node: the node, the agent
 * type, the terminal status and the captured output or error.
 */
public record AgentExecutionResult(
		String nodeId,
		AgentType agentType,
		ExecutionNodeStatus status,
		String output,
		String error,
		Instant startedAt,
		Instant finishedAt) {

	public static AgentExecutionResult success(ExecutionNode node, String output) {
		Instant now = Instant.now();
		return new AgentExecutionResult(node.getNodeId(), node.getAgentType(),
			ExecutionNodeStatus.COMPLETED, output, null, now, now);
	}

	public static AgentExecutionResult failure(ExecutionNode node, String error) {
		Instant now = Instant.now();
		return new AgentExecutionResult(node.getNodeId(), node.getAgentType(),
			ExecutionNodeStatus.FAILED, null, error, now, now);
	}

	/** Builds a result from the executing node's context. */
	public static AgentExecutionResult of(AgentExecutionContext context,
			ExecutionNodeStatus status, String output, String error) {
		Instant now = Instant.now();
		return new AgentExecutionResult(context.getNodeId(), context.getAgentType(), status,
			output, error, now, now);
	}
}
