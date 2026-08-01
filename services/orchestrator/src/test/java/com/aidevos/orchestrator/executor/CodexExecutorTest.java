package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Implement a new feature");
		context.setWorkspace("/workspace/project");

		ExecutionResult result = new CodexExecutor(commandExecutor).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(command, optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
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
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Invalid task");

		ExecutionResult result = new CodexExecutor(commandExecutor).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(command, optionsCaptor.getValue().getCommand());
		assertNull(optionsCaptor.getValue().getWorkingDirectory());
		assertFalse(result.isSuccess());
		assertEquals("Codex failed", result.getMessage());
	}

	@Test
	void shouldApplyExecutorConfiguration() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");
		context.setWorkspace("/default/workspace");
		context.setParameters(Map.of(
			"workspace", "/configured/workspace",
			"model", "gpt-5.6-codex"));

		new CodexExecutor(commandExecutor).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("codex", "exec", "--model", "gpt-5.6-codex",
			"Implement a new feature"), optionsCaptor.getValue().getCommand());
		assertEquals("/configured/workspace", optionsCaptor.getValue().getWorkingDirectory());
	}
}
