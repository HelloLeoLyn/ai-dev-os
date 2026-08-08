package com.aidevos.orchestrator.repair;

import java.time.Instant;

/**
 * Snapshot of a failed test that triggers a repair: the task, the workspace
 * and test involved, the failure detail and the git diff at failure time.
 * Built by RepairCoordinator from the latest failed TestPlan and the
 * WorkspaceService git state.
 */
public record FailureContext(
		String taskId,
		String workspaceId,
		String testId,
		String errorMessage,
		String stackTrace,
		String testReport,
		String gitDiff,
		Instant createdAt) {
}
