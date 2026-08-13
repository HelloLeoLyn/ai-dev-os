package com.aidevos.orchestrator.validation.browser;

import java.util.List;

public record BrowserScenario(String scenarioId, String name, String targetUrl, boolean required,
		List<BrowserStep> steps) {
	public BrowserScenario {
		steps = steps == null ? List.of() : List.copyOf(steps);
	}
}
