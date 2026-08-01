package com.aidevos.orchestrator.execution.query;

public record ExecutionRecordSummary(String id, String taskId, String agentName,
		String status, String message) {
}
