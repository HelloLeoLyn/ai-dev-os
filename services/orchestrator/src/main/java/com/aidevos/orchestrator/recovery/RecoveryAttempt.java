package com.aidevos.orchestrator.recovery;

import java.time.Instant;

/**
 * 统一 Recovery Attempt budget 记录（taskId + fingerprint + action 共享）。
 * 必须持久化：重启后 budget 不重置。
 */
public record RecoveryAttempt(
		String attemptId,
		String taskId,
		String fingerprint,
		RecoveryAction action,
		String scopeId,
		int attemptNumber,
		int maxAttempts,
		boolean automatic,
		AttemptStatus status,
		Instant startedAt,
		Instant finishedAt,
		String result,
		String failureReason,
		long backoffSeconds) {

	public enum AttemptStatus {
		PENDING, RUNNING, SUCCEEDED, FAILED, EXHAUSTED
	}
}
