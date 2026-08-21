package com.aidevos.orchestrator.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.aidevos.orchestrator.diagnosis.FailureCategory;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosis;
import com.aidevos.orchestrator.diagnosis.RecommendedAction;
import org.springframework.stereotype.Component;

/**
 * V1 Recovery Safety Policy（fail-safe，不依赖 LLM）。
 *
 * 规则表：
 * - transient 基础设施失败 → 有限自动 Retry（maxAttempts=1，绝不无限）
 * - Validation 类（TEST/BUILD/TYPECHECK/DIFF_FAILED）→ 默认 HUMAN_INTERVENTION
 *   （同样代码重跑通常仍失败）；仅 evidence 显示 transient tool 失败 → RETRY_VALIDATION
 * - 配置/权限/认证/模型类 → 绝不自动 Retry
 * - Delivery 基础设施失败 → RETRY_DELIVERY；业务 Gate BLOCK / 人工 → HUMAN_INTERVENTION
 */
@Component
public class RecoveryPolicy {

	public record PolicyEntry(RecoveryAction action, boolean automaticAllowed,
			int maxAttempts, long backoffSeconds) {
		static PolicyEntry auto(RecoveryAction action, int maxAttempts, long backoffSeconds) {
			return new PolicyEntry(action, true, maxAttempts, backoffSeconds);
		}

		static PolicyEntry human(RecoveryAction action) {
			return new PolicyEntry(action, false, 0, 0);
		}
	}

	private static final Map<String, PolicyEntry> BY_CODE = Map.ofEntries(
		// ---- 允许有限自动 Retry ----
		Map.entry("NETWORK_TRANSIENT", PolicyEntry.auto(RecoveryAction.RETRY_EXECUTION, 1, 5)),
		Map.entry("NETWORK_ERROR", PolicyEntry.auto(RecoveryAction.RETRY_EXECUTION, 1, 5)),
		Map.entry("RATE_LIMITED", PolicyEntry.auto(RecoveryAction.RETRY_EXECUTION, 1, 30)),
		Map.entry("USAGE_LIMIT", PolicyEntry.auto(RecoveryAction.RETRY_EXECUTION, 1, 30)),
		Map.entry("TEMPORARY_PROVIDER_FAILURE", PolicyEntry.auto(RecoveryAction.RETRY_EXECUTION, 1, 5)),
		Map.entry("TOOL_TIMEOUT", PolicyEntry.auto(RecoveryAction.RETRY_EXECUTION, 1, 5)),
		Map.entry("CI_TRANSIENT", PolicyEntry.auto(RecoveryAction.RETRY_DELIVERY, 1, 15)),
		// ---- Validation 类：默认人工（无意义自动重跑）----
		Map.entry("TEST_FAILED", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("BUILD_FAILED", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("TYPECHECK_FAILED", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("DIFF_CHECK_FAILED", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("VALIDATION_FAILED", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		// ---- 配置/权限/认证/模型：绝不自动 Retry ----
		Map.entry("PROVIDER_AUTHENTICATION_FAILED",
			PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("MODEL_NOT_FOUND", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("AGENT_DEFAULT_MODEL_MISSING",
			PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("TASK_MODE_CONFLICT", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("MODE_CONFLICT", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("SPRING_BEAN_CONFLICT", PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("WRONG_WORKING_DIRECTORY",
			PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("WORKING_DIRECTORY_INVALID",
			PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)),
		Map.entry("PERSISTED_REFERENCE_NOT_RESOLVABLE",
			PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION)));

	private static final List<String> TRANSIENT_HINTS = List.of(
		"timeout", "timed out", "connection refused", "connection reset", "network is unreachable",
		"unable to access", "rate limit", "quota", "temporarily unavailable", "503");

	/** 规则匹配：exact code → 分类兜底 → 默认人工。 */
	public PolicyEntry entryFor(FailureDiagnosis diagnosis) {
		String code = codeOf(diagnosis);
		PolicyEntry exact = BY_CODE.get(code);
		if (exact != null) {
			return exact;
		}
		// Validation 类带 transient 证据 → 允许一次 RETRY_VALIDATION
		if (isValidationCode(code) && hasTransientHint(diagnosis.evidence())) {
			return PolicyEntry.auto(RecoveryAction.RETRY_VALIDATION, 1, 5);
		}
		if (diagnosis.category() == FailureCategory.DELIVERY) {
			// 业务 Gate BLOCK / 人工要求 → 人工；其余 Delivery 基础设施失败 → 可恢复重试
			String reason = diagnosis.rootCause() == null ? "" : diagnosis.rootCause();
			if (reason.contains("Quality gate blocked") || reason.contains("requires approval")
					|| diagnosis.recommendedAction() == RecommendedAction.HUMAN_INTERVENTION) {
				return PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION);
			}
			return PolicyEntry.auto(RecoveryAction.RETRY_DELIVERY, 1, 5);
		}
		return PolicyEntry.human(RecoveryAction.HUMAN_INTERVENTION);
	}

	public RecoveryDecision decide(FailureDiagnosis diagnosis, int attempt, int maxAttempts) {
		PolicyEntry entry = entryFor(diagnosis);
		String reason = reasonFor(diagnosis, entry);
		return new RecoveryDecision(diagnosis.taskId(), diagnosis.fingerprint(),
			diagnosis.source(), diagnosis.stage(), diagnosis.errorCode(),
			diagnosis.category() == null ? "" : diagnosis.category().name(),
			entry.action(), entry.automaticAllowed(), reason, attempt, maxAttempts,
			entry.backoffSeconds(), "DETERMINISTIC_POLICY", Instant.now());
	}

	private String reasonFor(FailureDiagnosis diagnosis, PolicyEntry entry) {
		String reason = entry.automaticAllowed()
			? "Transient failure eligible for one automatic recovery"
			: "Failure requires human intervention (policy refuses automatic retry)";
		if (diagnosis.knownFailure()) {
			reason += " (known failure, occurrence=" + diagnosis.occurrenceCount() + ")";
		}
		return reason;
	}

	private String codeOf(FailureDiagnosis diagnosis) {
		if (diagnosis.errorCode() != null && !diagnosis.errorCode().isBlank()) {
			return diagnosis.errorCode().toUpperCase(Locale.ROOT);
		}
		return diagnosis.code() == null ? "UNKNOWN" : diagnosis.code().toUpperCase(Locale.ROOT);
	}

	private boolean isValidationCode(String code) {
		return List.of("TEST_FAILED", "BUILD_FAILED", "TYPECHECK_FAILED", "DIFF_CHECK_FAILED",
			"VALIDATION_FAILED").contains(code);
	}

	private boolean hasTransientHint(List<String> evidence) {
		if (evidence == null) {
			return false;
		}
		String joined = String.join(" ", evidence).toLowerCase(Locale.ROOT);
		return TRANSIENT_HINTS.stream().anyMatch(joined::contains);
	}
}
