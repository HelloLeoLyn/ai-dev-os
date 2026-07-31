package com.aidevos.orchestrator.executor.git;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class GitExecutor {

	private final CommandExecutor commandExecutor;

	public GitExecutor(CommandExecutor commandExecutor) {
		this.commandExecutor = commandExecutor;
	}

	public GitResult status(String workspace) {
		return execute(List.of("git", "status", "--short"), workspace);
	}

	public GitResult diff(String workspace) {
		return execute(List.of("git", "diff", "--stat"), workspace);
	}

	private GitResult execute(List<String> command, String workspace) {
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		options.setWorkingDirectory(workspace);

		CommandResult commandResult = commandExecutor.execute(options);
		GitResult result = new GitResult();
		result.setSuccess(commandResult.isSuccess());
		result.setOutput(commandResult.getOutput());
		result.setError(commandResult.getError());
		return result;
	}
}
