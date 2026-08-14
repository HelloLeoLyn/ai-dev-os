package com.aidevos.orchestrator.executor.command;

import java.util.List;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommandOptions {

	private List<String> command;
	private String workingDirectory;
	private Duration timeout;
	private Map<String, String> environment = new LinkedHashMap<>();
	private boolean runtimeNetworkEnabled = true;

	public List<String> getCommand() {
		return command;
	}

	public void setCommand(List<String> command) {
		this.command = command;
	}

	public String getWorkingDirectory() {
		return workingDirectory;
	}

	public void setWorkingDirectory(String workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	public Duration getTimeout() { return timeout; }
	public void setTimeout(Duration timeout) { this.timeout = timeout; }
	public Map<String, String> getEnvironment() { return environment; }
	public void setEnvironment(Map<String, String> environment) {
		this.environment = environment == null ? new LinkedHashMap<>() : new LinkedHashMap<>(environment);
	}
	public boolean isRuntimeNetworkEnabled() { return runtimeNetworkEnabled; }
	public void setRuntimeNetworkEnabled(boolean runtimeNetworkEnabled) { this.runtimeNetworkEnabled = runtimeNetworkEnabled; }
}
