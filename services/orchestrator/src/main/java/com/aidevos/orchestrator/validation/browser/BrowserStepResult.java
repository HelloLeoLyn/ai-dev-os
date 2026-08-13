package com.aidevos.orchestrator.validation.browser;

import com.aidevos.orchestrator.validation.ValidationStatus;

public record BrowserStepResult(String stepId, String name, ValidationStatus status,
		long durationMs, String summary, String errorMessage, String finalUrl,
		String screenshotArtifactId) { }
