package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.execution.tool.DeterministicTool;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.springframework.stereotype.Component;

/**
 * Classifies failures without an LLM: tool kind, exit code and captured output
 * are mapped to a FailureClass. Only UNKNOWN / CODE_LOGIC_ERROR should ever
 * reach AI diagnosis.
 */
@Component
public class FailureClassifier {

	public FailureClass classify(DeterministicTool tool, CommandResult result) {
		if (result == null || result.isSuccess()) {
			return null;
		}
		String output = combined(result);
		String lower = output == null ? "" : output.toLowerCase();
		if (containsAny(lower, "authentication failed", "could not read username",
				"permission denied (publickey)", "credentials", "401 unauthorized",
				"403 forbidden", "invalid api key")) {
			return FailureClass.CREDENTIAL_MISSING;
		}
		if (tool == DeterministicTool.HTTP_HEALTH) {
			return FailureClass.HEALTH_CHECK_FAILED;
		}
		if (tool == DeterministicTool.GIT || tool == DeterministicTool.WORKSPACE) {
			if (containsAny(lower, "conflict", "your local changes would be overwritten")) {
				return FailureClass.GIT_CONFLICT;
			}
			if (containsAny(lower, "could not resolve host", "connection refused",
					"unable to access", "network is unreachable", "timed out")) {
				return FailureClass.NETWORK_ERROR;
			}
		}
		if (tool == DeterministicTool.MAVEN || tool == DeterministicTool.NPM) {
			if (containsAny(output, "Tests run:", "Failures:", "Errors:")
					|| containsAny(lower, "test failed", "assertionerror", "failing test")) {
				return FailureClass.TEST_FAILED;
			}
			if (containsAny(lower, "build failure", "npm err!", "error ts",
					"compilation failed", "cannot find symbol")) {
				return FailureClass.BUILD_FAILED;
			}
		}
		if (containsAny(lower, "quota", "rate limit", "usage limit", "max tokens")) {
			return FailureClass.USAGE_LIMIT;
		}
		if (containsAny(lower, "model not found", "no such model", "unknown model")) {
			return FailureClass.MODEL_NOT_FOUND;
		}
		if (containsAny(lower, "approval required", "approval is required")) {
			return FailureClass.APPROVAL_REQUIRED;
		}
		if (containsAny(lower, "provider is disabled", "provider disabled")) {
			return FailureClass.PROVIDER_DISABLED;
		}
		return FailureClass.EXECUTOR_FAILED;
	}

	public FailureClass classify(DeterministicTool tool, Throwable failure) {
		String message = failure == null || failure.getMessage() == null
			? failure == null ? null : failure.getClass().getSimpleName()
			: failure.getMessage();
		String lower = message == null ? "" : message.toLowerCase();
		if (containsAny(lower, "timeout", "timed out", "connection refused", "connect exception",
				"could not resolve host", "network is unreachable", "unknownhost")) {
			return FailureClass.NETWORK_ERROR;
		}
		if (containsAny(lower, "quota", "rate limit", "usage limit", "max tokens")) {
			return FailureClass.USAGE_LIMIT;
		}
		if (containsAny(lower, "credential", "authentication", "unauthorized", "api key")) {
			return FailureClass.CREDENTIAL_MISSING;
		}
		return FailureClass.UNKNOWN;
	}

	/**
	 * Whether a failure class may recover on retry. Permanent classes
	 * (credentials, missing model, disabled provider, required approval and
	 * git conflicts) are never retried; transient classes such as network or
	 * health-check failures are retried within the tool budget.
	 */
	public static boolean isRetryable(FailureClass failureClass) {
		return severity(failureClass) == FailureSeverity.L0_RECOVERABLE;
	}

	/**
	 * Maps a failure class onto the severity ladder. L0 is transient, L1 is
	 * AI-repairable, L2 needs a human decision, L3 requires human action and
	 * L4 is a system failure that stops immediately.
	 */
	public static FailureSeverity severity(FailureClass failureClass) {
		if (failureClass == null) {
			return FailureSeverity.L4_SYSTEM_FAILURE;
		}
		return switch (failureClass) {
			case NETWORK_ERROR, HEALTH_CHECK_FAILED ->
				FailureSeverity.L0_RECOVERABLE;
			case BUILD_FAILED, TEST_FAILED, CODE_LOGIC_ERROR, EXECUTOR_FAILED, UNKNOWN ->
				FailureSeverity.L1_AI_RECOVERABLE;
			case GIT_CONFLICT, APPROVAL_REQUIRED, QUALITY_GATE_APPROVAL, AMBIGUOUS_STATE ->
				FailureSeverity.L2_HUMAN_DECISION;
			case CREDENTIAL_MISSING, MODEL_NOT_FOUND, PROVIDER_DISABLED, PERMISSION_DENIED,
				REMOTE_AUTHORITY_REQUIRED, USAGE_LIMIT -> FailureSeverity.L3_HUMAN_REQUIRED;
			case STATE_CORRUPTION, DATABASE_UNAVAILABLE, UNKNOWN_FATAL ->
				FailureSeverity.L4_SYSTEM_FAILURE;
		};
	}

	/**
	 * The automatic response for a failure class: L0 retries the tool, L1
	 * retries or replans through AI, L2/L3 request a human and L4 stops.
	 */
	public static FailureResponse response(FailureClass failureClass) {
		if (failureClass == null) {
			return FailureResponse.STOP;
		}
		return switch (failureClass) {
			case NETWORK_ERROR, HEALTH_CHECK_FAILED -> FailureResponse.RETRY_TOOL;
			case CODE_LOGIC_ERROR -> FailureResponse.REPLAN_AI;
			case BUILD_FAILED, TEST_FAILED, EXECUTOR_FAILED, UNKNOWN -> FailureResponse.RETRY_AI;
			case GIT_CONFLICT, APPROVAL_REQUIRED, QUALITY_GATE_APPROVAL, AMBIGUOUS_STATE,
				CREDENTIAL_MISSING, MODEL_NOT_FOUND, PROVIDER_DISABLED, PERMISSION_DENIED,
				REMOTE_AUTHORITY_REQUIRED, USAGE_LIMIT -> FailureResponse.REQUEST_HUMAN;
			case STATE_CORRUPTION, DATABASE_UNAVAILABLE, UNKNOWN_FATAL -> FailureResponse.STOP;
		};
	}

	/**
	 * Human-recommended action for an intervention state. REQUEST_HUMAN /
	 * STOP classes get an actionable recommendation; nothing here is ever
	 * executed automatically.
	 */
	public static RecommendedAction recommendedAction(FailureClass failureClass,
			FailureResponse response) {
		if (response == FailureResponse.REPLAN_AI) {
			return RecommendedAction.REPLAN;
		}
		if (failureClass == null) {
			return RecommendedAction.ABORT;
		}
		return switch (failureClass) {
			case NETWORK_ERROR, HEALTH_CHECK_FAILED -> RecommendedAction.CHECK_NETWORK;
			case CREDENTIAL_MISSING -> RecommendedAction.FIX_CREDENTIAL;
			case STATE_CORRUPTION, DATABASE_UNAVAILABLE, UNKNOWN_FATAL ->
				RecommendedAction.ABORT;
			case BUILD_FAILED, TEST_FAILED, CODE_LOGIC_ERROR, EXECUTOR_FAILED, UNKNOWN ->
				RecommendedAction.REVIEW_CODE;
			case USAGE_LIMIT, GIT_CONFLICT, APPROVAL_REQUIRED, QUALITY_GATE_APPROVAL,
				AMBIGUOUS_STATE, PERMISSION_DENIED, REMOTE_AUTHORITY_REQUIRED,
				MODEL_NOT_FOUND, PROVIDER_DISABLED -> RecommendedAction.RETRY_MANUALLY;
		};
	}

	/**
	 * Classifies a free-form failure message without an LLM. Used by the
	 * scheduler guardrail for job failures that carry no explicit class.
	 */
	public static FailureClass classifyMessage(String message) {
		String lower = message == null ? "" : message.toLowerCase();
		if (containsAny(lower, "tests run:", "failures:", "test failed", "assertionerror")) {
			return FailureClass.TEST_FAILED;
		}
		if (containsAny(lower, "build failure", "cannot find symbol", "compilation failed",
				"error ts")) {
			return FailureClass.BUILD_FAILED;
		}
		if (containsAny(lower, "conflict", "local changes would be overwritten")) {
			return FailureClass.GIT_CONFLICT;
		}
		if (containsAny(lower, "permission denied")) {
			return FailureClass.PERMISSION_DENIED;
		}
		if (containsAny(lower, "timeout", "timed out", "connection refused", "could not resolve host",
				"network is unreachable", "unable to access")) {
			return FailureClass.NETWORK_ERROR;
		}
		if (containsAny(lower, "quota", "rate limit", "usage limit", "max tokens")) {
			return FailureClass.USAGE_LIMIT;
		}
		if (containsAny(lower, "credential", "authentication", "unauthorized", "invalid api key")) {
			return FailureClass.CREDENTIAL_MISSING;
		}
		if (containsAny(lower, "model not found", "unknown model", "no such model")) {
			return FailureClass.MODEL_NOT_FOUND;
		}
		if (containsAny(lower, "provider is disabled", "provider disabled")) {
			return FailureClass.PROVIDER_DISABLED;
		}
		if (containsAny(lower, "approval required")) {
			return FailureClass.APPROVAL_REQUIRED;
		}
		if (containsAny(lower, "state corruption", "corrupt state")) {
			return FailureClass.STATE_CORRUPTION;
		}
		if (containsAny(lower, "database unavailable", "database connection")) {
			return FailureClass.DATABASE_UNAVAILABLE;
		}
		if (containsAny(lower, "executor failed")) {
			return FailureClass.EXECUTOR_FAILED;
		}
		return FailureClass.UNKNOWN;
	}

	private static String combined(CommandResult result) {
		String output = result.getOutput();
		String error = result.getError();
		if (output == null) {
			return error;
		}
		if (error == null) {
			return output;
		}
		return output + "\n" + error;
	}

	private static boolean containsAny(String text, String... candidates) {
		if (text == null) {
			return false;
		}
		for (String candidate : candidates) {
			if (text.contains(candidate)) {
				return true;
			}
		}
		return false;
	}
}
