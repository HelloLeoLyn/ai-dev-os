package com.aidevos.orchestrator.validation.provider;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.validation.ValidationStatus;

abstract class CommandValidationSupport {
	private final CommandExecutor executor;

	CommandValidationSupport(CommandExecutor executor) { this.executor = executor; }

	ValidationCheckResult run(List<String> command, String directory) {
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		options.setWorkingDirectory(directory);
		options.setTimeout(Duration.ofMinutes(20));
		long started = System.nanoTime();
		CommandResult result = executor.execute(options);
		long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("command", command);
		metadata.put("workingDirectory", directory);
		metadata.put("exitCode", result.getExitCode());
		metadata.put("durationMs", durationMs);
		String error = result.isSuccess() ? null : usefulError(result);
		return new ValidationCheckResult(result.isSuccess() ? ValidationStatus.SUCCESS
			: ValidationStatus.FAILED,
			result.isSuccess() ? "Command completed successfully" : "Command failed",
			error, result.getOutput(), result.getError(), List.of(), metadata);
	}

	private String usefulError(CommandResult result) {
		String value = result.getError();
		if (value == null || value.isBlank()) value = lastLines(result.getOutput(), 20);
		return value == null || value.isBlank() ? "Command exited with code " + result.getExitCode()
			: value.strip();
	}

	private String lastLines(String value, int count) {
		if (value == null || value.isBlank()) return value;
		String[] lines = value.split("\\R");
		return String.join(System.lineSeparator(), java.util.Arrays.copyOfRange(lines,
			Math.max(0, lines.length - count), lines.length));
	}
}
