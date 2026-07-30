package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class CodexExecutor implements AgentExecutor {

	@Override
	public String getType() {
		return "codex";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		result.setOutput("Simulated Codex execution for task " + context.getTaskId() + ": "
			+ context.getDescription());
		return result;
	}
}
