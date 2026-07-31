package com.aidevos.orchestrator.executor.command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandExecutorTest {

	private final CommandExecutor commandExecutor = new CommandExecutor();

	@TempDir
	Path temporaryDirectory;

	@Test
	void shouldExecuteCommandSuccessfully() {
		CommandResult result = commandExecutor.execute(List.of("echo", "hello"));

		assertTrue(result.isSuccess());
		assertEquals(0, result.getExitCode());
		assertTrue(result.getOutput().contains("hello"));
	}

	@Test
	void shouldReturnFailureForInvalidCommand() {
		CommandResult result = commandExecutor.execute(List.of("command-that-does-not-exist"));

		assertFalse(result.isSuccess());
		assertEquals(-1, result.getExitCode());
		assertFalse(result.getError().isBlank());
	}

	@Test
	void shouldExecuteCommandInConfiguredWorkingDirectory() throws IOException {
		CommandOptions options = new CommandOptions();
		options.setCommand(List.of("pwd"));
		options.setWorkingDirectory(temporaryDirectory.toString());

		CommandResult result = commandExecutor.execute(options);

		assertTrue(result.isSuccess());
		assertEquals(temporaryDirectory.toRealPath().toString(), result.getOutput().trim());
	}
}
