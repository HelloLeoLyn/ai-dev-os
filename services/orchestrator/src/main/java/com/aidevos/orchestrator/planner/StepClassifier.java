package com.aidevos.orchestrator.planner;

import java.util.Map;

import com.aidevos.orchestrator.execution.ValidationProfile;
import com.aidevos.orchestrator.execution.tool.DeterministicTool;
import com.aidevos.orchestrator.plan.StepExecutionType;

/**
 * Deterministic step classification applied by the planner so that real plans
 * use the deterministic executor instead of an LLM whenever possible.
 *
 * The planner only proposes an execution type; the backend still enforces the
 * tool allowlist ({@link DeterministicTool}) at validation and execution time.
 * Steps outside the allowlist are downgraded to AI_STEP instead of silently
 * running as tools.
 */
public final class StepClassifier {

	private StepClassifier() {
	}

	/**
	 * Classifies a proposed step. TOOL_STEP is only honored for the reserved
	 * "deterministic" provider with an allowlisted tool name; anything else is
	 * downgraded to AI_STEP. HUMAN_GATE and SYSTEM_STEP are honored as declared.
	 * A missing execution type (legacy plans) defaults to AI_STEP.
	 */
	public static StepExecutionType classify(StepExecutionType declared, String toolProviderId,
			String toolName) {
		StepExecutionType effective = declared == null ? StepExecutionType.AI_STEP : declared;
		if (effective == StepExecutionType.TOOL_STEP) {
			return isDeterministicTool(toolProviderId, toolName)
				? StepExecutionType.TOOL_STEP : StepExecutionType.AI_STEP;
		}
		if (effective == StepExecutionType.HUMAN_GATE
				|| effective == StepExecutionType.SYSTEM_STEP) {
			return effective;
		}
		return isDeterministicTool(toolProviderId, toolName)
			? StepExecutionType.TOOL_STEP : StepExecutionType.AI_STEP;
	}

	/**
	 * A step only runs deterministically when it declares the reserved
	 * "deterministic" provider and an allowlisted tool name.
	 */
	public static boolean isDeterministicTool(String toolProviderId, String toolName) {
		return "deterministic".equalsIgnoreCase(toolProviderId)
			&& DeterministicTool.fromName(toolName).isPresent();
	}

	/**
	 * Validation profile decision. The planner never invents FULL: it only
	 * honors an explicit validationProfile supplied by the user in the
	 * structured input. Everything else defaults to FAST.
	 */
	public static ValidationProfile validationProfile(Map<String, Object> structuredInput) {
		Object value = structuredInput == null ? null : structuredInput.get("validationProfile");
		if (value instanceof String name && !name.isBlank()) {
			try {
				return ValidationProfile.valueOf(name.trim().toUpperCase());
			}
			catch (IllegalArgumentException ignored) {
				return ValidationProfile.FAST;
			}
		}
		return ValidationProfile.FAST;
	}

	/**
	 * Goal-aware profile decision. An explicit structuredInput profile always
	 * wins. Otherwise FULL is only chosen for an explicitly stated full
	 * regression/release acceptance goal and TARGETED for explicit cross-module
	 * work; ordinary development goals stay FAST.
	 */
	public static ValidationProfile validationProfile(String goal,
			Map<String, Object> structuredInput) {
		ValidationProfile explicit = validationProfile(structuredInput);
		if (explicit != ValidationProfile.FAST || goal == null || goal.isBlank()) {
			return explicit;
		}
		String text = goal.toLowerCase();
		if (containsAny(text, "完整回归", "大版本验收", "full regression", "release 验收")) {
			return ValidationProfile.FULL;
		}
		if (containsAny(text, "跨模块", "cross-module", "cross module")) {
			return ValidationProfile.TARGETED;
		}
		return ValidationProfile.FAST;
	}

	private static boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}
}
