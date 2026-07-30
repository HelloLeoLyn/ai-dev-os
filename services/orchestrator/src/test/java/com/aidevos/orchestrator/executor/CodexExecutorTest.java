package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandResult;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexExecutorTest {

	@Test
	void shouldReturnCodexType() {
		assertEquals("codex", new CodexExecutor(mock(CommandExecutor.class)).getType());
	}

	@Test
	void shouldExecuteCodexCommandAndConvertSuccessfulResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("Codex output");
		List<String> command = List.of("codex", "exec", "Implement a new feature");
		when(commandExecutor.execute(command)).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Implement a new feature");

		ExecutionResult result = new CodexExecutor(commandExecutor).execute(context);

		verify(commandExecutor).execute(command);
		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Codex output", result.getOutput());
	}

	@Test
	void shouldConvertFailedCommandResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(false);
		commandResult.setExitCode(1);
		commandResult.setError("Codex failed");
		List<String> command = List.of("codex", "exec", "Invalid task");
		when(commandExecutor.execute(command)).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Invalid task");

		ExecutionResult result = new CodexExecutor(commandExecutor).execute(context);

		verify(commandExecutor).execute(command);
		assertFalse(result.isSuccess());
		assertEquals("Codex failed", result.getMessage());
	}
}
