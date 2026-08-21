package com.aidevos.orchestrator.validationplan;

import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 生产默认：AI Selector 未接入（V1-A 只做 planning 架构，真实 LLM 在后续 WP 接入）。
 * isAvailable=false → AUTO/VERIFY 自动 fallback 到 Local broader validation，
 * Validation Planning 永不因 AI 不可用而失败。
 */
@Component
@ConditionalOnProperty(name = "aidevos.validation.ai-selector", havingValue = "disabled",
	matchIfMissing = true)
public class DisabledAiValidationSelector implements AiValidationSelector {

	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public AiPlan suggest(AiValidationInput input) {
		throw new AiUnavailableException("AI validation selector is not configured");
	}

	public static class AiUnavailableException extends RuntimeException {
		public AiUnavailableException(String message) {
			super(message);
		}
	}
}
