package com.aidevos.orchestrator.planner.replan;

import java.util.Locale;

import com.aidevos.orchestrator.execution.ExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class FailureClassifier {

	public FailureClassification classify(String reason, ExecutionResult result,
			boolean artifactMissing) {
		if (artifactMissing) {
			return FailureClassification.ARTIFACT_MISSING;
		}
		String text = ((reason == null ? "" : reason) + " "
			+ (result == null || result.getMessage() == null ? "" : result.getMessage()))
			.toLowerCase(Locale.ROOT);
		if (contains(text, "user change", "user input", "requirements changed")) {
			return FailureClassification.USER_REQUIRED_CHANGE;
		}
		if (contains(text, "plan invalid", "invalid plan", "dependency cycle")) {
			return FailureClassification.PLAN_INVALID;
		}
		if (contains(text, "validation", "validator")) {
			return FailureClassification.VALIDATION_FAILED;
		}
		if (contains(text, "agent unavailable", "unknown agent", "agent disabled")) {
			return FailureClassification.AGENT_UNAVAILABLE;
		}
		if (result != null && result.getMetadata() != null
				&& result.getMetadata().containsKey("toolResultCode")) {
			return FailureClassification.TOOL_ERROR;
		}
		if (contains(text, "tool error", "tool failed", "unknown tool")) {
			return FailureClassification.TOOL_ERROR;
		}
		if (contains(text, "timeout", "temporarily", "connection reset", "rate limit")) {
			return FailureClassification.TRANSIENT;
		}
		return FailureClassification.UNKNOWN;
	}

	private boolean contains(String value, String... candidates) {
		for (String candidate : candidates) {
			if (value.contains(candidate)) {
				return true;
			}
		}
		return false;
	}
}
