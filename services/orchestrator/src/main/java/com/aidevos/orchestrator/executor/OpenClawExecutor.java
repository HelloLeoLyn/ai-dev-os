package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;

import org.springframework.stereotype.Component;

@Component
public class OpenClawExecutor implements AgentExecutor {

	private final OpenClawTaskService taskService;

	public OpenClawExecutor(OpenClawTaskService taskService) {
		this.taskService = taskService;
	}

	@Override
	public String getType() {
		return "openclaw";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		OpenClawTaskRequest request = new OpenClawTaskRequest(agentId(context), context.getInput());
		OpenClawTaskResult taskResult = taskService.execute(request).join();

		ExecutionResult result = new ExecutionResult();
		result.setSuccess(taskResult.successful());
		if (taskResult.successful()) {
			result.setMessage("Task executed successfully");
			result.setOutput(taskResult.output());
		}
		else {
			result.setMessage(failureMessage(taskResult));
		}
		return result;
	}

	private String agentId(ExecutionContext context) {
		Object agentId = context.getParameters().get("agentId");
		return agentId instanceof String value ? value : null;
	}

	private String failureMessage(OpenClawTaskResult taskResult) {
		if (taskResult.output() != null && !taskResult.output().isBlank()) {
			return taskResult.output();
		}
		return "OpenClaw task failed: " + taskResult.status();
	}
}
