package com.aidevos.orchestrator.testagent;

/**
 * Outcome of running a test command through the command execution layer.
 */
public record TestCommandResult(int exitCode, String stdout, String stderr) {

	public boolean succeeded() {
		return exitCode == 0;
	}

	public String output() {
		StringBuilder builder = new StringBuilder();
		if (stdout != null && !stdout.isBlank()) {
			builder.append(stdout);
		}
		if (stderr != null && !stderr.isBlank()) {
			if (!builder.isEmpty()) {
				builder.append(System.lineSeparator());
			}
			builder.append(stderr);
		}
		return builder.toString();
	}
}
