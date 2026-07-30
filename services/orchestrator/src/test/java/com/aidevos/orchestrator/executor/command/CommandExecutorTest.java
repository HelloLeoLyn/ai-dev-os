package com.aidevos.orchestrator.executor.command;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandExecutorTest {

	private final CommandExecutor commandExecutor = new CommandExecutor();

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
}
