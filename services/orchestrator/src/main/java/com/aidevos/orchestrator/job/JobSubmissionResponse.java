package com.aidevos.orchestrator.job;

public record JobSubmissionResponse(String jobId, String taskId, JobStatus status) {
}
