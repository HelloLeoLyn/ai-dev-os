package com.aidevos.orchestrator.metrics.agent;

import java.util.List;

/**
 * Agent detail view: the aggregate metrics plus the underlying executions.
 */
public record AgentMetricsDetail(
		AgentMetrics metrics,
		List<AgentExecutionMetric> executions) {
}
