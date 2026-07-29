package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExecutionEngine {

	private final ExecutorManager executorManager;
	private final ExecutionRecordManager executionRecordManager;

	public ExecutionEngine(ExecutorManager executorManager, ExecutionRecordManager executionRecordManager) {
		this.executorManager = executorManager;
		this.executionRecordManager = executionRecordManager;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		AgentExecutor executor = executorManager.getExecutor(taskDefinition.getAgentName());
		ExecutionResult result = executor == null
			? failedResult(taskDefinition.getAgentName())
			: executor.execute(taskDefinition);

		executionRecordManager.save(createRecord(taskDefinition, result));
		return result;
	}

	private ExecutionResult failedResult(String agentName) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage("Agent not found: " + agentName);
		result.setOutput(null);
		return result;
	}

	private ExecutionRecord createRecord(TaskDefinition taskDefinition, ExecutionResult result) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(UUID.randomUUID().toString());
		record.setTaskId(taskDefinition.getId());
		record.setAgentName(taskDefinition.getAgentName());
		record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
		record.setMessage(result.getMessage());
		record.setOutput(result.getOutput());
		return record;
	}
}
