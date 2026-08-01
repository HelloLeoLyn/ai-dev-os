package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentResolutionException;
import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitResult;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ExecutionEngine {

	private final AgentResolver agentResolver;
	private final ExecutionRecordManager executionRecordManager;
	private final GitExecutor gitExecutor;

	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager,
			GitExecutor gitExecutor) {
		this.agentResolver = agentResolver;
		this.executionRecordManager = executionRecordManager;
		this.gitExecutor = gitExecutor;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		String agentName = taskDefinition.getAgentName();
		ExecutionResult result;
		GitResult beforeGitStatus = null;
		GitResult afterGitDiff = null;
		try {
			ResolvedAgent resolvedAgent = agentResolver.resolve(taskDefinition);
			agentName = resolvedAgent.definition().getName();
			AgentExecutor executor = resolvedAgent.executor();
			ExecutionContext context = createContext(taskDefinition, resolvedAgent.definition());
			beforeGitStatus = gitExecutor.status(context.getWorkspace());
			result = executor.execute(context);
			afterGitDiff = gitExecutor.diff(context.getWorkspace());
		}
		catch (AgentResolutionException exception) {
			result = failedResult(exception.getMessage());
		}

		ExecutionReport report = createReport(taskDefinition, agentName, result,
			beforeGitStatus, afterGitDiff);
		executionRecordManager.save(createRecord(taskDefinition, agentName, result, report));
		return result;
	}

	private ExecutionContext createContext(TaskDefinition taskDefinition, AgentDefinition agent) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(taskDefinition.getId());
		context.setTaskName(taskDefinition.getName());
		context.setDescription(taskDefinition.getDescription());
		context.setAgentName(agent.getName());
		context.setExternalAgentId(agent.getExternalId());
		context.setInput(taskDefinition.getDescription());
		context.setWorkspace(System.getProperty("user.dir"));
		return context;
	}

	private ExecutionResult failedResult(String message) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage(message);
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
