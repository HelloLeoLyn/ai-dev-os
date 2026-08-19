package com.aidevos.orchestrator.executor.codex;

import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Maps a structured Codex CLI failure (turn.failed error message) to a stable
 * execution error code. Classification is keyword based and conservative:
 * specific model/provider failures are recognized first, everything else falls
 * back to EXECUTOR_FAILED so a real failure is never reported as success.
 */
@Component
public class CodexErrorClassifier {

	private static final String USAGE_LIMIT = "USAGE_LIMIT";
	private static final String CREDENTIAL_MISSING = "CREDENTIAL_MISSING";
	private static final String MODEL_NOT_FOUND = "MODEL_NOT_FOUND";
	private static final String MODEL_DISABLED = "MODEL_DISABLED";
	private static final String PROVIDER_NOT_FOUND = "PROVIDER_NOT_FOUND";
	private static final String PROVIDER_DISABLED = "PROVIDER_DISABLED";
	private static final String UNSUPPORTED_EXECUTOR = "UNSUPPORTED_EXECUTOR";
	private static final String MODEL_EXECUTOR_MISMATCH = "MODEL_EXECUTOR_MISMATCH";
	private static final String EXECUTOR_FAILED = "EXECUTOR_FAILED";

	/**
	 * Classifies the effective failure text. Both the CLI error type and the
	 * message are folded into a single lower-cased haystack.
	 */
	public String classify(String failureMessage, String failureType) {
		String haystack = normalize(failureMessage) + " " + normalize(failureType);
		if (haystack.isEmpty()) {
			return EXECUTOR_FAILED;
		}
		if (containsAny(haystack, "usage limit", "usage_limit", "billing", "quota",
				"insufficient_quota", "429")) {
			return USAGE_LIMIT;
		}
		if (containsAny(haystack, "api key", "apikey", "authentication", "unauthorized",
				"invalid_api_key", "401", "credential", "environment variable")) {
			return CREDENTIAL_MISSING;
		}
		if (containsAny(haystack, "model_not_found", "model not found", "no model named",
				"unknown model", "model does not exist")) {
			return MODEL_NOT_FOUND;
		}
		if (containsAny(haystack, "model_disabled", "model disabled", "model is disabled")) {
			return MODEL_DISABLED;
		}
		if (containsAny(haystack, "provider_not_found", "provider not found", "unknown provider",
				"no provider")) {
			return PROVIDER_NOT_FOUND;
		}
		if (containsAny(haystack, "provider_disabled", "provider disabled", "provider is disabled")) {
			return PROVIDER_DISABLED;
		}
		if (containsAny(haystack, "unsupported executor")) {
			return UNSUPPORTED_EXECUTOR;
		}
		if (containsAny(haystack, "executor mismatch", "model_executor_mismatch")) {
			return MODEL_EXECUTOR_MISMATCH;
		}
		return EXECUTOR_FAILED;
	}

	private boolean containsAny(String haystack, String... needles) {
		for (String needle : needles) {
			if (haystack.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
