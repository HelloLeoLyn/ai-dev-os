package com.aidevos.orchestrator.validationplan;

import java.time.Instant;
import java.util.List;

/**
 * V1-B 执行结果模型。不存超长 stdout/stderr（仅 snippet）。
 */
public final class ValidationExecutionModels {

	private ValidationExecutionModels() {
	}

	public enum CheckExecutionStatus {
		SUCCESS, FAILED, SKIPPED
	}

	public record ValidationCheckResult(
			String checkType,
			CheckExecutionStatus status,
			String commandSummary,
			String workingDirectory,
			Integer exitCode,
			long durationMillis,
			String outputSnippet,
			String errorCode,
			String selectedTest,
			Instant startedAt,
			Instant finishedAt) {
	}

	public record ValidationRunResult(
			String runId,
			String taskId,
			String changeSetId,
			String planFingerprint,
			String changeFingerprint,
			String mode,
			String profile,
			ValidationStatus status,
			Instant startedAt,
			Instant finishedAt,
			boolean reused,
			List<ValidationCheckResult> checks) {

		public ValidationRunResult withReused() {
			return new ValidationRunResult(runId, taskId, changeSetId, planFingerprint,
				changeFingerprint, mode, profile, status, startedAt, finishedAt, true, checks);
		}
	}

	public enum ValidationStatus {
		PENDING, RUNNING, SUCCESS, FAILED, ERROR, REUSED
	}
}
