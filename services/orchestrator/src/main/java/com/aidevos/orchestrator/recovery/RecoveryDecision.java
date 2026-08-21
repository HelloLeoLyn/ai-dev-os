package com.aidevos.orchestrator.recovery;

import java.time.Instant;

/**
 * 统一 Recovery Decision：Diagnosis → Decision（不依赖 LLM）。
 */
public record RecoveryDecision(
		String taskId,
		String diagnosisFingerprint,
		String failureSource,
		String failureStage,
		String failureCode,
		String failureCategory,
		RecoveryAction action,
		boolean automaticAllowed,
		String reason,
		int attempt,
		int maxAttempts,
		long backoffSeconds,
		String decisionSource,
		Instant createdAt) {
}
