package com.aidevos.orchestrator.validation.browser;

import java.util.List;

public record BrowserStep(String stepId, String name, BrowserAction action, String selector,
		String value, Long timeoutMs, List<BrowserAssertion> assertions,
		boolean screenshotOnSuccess, boolean screenshotOnFailure) {
	public BrowserStep {
		assertions = assertions == null ? List.of() : List.copyOf(assertions);
	}
}
