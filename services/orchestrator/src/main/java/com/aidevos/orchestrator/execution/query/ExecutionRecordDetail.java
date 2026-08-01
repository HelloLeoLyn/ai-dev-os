package com.aidevos.orchestrator.execution.query;

import com.aidevos.orchestrator.execution.ExecutionReport;

public record ExecutionRecordDetail(String id, String taskId, String agentName,
		String status, String message, String output, ExecutionReport report) {
}
