package com.aidevos.orchestrator.ci;

/**
 * CI report snapshot: the report url and a short summary of the run.
 */
public record CiReport(String reportUrl, String summary) {
}
