package com.aidevos.orchestrator.ci;

/**
 * Result of associating a commit with its CI pipeline: the provider pipeline
 * id and the report url.
 */
public record CiTriggerResult(String pipelineId, String reportUrl) {
}
