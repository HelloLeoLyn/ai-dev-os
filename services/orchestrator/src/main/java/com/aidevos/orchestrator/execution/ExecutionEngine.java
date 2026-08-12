package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentResolutionException;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.job.JobLease;
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

	private static final String EXECUTOR_FAILURE = "EXECUTOR_FAILURE";
	private static final String AGENT_RESOLUTION_FAILURE = "AGENT_RESOLUTION_FAILURE";

	private final AgentResolver agentResolver;
	private final ExecutionRecordManager executionRecordManager;
	private final AuditService auditService;
	private final ExecutionAttemptRepository attemptRepository;

	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager) {
		this(agentResolver, executionRecordManager, AuditService.noop());
	}

	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager,
			AuditService auditService) {
		this(agentResolver, executionRecordManager, auditService,
			new InMemoryExecutionAttemptRepository());
	}

	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager,
			ExecutionAttemptRepository attemptRepository) {
		this(agentResolver, executionRecordManager, AuditService.noop(), attemptRepository);
	}

	@Autowired
	public ExecutionEngine(AgentResolver agentResolver, ExecutionRecordManager executionRecordManager,
			AuditService auditService, ExecutionAttemptRepository attemptRepository) {
		this.agentResolver = agentResolver;
		this.executionRecordManager = executionRecordManager;
		this.auditService = auditService;
		this.attemptRepository = attemptRepository;
	}

	public ExecutionResult execute(TaskDefinition taskDefinition) {
		return execute(taskDefinition, null, null);
	}

	public ExecutionResult execute(TaskDefinition taskDefinition, String jobId) {
		return execute(taskDefinition, jobId, null);
	}

	public ExecutionResult execute(TaskDefinition taskDefinition, String jobId, JobLease lease) {
		Instant startedAt = Instant.now();
		String executionId = UUID.randomUUID().toString();
		String agentName = taskDefinition.getAgentName();
		ExecutionContext context = null;
		ExecutionAttempt attempt = startAttempt(taskDefinition, jobId, executionId, startedAt, lease);
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
				finishAttempt(attempt, result);
				auditService.agentEvent(result.isSuccess() ? EventType.AGENT_EXECUTION_COMPLETED
					: EventType.AGENT_EXECUTION_FAILED, taskDefinition, context.getExecutionId(),
					jobId, agentName, result.isSuccess() ? "COMPLETED" : "FAILED");
			}
			catch (Exception exception) {
				result = failedResult(executorFailureMessage(executor, exception));
				failAttempt(attempt, EXECUTOR_FAILURE);
				auditService.agentEvent(EventType.AGENT_EXECUTION_FAILED, taskDefinition,
					context.getExecutionId(), jobId, agentName, "FAILED");
			}
		}
		catch (AgentResolutionException exception) {
			result = failedResult(exception.getMessage());
			failAttempt(attempt, AGENT_RESOLUTION_FAILURE);
		}

		ExecutionReport report = createReport(taskDefinition, agentName, result);
		ExecutionRecord record = createRecord(taskDefinition, agentName, result, report,
			context, startedAt, attempt);
		executionRecordManager.save(record);
		EventType completedType = result.isApprovalRequired() ? EventType.EXECUTION_WAITING_APPROVAL
			: result.isSuccess() ? EventType.EXECUTION_COMPLETED : EventType.EXECUTION_FAILED;
		auditService.executionEvent(completedType, taskDefinition, record.getExecutionId(), jobId,
			record.getId(), record.getStatus(), agentName);
		return result;
	}

	private ExecutionAttempt startAttempt(TaskDefinition taskDefinition, String jobId,
			String executionId, Instant startedAt, JobLease lease) {
		String attemptJobId = jobId != null ? jobId : executionId;
		int attemptNo = attemptRepository.getByJob(attemptJobId).size() + 1;
		ExecutionAttempt attempt = new ExecutionAttempt(UUID.randomUUID().toString(),
			attemptJobId, attemptNo);
		attempt.setExecutionId(executionId);
		if (lease != null) {
			attempt.applyLease(lease);
		}
		attemptRepository.save(attempt);
		attempt.markRunning(startedAt);
		attemptRepository.save(attempt);
		return attempt;
	}

	private void finishAttempt(ExecutionAttempt attempt, ExecutionResult result) {
		if (result.isApprovalRequired() || result.isSuccess()) {
			attempt.markSucceeded(Instant.now());
		}
		else {
			attempt.markFailed(EXECUTOR_FAILURE, Instant.now());
		}
		attemptRepository.save(attempt);
	}

	private void failAttempt(ExecutionAttempt attempt, String failureCode) {
		attempt.markFailed(failureCode, Instant.now());
		attemptRepository.save(attempt);
	}

	private ExecutionContext createContext(TaskDefinition taskDefinition, AgentDefinition agent, String jobId,
			String executionId) {
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId(executionId);
		context.setJobId(jobId);
		String originalTaskId = taskMetadataString(taskDefinition, "originalTaskId");
		context.setTaskId(originalTaskId == null || originalTaskId.isBlank()
			? taskDefinition.getId() : originalTaskId);
		context.setTaskName(taskDefinition.getName());
		context.setDescription(taskDefinition.getDescription());
		context.setAgentName(agent.getName());
		context.setInput(taskDefinition.getDescription());
		String workspacePath = taskMetadataString(taskDefinition, "workspacePath");
		context.setWorkspace(workspacePath == null || workspacePath.isBlank()
			? System.getProperty("user.dir") : workspacePath);
		context.setProjectId(taskMetadataString(taskDefinition, "projectId"));
		if (taskDefinition.getMetadata() != null) {
			context.getMetadata().putAll(taskDefinition.getMetadata());
		}
		Map<String, Object> parameters = new LinkedHashMap<>();
		if (taskDefinition.getParameters() != null) {
			parameters.putAll(taskDefinition.getParameters());
		}
		parameters.putAll(agent.getExecutorConfig());
		String executionMode = taskMetadataString(taskDefinition, "executionMode");
		if (executionMode != null) {
			parameters.put("executionMode", executionMode);
			if ("READ_ONLY".equals(executionMode)) {
				parameters.put("sandbox", "read-only");
				Object coding = parameters.get("coding");
				if (coding instanceof Map<?, ?> source) {
					Map<String, Object> safeCoding = new LinkedHashMap<>();
					source.forEach((key, value) -> safeCoding.put(String.valueOf(key), value));
					safeCoding.put("sandbox", "read-only");
					parameters.put("coding", Map.copyOf(safeCoding));
				}
			}
		}
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
		String originalTaskId = taskMetadataString(taskDefinition, "originalTaskId");
		report.setTaskId(originalTaskId == null || originalTaskId.isBlank()
			? taskDefinition.getId() : originalTaskId);
		report.setAgentName(agentName);
		report.setSuccess(result.isSuccess());
		report.setOutput(result.getOutput());
		report.setBeforeGitStatus(artifactContent(result, "git-status-before"));
		report.setAfterGitDiff(artifactContent(result, "git-diff"));
		return report;
	}

	private ExecutionRecord createRecord(TaskDefinition taskDefinition, String agentName,
			ExecutionResult result, ExecutionReport report, ExecutionContext context,
			Instant startedAt, ExecutionAttempt attempt) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(UUID.randomUUID().toString());
		String originalTaskId = taskMetadataString(taskDefinition, "originalTaskId");
		record.setTaskId(originalTaskId == null || originalTaskId.isBlank()
			? taskDefinition.getId() : originalTaskId);
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
		String metadataAttemptId = taskMetadataString(taskDefinition, "attemptId");
		record.setAttemptId(metadataAttemptId != null ? metadataAttemptId : attempt.getId());
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
