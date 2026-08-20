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
		if (failureClass == null) {
			return true;
		}
		return switch (failureClass) {
			case CREDENTIAL_MISSING, MODEL_NOT_FOUND, PROVIDER_DISABLED, APPROVAL_REQUIRED,
				GIT_CONFLICT -> false;
			default -> true;
		};
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
