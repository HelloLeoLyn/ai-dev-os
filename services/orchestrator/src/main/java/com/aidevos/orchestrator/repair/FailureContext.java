package com.aidevos.orchestrator.repair;

import java.time.Instant;

/**
 * Snapshot of a failure that triggers a repair: the task, the workspace and
 * test involved, the failure detail and the git diff at failure time. The
 * sourceType/sourceId pair records where the failure came from (TEST_FAILURE
 * from a failed test plan, CI_FAILURE from a failed CI run) so a repair can
 * be traced back to its origin.
 */
public record FailureContext(
		String taskId,
		String workspaceId,
		String testId,
		String errorMessage,
		String stackTrace,
		String testReport,
		String gitDiff,
		String sourceType,
		String sourceId,
		String commitHash,
		String branch,
		int changedFiles,
		Instant createdAt) {
}
