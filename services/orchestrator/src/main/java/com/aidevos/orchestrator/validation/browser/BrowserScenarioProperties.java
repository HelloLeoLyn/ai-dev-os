package com.aidevos.orchestrator.validation.browser;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aidevos.browser-validation")
public class BrowserScenarioProperties {
	private List<BrowserScenario> scenarios = new ArrayList<>();
	private List<String> allowedBaseUrls = new ArrayList<>();
	public List<BrowserScenario> getScenarios() { return scenarios; }
	public void setScenarios(List<BrowserScenario> scenarios) { this.scenarios = scenarios == null ? new ArrayList<>() : new ArrayList<>(scenarios); }
	public List<String> getAllowedBaseUrls() { return allowedBaseUrls; }
	public void setAllowedBaseUrls(List<String> allowedBaseUrls) { this.allowedBaseUrls = allowedBaseUrls == null ? new ArrayList<>() : new ArrayList<>(allowedBaseUrls); }
}
