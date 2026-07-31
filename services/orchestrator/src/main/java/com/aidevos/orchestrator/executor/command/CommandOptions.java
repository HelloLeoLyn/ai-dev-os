package com.aidevos.orchestrator.executor.command;

import java.util.List;

public class CommandOptions {

	private List<String> command;
	private String workingDirectory;

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
}
