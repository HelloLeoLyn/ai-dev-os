package com.aidevos.orchestrator.tool.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tools.mcp")
public class McpProperties {

	private boolean enabled;
	private String providerId = "filesystem";
	private List<String> command = new ArrayList<>();
	private String workingDirectory;
	private Duration requestTimeout = Duration.ofSeconds(30);

	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public String getProviderId() { return providerId; }
	public void setProviderId(String providerId) { this.providerId = providerId; }
	public List<String> getCommand() { return command; }
	public void setCommand(List<String> command) { this.command = command; }
	public String getWorkingDirectory() { return workingDirectory; }
	public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }
	public Duration getRequestTimeout() { return requestTimeout; }
	public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
}
