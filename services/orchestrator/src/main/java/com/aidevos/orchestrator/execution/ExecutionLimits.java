package com.aidevos.orchestrator.execution;

import java.util.Map;

/**
 * Unified execution ceilings for a work package. Every counter is persisted in
 * the run execution state; when any ceiling is reached the run stops with
 * NEEDS_INTERVENTION and no new job, LLM call or tool execution is started.
 */
public record ExecutionLimits(int maxTotalAttempts, int maxAiAttempts, int maxToolAttempts,
		int maxRepairAttempts, int maxReplanAttempts, int maxConsecutiveFailures) {

	public static final int DEFAULT_MAX_TOTAL_ATTEMPTS = 20;
	public static final int DEFAULT_MAX_AI_ATTEMPTS = 10;
	public static final int DEFAULT_MAX_TOOL_ATTEMPTS = 10;
	public static final int DEFAULT_MAX_REPAIR_ATTEMPTS = 2;
	public static final int DEFAULT_MAX_REPLAN_ATTEMPTS = 2;
	public static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 3;

	public static ExecutionLimits defaults() {
		return new ExecutionLimits(DEFAULT_MAX_TOTAL_ATTEMPTS, DEFAULT_MAX_AI_ATTEMPTS,
			DEFAULT_MAX_TOOL_ATTEMPTS, DEFAULT_MAX_REPAIR_ATTEMPTS, DEFAULT_MAX_REPLAN_ATTEMPTS,
			DEFAULT_MAX_CONSECUTIVE_FAILURES);
	}

	public static ExecutionLimits resolve(Map<String, Object> plannerMetadata) {
		if (plannerMetadata == null || plannerMetadata.isEmpty()) {
			return defaults();
		}
		return new ExecutionLimits(
			intValue(plannerMetadata.get("maxTotalAttempts"), DEFAULT_MAX_TOTAL_ATTEMPTS),
			intValue(plannerMetadata.get("maxAiAttempts"), DEFAULT_MAX_AI_ATTEMPTS),
			intValue(plannerMetadata.get("maxToolAttempts"), DEFAULT_MAX_TOOL_ATTEMPTS),
			intValue(plannerMetadata.get("maxRepairAttempts"), DEFAULT_MAX_REPAIR_ATTEMPTS),
			intValue(plannerMetadata.get("maxReplanAttempts"), DEFAULT_MAX_REPLAN_ATTEMPTS),
			intValue(plannerMetadata.get("maxConsecutiveFailures"),
				DEFAULT_MAX_CONSECUTIVE_FAILURES));
	}

	/**
	 * The first exceeded limit name, or null when every counter is below its
	 * ceiling. A reached ceiling blocks all further automatic execution.
	 */
	public String exceeded(RunExecutionState state) {
		if (state.getTotalAttempts() >= maxTotalAttempts) {
			return "maxTotalAttempts";
		}
		if (state.getAiAttempts() >= maxAiAttempts) {
			return "maxAiAttempts";
		}
		if (state.getToolAttempts() >= maxToolAttempts) {
			return "maxToolAttempts";
		}
		if (state.getRepairAttempts() >= maxRepairAttempts) {
			return "maxRepairAttempts";
		}
		if (state.getReplanAttempts() >= maxReplanAttempts) {
			return "maxReplanAttempts";
		}
		if (state.getConsecutiveFailures() >= maxConsecutiveFailures) {
			return "maxConsecutiveFailures";
		}
		return null;
	}

	private static int intValue(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text);
			}
			catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}
}
