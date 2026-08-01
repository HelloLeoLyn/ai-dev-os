package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.browser.BrowserResultMapper;
import com.aidevos.orchestrator.browser.BrowserTaskPromptBuilder;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;

import org.springframework.stereotype.Component;

@Component
public class OpenClawExecutor implements AgentExecutor {

	private final OpenClawTaskService taskService;
	private final BrowserTaskPromptBuilder browserTaskPromptBuilder;
	private final BrowserResultMapper browserResultMapper;

	public OpenClawExecutor(OpenClawTaskService taskService,
			BrowserTaskPromptBuilder browserTaskPromptBuilder,
			BrowserResultMapper browserResultMapper) {
		this.taskService = taskService;
		this.browserTaskPromptBuilder = browserTaskPromptBuilder;
		this.browserResultMapper = browserResultMapper;
	}

	@Override
	public String getType() {
		return "openclaw";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		boolean browserTask = browserTaskPromptBuilder.supports(context.getParameters());
		String input = browserTask
			? browserTaskPromptBuilder.build(context.getInput(), context.getParameters())
			: context.getInput();
		OpenClawTaskRequest request = new OpenClawTaskRequest(agentId(context), input);
		OpenClawTaskResult taskResult = taskService.execute(request).join();

		ExecutionResult result = new ExecutionResult();
		result.setSuccess(taskResult.successful());
		if (taskResult.successful()) {
			result.setMessage("Task executed successfully");
			result.setOutput(taskResult.output());
			if (browserTask) {
				browserResultMapper.map(taskResult.output(), result);
			}
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
