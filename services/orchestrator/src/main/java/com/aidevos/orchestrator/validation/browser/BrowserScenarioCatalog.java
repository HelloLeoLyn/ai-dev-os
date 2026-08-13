package com.aidevos.orchestrator.validation.browser;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class BrowserScenarioCatalog {
	private final BrowserScenarioProperties properties;
	public BrowserScenarioCatalog(BrowserScenarioProperties properties) { this.properties = properties; }
	public BrowserScenario require(String scenarioId) {
		if (scenarioId == null || scenarioId.isBlank()) return null;
		Map<String, BrowserScenario> scenarios = properties.getScenarios().stream()
			.collect(Collectors.toMap(BrowserScenario::scenarioId, Function.identity()));
		BrowserScenario scenario = scenarios.get(scenarioId);
		if (scenario == null) throw new IllegalArgumentException("Unknown browser scenario: " + scenarioId);
		return scenario;
	}
}
