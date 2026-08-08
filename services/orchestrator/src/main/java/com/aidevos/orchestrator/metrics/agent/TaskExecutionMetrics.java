package com.aidevos.orchestrator.metrics.agent;

import java.util.List;

/**
 * Execution statistics for one task: executions, durations and the associated
 * repair/change summary derived from existing records.
 */
public record TaskExecutionMetrics(
		String taskId,
		String taskStatus,
		int executionCount,
		int successCount,
		int failedCount,
		long totalDurationMillis,
		long averageDurationMillis,
		int repairCount,
		int retryCount,
		int changeCount,
		int approvedChanges,
		int rejectedChanges,
		double reviewPassRate,
		List<AgentExecutionMetric> executions) {
}
