package com.aidevos.orchestrator.execution.query;

import com.aidevos.orchestrator.execution.ExecutionReport;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import java.util.List;
import java.time.Instant;

public record ExecutionRecordDetail(String id, String taskId, String agentName, String executorName,
		String status, String message, String output, ExecutionReport report,
		List<ExecutionArtifact> artifacts, String executionId, String jobId,
		String planRunId, String stepRunId, String attemptId,
		String workspace, String sandbox, String approvalId, String branch,
		String beforeHead, String afterHead, Integer exitCode, String codexThreadId,
		Instant startedAt, Instant completedAt) {
}
