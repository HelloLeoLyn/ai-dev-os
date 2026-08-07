package com.aidevos.orchestrator.metrics;

/**
 * Operational metrics snapshot: counts derived from the existing repositories
 * and registries. No external metrics dependency is required.
 */
public record MetricsSnapshot(
		int agents,
		int tasks,
		int runningJobs,
		int failedJobs,
		int recoveryJobs,
		int memoryRecords,
		int plugins) {
}
