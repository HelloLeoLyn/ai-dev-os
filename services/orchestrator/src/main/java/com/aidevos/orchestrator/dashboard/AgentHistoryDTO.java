package com.aidevos.orchestrator.dashboard;

import java.util.List;

/**
 * Agent execution history: recent executions, success/failure counts and the
 * most recent error message.
 */
public record AgentHistoryDTO(
		List<AgentExecutionSummary> recentExecutions,
		int successCount,
		int failedCount,
		String lastError) {

	public AgentHistoryDTO {
		recentExecutions = List.copyOf(recentExecutions);
	}
}
