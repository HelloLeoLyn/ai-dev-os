package com.aidevos.orchestrator.dashboard;

import java.time.Instant;

import com.aidevos.orchestrator.job.JobStatus;

public record RecentJobSummary(String id, String taskId, JobStatus status,
		Instant createdAt, Instant startedAt, Instant completedAt,
		String executionRecordId, String resultSummary, String errorMessage) {
}
