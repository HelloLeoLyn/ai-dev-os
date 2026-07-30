package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ExecutionEngine {

	private final ExecutorManager executorManager;
	private final ExecutionRecordManager executionRecordManager;
	private final AgentSelector agentSelector;

	public ExecutionEngine(ExecutorManager executorManager, ExecutionRecordManager executionRecordManager,
			AgentSelector agentSelector) {
		this.executorManager = executorManager;
		this.executionRecordManager = executionRecordManager;
		this.agentSelector = agentSelector;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		String agentName = resolveAgentName(taskDefinition);
		AgentExecutor executor = executorManager.getExecutor(agentName);
		ExecutionResult result = executor == null
			? failedResult(taskDefinition, agentName)
			: executor.execute(taskDefinition);

		executionRecordManager.save(createRecord(taskDefinition, agentName, result));
		return result;
	}

	private String resolveAgentName(TaskDefinition taskDefinition) {
		List<String> requiredCapabilities = taskDefinition.getRequiredCapabilities();
		if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
			return taskDefinition.getAgentName();
		}

		AgentDefinition selectedAgent = agentSelector.select(requiredCapabilities);
		return selectedAgent == null ? null : selectedAgent.getName();
	}

	private ExecutionResult failedResult(TaskDefinition taskDefinition, String agentName) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		if (taskDefinition.getRequiredCapabilities() != null
				&& !taskDefinition.getRequiredCapabilities().isEmpty()) {
			result.setMessage("Agent not found for required capabilities: "
				+ taskDefinition.getRequiredCapabilities());
		}
		else {
			result.setMessage("Agent not found: " + agentName);
		}
		result.setOutput(null);
		return result;
	}

	private ExecutionRecord createRecord(TaskDefinition taskDefinition, String agentName, ExecutionResult result) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(UUID.randomUUID().toString());
		record.setTaskId(taskDefinition.getId());
		record.setAgentName(agentName);
		record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
		record.setMessage(result.getMessage());
		record.setOutput(result.getOutput());
		return record;
	}
}
