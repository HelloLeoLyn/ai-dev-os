package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

@Component
public class ExecutionEngine {

	private final AgentManager agentManager;

	public ExecutionEngine(AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		if (agentManager.getAgent(taskDefinition.getAgentName()) == null) {
			return failedResult(taskDefinition.getAgentName());
		}

		ExecutionContext context = createContext(taskDefinition);
		return successfulResult(context);
	}

	private ExecutionContext createContext(TaskDefinition taskDefinition) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(taskDefinition.getId());
		context.setAgentName(taskDefinition.getAgentName());
		context.setInput(taskDefinition.getDescription());
		return context;
	}

	private ExecutionResult failedResult(String agentName) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage("Agent not found: " + agentName);
		result.setOutput(null);
		return result;
	}

	private ExecutionResult successfulResult(ExecutionContext context) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		result.setOutput("Simulated execution for task " + context.getTaskId() + ": " + context.getInput());
		return result;
	}
}
