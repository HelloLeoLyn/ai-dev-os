package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CodexExecutor implements AgentExecutor {

	private final CommandExecutor commandExecutor;

	public CodexExecutor(CommandExecutor commandExecutor) {
		this.commandExecutor = commandExecutor;
	}

	@Override
	public String getType() {
		return "codex";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		CommandOptions options = new CommandOptions();
		options.setCommand(List.of("codex", "exec", context.getDescription()));
		options.setWorkingDirectory(context.getWorkspace());
		CommandResult commandResult = commandExecutor.execute(options);

		ExecutionResult result = new ExecutionResult();
		result.setSuccess(commandResult.isSuccess());
		if (commandResult.isSuccess()) {
			result.setMessage("Task executed successfully");
			result.setOutput(commandResult.getOutput());
		}
		else {
			result.setMessage(commandResult.getError());
		}
		return result;
	}
}
