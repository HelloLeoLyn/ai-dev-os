package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;

import java.util.List;

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

class OpenClawExecutorTest {

	@Test
	void shouldReturnOpenClawType() {
		assertEquals("openclaw", new OpenClawExecutor(mock(CommandExecutor.class)).getType());
	}

	@Test
	void shouldExecuteOpenClawCommandAndConvertSuccessfulResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("OpenClaw help");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setWorkspace("/workspace/project");

		ExecutionResult result = new OpenClawExecutor(commandExecutor).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("openclaw", "--help"), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("OpenClaw help", result.getOutput());
	}

	@Test
	void shouldConvertFailedCommandResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(false);
		commandResult.setExitCode(1);
		commandResult.setError("OpenClaw failed");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setWorkspace("/workspace/project");

		ExecutionResult result = new OpenClawExecutor(commandExecutor).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("openclaw", "--help"), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertFalse(result.isSuccess());
		assertEquals("OpenClaw failed", result.getMessage());
		assertNull(result.getOutput());
	}
}
