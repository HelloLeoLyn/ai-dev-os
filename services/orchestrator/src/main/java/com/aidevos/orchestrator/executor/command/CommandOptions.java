package com.aidevos.orchestrator.executor.command;

import java.util.List;
import java.time.Duration;

public class CommandOptions {

	private List<String> command;
	private String workingDirectory;
	private Duration timeout;

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
}
