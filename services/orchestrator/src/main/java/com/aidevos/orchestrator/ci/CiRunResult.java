package com.aidevos.orchestrator.ci;

/**
 * Polled CI status of one pipeline: the mapped CiStatus and the report url.
 */
public record CiRunResult(CiStatus status, String reportUrl) {
}
