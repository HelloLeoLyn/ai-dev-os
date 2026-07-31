package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitResult;
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
	private final GitExecutor gitExecutor;

	public ExecutionEngine(ExecutorManager executorManager, ExecutionRecordManager executionRecordManager,
			AgentSelector agentSelector, GitExecutor gitExecutor) {
		this.executorManager = executorManager;
		this.executionRecordManager = executionRecordManager;
		this.agentSelector = agentSelector;
		this.gitExecutor = gitExecutor;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		String agentName = resolveAgentName(taskDefinition);
		AgentExecutor executor = executorManager.getExecutor(agentName);
		ExecutionResult result;
		GitResult beforeGitStatus = null;
		GitResult afterGitDiff = null;
		if (executor == null) {
			result = failedResult(taskDefinition, agentName);
		}
		else {
			ExecutionContext context = createContext(taskDefinition, agentName);
			beforeGitStatus = gitExecutor.status(context.getWorkspace());
			result = executor.execute(context);
			afterGitDiff = gitExecutor.diff(context.getWorkspace());
		}

		ExecutionReport report = createReport(taskDefinition, agentName, result,
			beforeGitStatus, afterGitDiff);
		executionRecordManager.save(createRecord(taskDefinition, agentName, result, report));
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

	private ExecutionContext createContext(TaskDefinition taskDefinition, String agentName) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(taskDefinition.getId());
		context.setTaskName(taskDefinition.getName());
		context.setDescription(taskDefinition.getDescription());
		context.setAgentName(agentName);
		context.setInput(taskDefinition.getDescription());
		context.setWorkspace(System.getProperty("user.dir"));
		return context;
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

	private ExecutionReport createReport(TaskDefinition taskDefinition, String agentName,
			ExecutionResult result, GitResult beforeGitStatus, GitResult afterGitDiff) {
		ExecutionReport report = new ExecutionReport();
		report.setTaskId(taskDefinition.getId());
		report.setAgentName(agentName);
		report.setSuccess(result.isSuccess());
		report.setBeforeGitStatus(gitDiagnostic(beforeGitStatus));
		report.setAfterGitDiff(gitDiagnostic(afterGitDiff));
		report.setOutput(result.getOutput());
		return report;
	}

	private String gitDiagnostic(GitResult result) {
		if (result == null) {
			return null;
		}
		return result.isSuccess() ? result.getOutput() : result.getError();
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
