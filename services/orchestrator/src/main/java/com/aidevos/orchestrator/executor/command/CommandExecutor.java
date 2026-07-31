package com.aidevos.orchestrator.executor.command;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Component;

@Component
public class CommandExecutor {

	public CommandResult execute(List<String> command) {
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		return execute(options);
	}

	public CommandResult execute(CommandOptions options) {
		Process process = null;
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(options.getCommand());
			if (options.getWorkingDirectory() != null && !options.getWorkingDirectory().isBlank()) {
				processBuilder.directory(new File(options.getWorkingDirectory()));
			}
			process = processBuilder.start();
			Process runningProcess = process;

			try (ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
				Future<String> output = streamExecutor.submit(() -> read(runningProcess.getInputStream()));
				Future<String> error = streamExecutor.submit(() -> read(runningProcess.getErrorStream()));
				int exitCode = runningProcess.waitFor();

				return result(exitCode == 0, exitCode, output.get(), error.get());
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			if (process != null) {
				process.destroyForcibly();
			}
			return result(false, -1, "", ex.getMessage());
		}
		catch (IOException | ExecutionException ex) {
			return result(false, -1, "", ex.getMessage());
		}
	}

	private String read(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

	private CommandResult result(boolean success, int exitCode, String output, String error) {
		CommandResult result = new CommandResult();
		result.setSuccess(success);
		result.setExitCode(exitCode);
		result.setOutput(output);
		result.setError(error);
		return result;
	}
}
