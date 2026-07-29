package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

@Component
public class MockAgentExecutor implements AgentExecutor {

	@Override
	public ExecutionResult execute(TaskDefinition taskDefinition) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		result.setOutput("Simulated execution for task " + taskDefinition.getId() + ": "
			+ taskDefinition.getDescription());
		return result;
	}
}
