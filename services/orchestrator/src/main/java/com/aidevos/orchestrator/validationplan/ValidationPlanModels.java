package com.aidevos.orchestrator.validationplan;

import java.time.Instant;
import java.util.List;

/**
 * VALIDATION-CENTER-V1-A 模型。
 *
 * 只生成 ValidationPlan（不接执行链）。AI 只允许补充 checks，
 * mandatory checks 永不可被删除。
 */
public final class ValidationPlanModels {

	private ValidationPlanModels() {
	}

	public enum ValidationMode {
		LOCAL, AI, AUTO, VERIFY
	}

	public enum RiskLevel {
		LOW, MEDIUM, HIGH
	}

	public enum ConfidenceLevel {
		HIGH, MEDIUM, LOW
	}

	public enum CheckType {
		GIT_DIFF_CHECK,
		BACKEND_COMPILE,
		MAVEN_TARGETED_TEST,
		MAVEN_MODULE_TEST,
		FRONTEND_TYPECHECK,
		FRONTEND_TARGETED_TEST,
		FRONTEND_BUILD
	}

	public enum CheckSource {
		MANDATORY, LOCAL, AI, MERGED
	}

	public record ValidationCheck(CheckType type, String tool, String workingDirectory,
			List<String> arguments, boolean required, String reason, CheckSource source,
			int timeoutSeconds) {
	}

	/** 本地确定性计划（0 LLM）。 */
	public record LocalPlan(RiskLevel risk, ConfidenceLevel confidence,
			List<ValidationCheck> checks) {
	}

	/** AI 补充计划（仅 additional checks）。 */
	public record AiPlan(ConfidenceLevel confidence, List<ValidationCheck> checks) {
	}

	/** 最终计划：mandatory + local（默认保留）+ AI additional（并集，绝不缩小）。 */
	public record ValidationPlan(String taskId, String changeSetId, ValidationMode mode,
			String profile, RiskLevel risk, ConfidenceLevel confidence,
			List<ValidationCheck> checks, LocalPlan localPlan, AiPlan aiPlan,
			List<String> disagreements, boolean fallbackUsed, Instant generatedAt) {
	}
}
