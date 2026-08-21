package com.aidevos.orchestrator.validationplan;

import java.util.List;

import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.RiskLevel;

/**
 * AI Selector 边界：V1 AI 输入严格压缩（只给 goal/changed files/diff summary/module/
 * local candidates/受限 test catalog/risk/confidence），AI 只能从现有 catalog 与允许的
 * check type 中补充 checks，禁止生成任意 shell command。
 *
 * AI 不可用（timeout/rate limit/unavailable/auth）时必须由调用方 fallback 到
 * Local broader validation——Validation Planning 不允许因 AI 失败而失败。
 */
public interface AiValidationSelector {

	boolean isAvailable();

	AiPlan suggest(AiValidationInput input);

	/** 可观测性：实际使用的 provider id（不可用/未配置时返回空串）。 */
	default String providerId() {
		return "";
	}

	/** 可观测性：实际使用的 model id。 */
	default String modelId() {
		return "";
	}

	record AiValidationInput(String taskGoal, List<String> changedFiles, String diffSummary,
			String module, List<String> localCandidateTests, List<String> testCatalog,
			RiskLevel localRisk, ConfidenceLevel localConfidence) {
	}
}
