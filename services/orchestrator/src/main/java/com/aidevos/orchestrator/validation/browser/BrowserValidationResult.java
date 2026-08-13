package com.aidevos.orchestrator.validation.browser;

import java.util.List;

import com.aidevos.orchestrator.validation.ValidationStatus;

public record BrowserValidationResult(String scenarioId, ValidationStatus status,
		List<BrowserStepResult> steps, String finalUrl, String errorMessage,
		List<String> artifactIds) {
	public BrowserValidationResult {
		steps = steps == null ? List.of() : List.copyOf(steps);
		artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
	}
}
