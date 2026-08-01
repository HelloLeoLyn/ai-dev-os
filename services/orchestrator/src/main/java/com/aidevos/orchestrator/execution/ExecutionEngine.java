package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentResolutionException;
import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ExecutionEngine {

	private final AgentResolver agentResolver;
	private final ExecutionRecordManager executionRecordManager;

	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager) {
		this.agentResolver = agentResolver;
		this.executionRecordManager = executionRecordManager;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		String agentName = taskDefinition.getAgentName();
		ExecutionResult result;
		try {
			ResolvedAgent resolvedAgent = agentResolver.resolve(taskDefinition);
			agentName = resolvedAgent.definition().getName();
			AgentExecutor executor = resolvedAgent.executor();
			ExecutionContext context = createContext(taskDefinition, resolvedAgent.definition());
			try {
				result = executor.execute(context);
			}
			catch (Exception exception) {
				result = failedResult(executorFailureMessage(executor, exception));
			}
		}
		catch (AgentResolutionException exception) {
			result = failedResult(exception.getMessage());
		}

		ExecutionReport report = createReport(taskDefinition, agentName, result);
		executionRecordManager.save(createRecord(taskDefinition, agentName, result, report));
		return result;
	}

	private ExecutionContext createContext(TaskDefinition taskDefinition, AgentDefinition agent) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(taskDefinition.getId());
		context.setTaskName(taskDefinition.getName());
		context.setDescription(taskDefinition.getDescription());
		context.setAgentName(agent.getName());
		context.setInput(taskDefinition.getDescription());
		context.setWorkspace(System.getProperty("user.dir"));
		Map<String, Object> parameters = new LinkedHashMap<>();
		if (taskDefinition.getParameters() != null) {
			parameters.putAll(taskDefinition.getParameters());
		}
		parameters.putAll(agent.getExecutorConfig());
		context.setParameters(parameters);
		return context;
	}

	private String executorFailureMessage(AgentExecutor executor, Exception exception) {
		String detail = exception.getMessage();
		if (detail == null || detail.isBlank()) {
			detail = exception.getClass().getSimpleName();
		}
		return "Executor " + executor.getType() + " failed: " + detail;
	}

	private ExecutionResult failedResult(String message) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage(message);
		result.setOutput(null);
		return result;
	}

	private ExecutionReport createReport(TaskDefinition taskDefinition, String agentName,
			ExecutionResult result) {
		ExecutionReport report = new ExecutionReport();
		report.setTaskId(taskDefinition.getId());
		report.setAgentName(agentName);
		report.setSuccess(result.isSuccess());
		report.setOutput(result.getOutput());
		return report;
	}

	private ExecutionRecord createRecord(TaskDefinition taskDefinition, String agentName,
			ExecutionResult result, ExecutionReport report) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(UUID.randomUUID().toString());
		record.setTaskId(taskDefinition.getId());
		record.setAgentName(agentName);
		record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
		record.setMessage(result.getMessage());
		record.setOutput(result.getOutput());
		record.setReport(report);
		return record;
	}
}
