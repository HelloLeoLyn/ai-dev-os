package com.aidevos.orchestrator.executor.git;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitExecutorTest {

	@Test
	void shouldExecuteStatusCommandAndConvertSuccessfulResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setOutput(" M README.md");
		commandResult.setError("");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		GitResult result = new GitExecutor(commandExecutor).status("/workspace/project");

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("git", "status", "--short"), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertTrue(result.isSuccess());
		assertEquals(" M README.md", result.getOutput());
		assertEquals("", result.getError());
	}

	@Test
	void shouldExecuteDiffCommandAndConvertFailedResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(false);
		commandResult.setOutput("");
		commandResult.setError("Not a git repository");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		GitResult result = new GitExecutor(commandExecutor).diff("/workspace/project");

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("git", "diff", "--stat"), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertFalse(result.isSuccess());
		assertEquals("", result.getOutput());
		assertEquals("Not a git repository", result.getError());
	}
}
