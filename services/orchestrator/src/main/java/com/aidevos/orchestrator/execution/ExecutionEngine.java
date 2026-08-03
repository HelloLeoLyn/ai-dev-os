package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentResolutionException;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Component
public class ExecutionEngine {

	private final AgentResolver agentResolver;
	private final ExecutionRecordManager executionRecordManager;
	private final AuditService auditService;

	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager) {
		this(agentResolver, executionRecordManager, AuditService.noop());
	}

	@Autowired
	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager,
			AuditService auditService) {
		this.agentResolver = agentResolver;
		this.executionRecordManager = executionRecordManager;
		this.auditService = auditService;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		return execute(taskDefinition, null);
	}

	public ExecutionResult execute(TaskDefinition taskDefinition, String jobId) {
		Instant startedAt = Instant.now();
		String executionId = UUID.randomUUID().toString();
		String agentName = taskDefinition.getAgentName();
		ExecutionContext context = null;
		ExecutionResult result;
		try {
			ResolvedAgent resolvedAgent = agentResolver.resolve(taskDefinition);
			agentName = resolvedAgent.definition().getName();
			auditService.agentEvent(EventType.AGENT_SELECTED, taskDefinition, executionId, jobId,
				agentName, "SELECTED");
			AgentExecutor executor = resolvedAgent.executor();
			context = createContext(taskDefinition, resolvedAgent.definition(), jobId, executionId);
			auditService.agentEvent(EventType.AGENT_EXECUTION_STARTED, taskDefinition,
				context.getExecutionId(), jobId, agentName, "RUNNING");
			auditService.executionEvent(EventType.EXECUTION_STARTED, taskDefinition,
				context.getExecutionId(), jobId, null, "RUNNING", agentName);
			try {
				result = executor.execute(context);
				auditService.agentEvent(result.isSuccess() ? EventType.AGENT_EXECUTION_COMPLETED
					: EventType.AGENT_EXECUTION_FAILED, taskDefinition, context.getExecutionId(),
					jobId, agentName, result.isSuccess() ? "COMPLETED" : "FAILED");
			}
			catch (Exception exception) {
				result = failedResult(executorFailureMessage(executor, exception));
				auditService.agentEvent(EventType.AGENT_EXECUTION_FAILED, taskDefinition,
					context.getExecutionId(), jobId, agentName, "FAILED");
			}
		}
		catch (AgentResolutionException exception) {
			result = failedResult(exception.getMessage());
		}

		ExecutionReport report = createReport(taskDefinition, agentName, result);
		ExecutionRecord record = createRecord(taskDefinition, agentName, result, report,
			context, startedAt);
		executionRecordManager.save(record);
		EventType completedType = result.isApprovalRequired() ? EventType.EXECUTION_WAITING_APPROVAL
			: result.isSuccess() ? EventType.EXECUTION_COMPLETED : EventType.EXECUTION_FAILED;
		auditService.executionEvent(completedType, taskDefinition, record.getExecutionId(), jobId,
			record.getId(), record.getStatus(), agentName);
		return result;
	}

	private ExecutionContext createContext(TaskDefinition taskDefinition, AgentDefinition agent, String jobId,
			String executionId) {
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId(executionId);
		context.setJobId(jobId);
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
		report.setBeforeGitStatus(artifactContent(result, "git-status-before"));
		report.setAfterGitDiff(artifactContent(result, "git-diff"));
		return report;
	}

	private ExecutionRecord createRecord(TaskDefinition taskDefinition, String agentName,
			ExecutionResult result, ExecutionReport report, ExecutionContext context,
			Instant startedAt) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(UUID.randomUUID().toString());
		record.setTaskId(taskDefinition.getId());
		record.setAgentName(agentName);
		record.setStatus(result.isApprovalRequired() ? "WAITING_APPROVAL"
			: result.isSuccess() ? "SUCCESS" : "FAILED");
		record.setMessage(result.getMessage());
		record.setOutput(result.getOutput());
		record.setArtifacts(new java.util.ArrayList<>(result.getArtifacts()));
		record.setExecutionId(context == null ? null : context.getExecutionId());
		record.setJobId(context == null ? null : context.getJobId());
		record.setPlanRunId(taskMetadataString(taskDefinition, "planRunId"));
		record.setStepRunId(taskMetadataString(taskDefinition, "stepRunId"));
		record.setAttemptId(taskMetadataString(taskDefinition, "attemptId"));
		record.setWorkspace(metadataString(result, "workspace"));
		record.setSandbox(metadataString(result, "sandbox"));
		record.setApprovalId(result.getApprovalId() != null ? result.getApprovalId()
			: contextMetadataString(context, "approvalId"));
		record.setBranch(metadataString(result, "branch"));
		record.setBeforeHead(metadataString(result, "beforeHead"));
		record.setAfterHead(metadataString(result, "afterHead"));
		record.setExitCode(metadataInteger(result, "exitCode"));
		record.setCodexThreadId(metadataString(result, "codexThreadId"));
		record.setStartedAt(startedAt);
		record.setCompletedAt(Instant.now());
		record.setReport(report);
		return record;
	}

	private String artifactContent(ExecutionResult result, String type) {
		return result.getArtifacts().stream()
			.filter(artifact -> type.equals(artifact.getType()))
			.map(ExecutionArtifact::getContent)
			.findFirst()
			.orElse(null);
	}

	private String metadataString(ExecutionResult result, String key) {
		Object value = result.getMetadata().get(key);
		return value instanceof String text ? text : null;
	}

	private Integer metadataInteger(ExecutionResult result, String key) {
		Object value = result.getMetadata().get(key);
		return value instanceof Number number ? number.intValue() : null;
	}

	private String contextMetadataString(ExecutionContext context, String key) {
		if (context == null) {
			return null;
		}
		Object value = context.getMetadata().get(key);
		return value instanceof String text ? text : null;
	}

	private String taskMetadataString(TaskDefinition taskDefinition, String key) {
		if (taskDefinition.getMetadata() == null) {
			return null;
		}
		Object value = taskDefinition.getMetadata().get(key);
		return value instanceof String text ? text : null;
	}
}
