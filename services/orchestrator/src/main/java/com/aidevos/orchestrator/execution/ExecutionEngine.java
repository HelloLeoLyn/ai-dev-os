package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExecutionEngine {

	private final AgentManager agentManager;
	private final ExecutionRecordManager executionRecordManager;

	public ExecutionEngine(AgentManager agentManager, ExecutionRecordManager executionRecordManager) {
		this.agentManager = agentManager;
		this.executionRecordManager = executionRecordManager;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		ExecutionResult result;
		if (agentManager.getAgent(taskDefinition.getAgentName()) == null) {
			result = failedResult(taskDefinition.getAgentName());
		} else {
			ExecutionContext context = createContext(taskDefinition);
			result = successfulResult(context);
		}

		executionRecordManager.save(createRecord(taskDefinition, result));
		return result;
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
