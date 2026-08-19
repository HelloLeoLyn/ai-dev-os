package com.aidevos.orchestrator.executor.codex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodexErrorClassifierTest {

	private final CodexErrorClassifier classifier = new CodexErrorClassifier();

	@Test
	void mapsUsageLimitMessages() {
		assertEquals("USAGE_LIMIT", classifier.classify(
			"You've hit your usage limit. Please try again later.", null));
		assertEquals("USAGE_LIMIT", classifier.classify(null, "insufficient_quota"));
		assertEquals("USAGE_LIMIT", classifier.classify("429 Too Many Requests", null));
	}

	@Test
	void mapsCredentialFailures() {
		assertEquals("CREDENTIAL_MISSING", classifier.classify(
			"Invalid API key provided: env var OPENAI_API_KEY is not set", null));
		assertEquals("CREDENTIAL_MISSING", classifier.classify("authentication failed", null));
		assertEquals("CREDENTIAL_MISSING", classifier.classify("401 Unauthorized", null));
	}

	@Test
	void mapsModelAndProviderFailures() {
		assertEquals("MODEL_NOT_FOUND", classifier.classify("Model not found: deepseek-v4-flash", null));
		assertEquals("MODEL_NOT_FOUND", classifier.classify(null, "model_not_found"));
		assertEquals("MODEL_DISABLED", classifier.classify("model is disabled", null));
		assertEquals("PROVIDER_NOT_FOUND", classifier.classify("provider not found: deepseek", null));
		assertEquals("PROVIDER_DISABLED", classifier.classify("provider is disabled", null));
	}

	@Test
	void mapsExecutorFailures() {
		assertEquals("UNSUPPORTED_EXECUTOR", classifier.classify("unsupported executor type", null));
		assertEquals("MODEL_EXECUTOR_MISMATCH", classifier.classify("model_executor_mismatch", null));
		assertEquals("MODEL_EXECUTOR_MISMATCH", classifier.classify("executor mismatch", null));
	}

	@Test
	void fallsBackToExecutorFailedWithoutSwallowingFailure() {
		assertEquals("EXECUTOR_FAILED", classifier.classify(null, null));
		assertEquals("EXECUTOR_FAILED", classifier.classify("Reading additional input from stdin...", null));
		assertEquals("EXECUTOR_FAILED", classifier.classify("something unexpected broke", null));
	}
}
