package com.aidevos.orchestrator.audit;

import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.outbox.JdbcConnectionContext;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import com.aidevos.orchestrator.approval.CodingApprovalRequest;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.approval.ToolApprovalRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
	private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
	private final AuditRepository repository;
	private final Clock clock;

	@Autowired
	public AuditService(AuditRepository repository) {
		this(repository, Clock.systemUTC());
	}

	AuditService(AuditRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public static AuditService noop() {
		return new AuditService(new AuditRepository() {
			public EventRecord append(EventRecord event) { return event; }
			public EventRecord get(String id) { return null; }
			public List<EventRecord> query(EventQuery query) { return List.of(); }
		});
	}

	public Optional<EventRecord> record(EventRecord event) {
		try {
			return Optional.of(repository.append(event));
		}
		catch (RuntimeException exception) {
			// Inside a business transaction the audit enqueue is part of the
			// commit: a failure must roll back the business state instead of
			// silently committing without its outbox entry.
			if (JdbcConnectionContext.active()) {
				throw exception;
			}
			logger.error("Failed to append audit event type={} aggregate={}/{}",
				event.type(), event.aggregateType(), event.aggregateId(), exception);
			return Optional.empty();
		}
	}

	public EventRecord get(String id) { return repository.get(id); }
	public List<EventRecord> query(EventQuery query) { return repository.query(query); }

	public void jobEvent(EventType type, ExecutionJob job, String fromStatus, String toStatus) {
		record(eventAt(jobEventTime(type, job), type, "job", job.getId(), fromStatus, toStatus,
			job.getTaskId(), null, null,
			null, null, null, job.getId(), null, job.getExecutionRecordId(), null,
			job.getApprovalId(), "SYSTEM", "job", type.name(), Map.of(),
			type + ":job:" + job.getId() + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	private Instant jobEventTime(EventType type, ExecutionJob job) {
		return switch (type) {
			case JOB_SUBMITTED -> job.getCreatedAt();
			case JOB_STARTED -> job.getStartedAt();
			case JOB_SUCCEEDED, JOB_FAILED, JOB_APPROVAL_REJECTED -> job.getCompletedAt();
			default -> Instant.now(clock);
		};
	}

	public void executionEvent(EventType type, TaskDefinition task, String executionId, String jobId,
			String executionRecordId, String status, String agentName) {
		String aggregateId = executionId == null ? executionRecordId : executionId;
		record(event(type, "execution", aggregateId, null, status, taskId(task),
			metadata(task, "planId"), metadataInteger(task, "planVersion"),
			metadata(task, "planRunId"), metadata(task, "stepRunId"), metadata(task, "attemptId"),
			jobId, executionId, executionRecordId, null, null, "AGENT", agentName, type.name(),
			Map.of(), type + ":execution:" + aggregateId + ":" + value(status)));
	}

	public void executionRecordSaved(ExecutionRecord record) {
		record(event(EventType.EXECUTION_RECORD_SAVED, "execution-record", record.getId(), null,
			record.getStatus(), record.getTaskId(), null, null, record.getPlanRunId(),
			record.getStepRunId(), record.getAttemptId(), record.getJobId(), record.getExecutionId(),
			record.getId(), null, record.getApprovalId(), "SYSTEM", "execution-record-manager",
			"Execution record saved", Map.of("artifactCount", record.getArtifacts().size()),
			"EXECUTION_RECORD_SAVED:record:" + record.getId()));
		if (!record.getArtifacts().isEmpty()) {
			record(event(EventType.ARTIFACTS_RECORDED, "execution-record", record.getId(), null,
				record.getStatus(), record.getTaskId(), null, null, record.getPlanRunId(),
				record.getStepRunId(), record.getAttemptId(), record.getJobId(), record.getExecutionId(),
				record.getId(), null, record.getApprovalId(), "SYSTEM", "execution-record-manager",
				"Execution artifacts recorded", Map.of("artifactCount", record.getArtifacts().size()),
				"ARTIFACTS_RECORDED:record:" + record.getId()));
		}
	}

	public void toolEvent(EventType type, ToolInvocation invocation, String approvalId,
			String status, String resultCode) {
		toolEvent(type, invocation, approvalId, status, resultCode, Map.of());
	}

	public void toolEvent(EventType type, ToolInvocation invocation, String approvalId,
			String status, String resultCode, Map<String, Object> resultMetadata) {
		Map<String, Object> metadata = new java.util.LinkedHashMap<>();
		metadata.put("providerId", invocation.providerId());
		metadata.put("toolName", invocation.toolName());
		if (resultCode != null) metadata.put("resultCode", resultCode);
		for (String key : java.util.List.of("operation", "exitCode", "projectYaml",
				"outputPath", "projectDir", "durationMs")) {
			if (resultMetadata != null && resultMetadata.containsKey(key)) {
				metadata.put(key, resultMetadata.get(key));
			}
		}
		record(event(type, "tool-invocation", invocation.invocationId(), null, status, null, null,
			null, null, null, null, invocation.jobId(), invocation.executionId(), null,
			invocation.invocationId(), approvalId, "SYSTEM", invocation.providerId(), type.name(),
			Map.copyOf(metadata), type + ":tool:" + invocation.invocationId()));
	}

	public void mcpEvent(EventType type, String providerId, ToolInvocation invocation,
			String status) {
		String aggregateId = invocation == null ? providerId : invocation.invocationId();
		Map<String, Object> metadata = invocation == null
			? Map.of("providerId", providerId)
			: Map.of("providerId", providerId, "toolName", invocation.toolName());
		record(event(type, "mcp", aggregateId, null, status, null, null, null, null, null,
			null, invocation == null ? null : invocation.jobId(),
			invocation == null ? null : invocation.executionId(), null,
			invocation == null ? null : invocation.invocationId(), null, "SYSTEM", providerId,
			type.name(), metadata, type + ":mcp:" + aggregateId
				+ (invocation == null ? ":" + UUID.randomUUID() : "")));
	}

	public void toolApprovalEvent(EventType type, ToolApprovalRequest approval, String fromStatus,
			String toStatus) {
		record(event(type, "tool-approval", approval.getId(), fromStatus, toStatus, null, null,
			null, null, null, null, approval.getJobId(), approval.getExecutionId(), null,
			approval.getInvocationId(), approval.getId(), "SYSTEM", approval.getProviderId(),
			type.name(), Map.of("providerId", approval.getProviderId(),
				"toolName", approval.getToolName(), "permissionLevel", approval.getPermissionLevel()),
			type + ":tool-approval:" + approval.getId() + ":" + value(toStatus)));
	}

	public void codingApprovalEvent(EventType type, CodingApprovalRequest approval,
			String fromStatus, String toStatus) {
		record(event(type, "coding-approval", approval.getId(), fromStatus, toStatus,
			approval.getTaskId(), null, null, null, null, null, approval.getJobId(), null, null,
			null, approval.getId(), "SYSTEM", "coding-approval", type.name(),
			Map.of("sandbox", approval.getSandbox()),
			type + ":coding-approval:" + approval.getId() + ":" + value(toStatus)));
	}

	public void agentEvent(EventType type, TaskDefinition task, String executionId, String jobId,
			String agentName, String status) {
		String aggregateId = executionId == null ? task.getId() + ":" + agentName : executionId;
		record(event(type, "agent-execution", aggregateId, null, status, taskId(task),
			metadata(task, "planId"), metadataInteger(task, "planVersion"),
			metadata(task, "planRunId"), metadata(task, "stepRunId"), metadata(task, "attemptId"),
			jobId, executionId, null, null, null, "AGENT", agentName, type.name(), Map.of(),
			type + ":agent:" + aggregateId + ":" + value(jobId)));
	}

	public void planEvent(EventType type, PlanningRequest request, String status,
			List<String> errors) {
		String requestId = request == null ? "unknown" : request.requestId();
		Map<String, Object> metadata = errors == null || errors.isEmpty()
			? Map.of() : Map.of("errors", List.copyOf(errors));
		record(event(type, "planning-request", requestId, null, status, requestId, null, null,
			null, null, null, null, null, null, null, null, "USER", requestId, type.name(),
			metadata, type + ":planning-request:" + requestId));
	}

	public void stepEvent(EventType type, PlanRun run, StepRun step, StepAttempt attempt,
			String fromStatus, String toStatus) {
		String attemptId = attempt == null ? null : attempt.getId();
		String jobId = attempt == null ? null : attempt.getJobId();
		String recordId = attempt == null ? null : attempt.getExecutionRecordId();
		record(event(type, "step-run", step.getId(), fromStatus, toStatus,
			run.getOriginalTaskId(), run.getPlanId(),
			run.getPlanVersion(), run.getId(), step.getId(), attemptId, jobId, null, recordId,
			null, null, "SYSTEM", "plan-scheduler", type.name(),
			Map.of("stepId", step.getStepId()), type + ":step:" + step.getId() + ":"
				+ value(attemptId)));
	}

	public void replanEvent(ReplanRequest request) {
		record(event(EventType.REPLAN_REQUESTED, "replan-request", request.id(), null, "CREATED",
			null, request.originalPlanId(), request.originalPlanVersion(), request.failedPlanRunId(),
			null, null, null, null, null, null, null, "SYSTEM", "replan-service",
			"Replan requested", Map.of("failedStepId", request.failedStepId(),
				"classification", request.failureClassification().name()),
			"REPLAN_REQUESTED:replan:" + request.id()));
	}

	public void testEvent(EventType type, String testId, String taskId, String executionId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "test", testId, fromStatus, toStatus, taskId, null, null, null, null,
			null, null, executionId, null, null, null, "AGENT", "testing", summary, metadata,
			type + ":test:" + testId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	/**
	 * Records validation lifecycle events. The validation run id is the formal
	 * aggregate id and taskId is a first-class audit field, allowing task
	 * timelines to include validation without metadata-only joins.
	 */
	public void validationEvent(EventType type, String taskId, String validationRunId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "validation-run", validationRunId, fromStatus, toStatus, taskId,
			null, null, null, null, null, null, null, null, null, null, "SYSTEM",
			"validation-service", summary, metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":validation:" + validationRunId + ":" + value(fromStatus) + ":"
				+ value(toStatus) + ":" + UUID.randomUUID()));
	}

	public void qualityGateEvent(EventType type, String taskId, String validationRunId,
			String gateResultId, String approvalId, String summary, Map<String, Object> metadata) {
		String aggregateId = gateResultId == null ? validationRunId : gateResultId;
		record(event(type, "quality-gate-result", aggregateId, null, null, taskId,
			null, null, null, null, null, null, null, null, null, approvalId,
			"SYSTEM", "quality-gate-service", summary, metadata == null ? Map.of() : metadata,
			type + ":quality-gate:" + aggregateId + ":" + UUID.randomUUID()));
	}

	public void agentPlanEvent(EventType type, String planId, String taskId, String agentId,
			int step, String fromStatus, String toStatus, String summary,
			Map<String, Object> metadata) {
		record(event(type, "agent-plan", planId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "AGENT", agentId, summary, metadata,
			type + ":agent-plan:" + planId + ":" + agentId + ":" + step + ":"
				+ value(fromStatus) + ":" + value(toStatus)));
	}

	/**
	 * Records an administrative or user operation (agent package install /
	 * uninstall, skill and plugin enable / disable, project switch, user task
	 * submission). Keeps the existing audit API untouched.
	 */
	public void adminEvent(EventType type, String aggregateType, String aggregateId,
			String actor, String summary, Map<String, Object> metadata) {
		record(event(type, aggregateType, aggregateId, null, null, null, null, null, null,
			null, null, null, null, null, null, null,
			actor == null || actor.isBlank() ? "SYSTEM" : "USER", actor, summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":" + aggregateType + ":" + aggregateId + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a Task Center lifecycle transition. Unlike adminEvent, the event
	 * carries the taskId so it appears in the task-scoped unified timeline.
	 */
	/**
	 * Records an automatic repair lifecycle event (REPAIR_STARTED /
	 * ANALYZING / FIXING / VERIFYING / SUCCESS / FAILED) carrying the taskId
	 * so it appears on the task timeline.
	 */
	public void repairEvent(EventType type, String taskId, String repairId, String fromStatus,
			String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "repair", repairId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "repair-coordinator", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":repair:" + repairId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	public void changeEvent(EventType type, String taskId, String changeId, String fromStatus,
			String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "change", changeId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "change-service", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":change:" + changeId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	public void commitEvent(EventType type, String taskId, String commitId, String changeId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "commit", commitId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "commit-service", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":commit:" + commitId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	public void remoteEvent(EventType type, String taskId, String remoteId, String commitId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "remote", remoteId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "remote-service", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":remote:" + remoteId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	/**
	 * Records a pull request lifecycle event (PR_CREATED / OPENED / CLOSED /
	 * MERGED / FAILED) carrying the taskId so it appears on the task
	 * timeline alongside commit, push and change events.
	 */
	public void prEvent(EventType type, String taskId, String pullRequestId, String commitId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "pull-request", pullRequestId, fromStatus, toStatus, taskId, null,
			null, null, null, null, null, null, null, null, null, "SYSTEM", "pr-service",
			summary, metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":pr:" + pullRequestId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	/**
	 * Records a CI run lifecycle event (CI_STARTED / CI_RUNNING / CI_SUCCESS /
	 * CI_FAILED / CI_CANCELLED) carrying the taskId so it appears on the task
	 * timeline alongside pull request and commit events.
	 */
	public void ciEvent(EventType type, String taskId, String ciRunId, String pullRequestId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "ci-run", ciRunId, fromStatus, toStatus, taskId, null, null,
			null, null, null, null, null, null, null, null, "SYSTEM", "ci-service",
			summary, metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":ci:" + ciRunId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	/**
	 * Records a pull request feedback loop event (FEEDBACK_CREATED /
	 * REPAIRING / WAITING_REVIEW / PUSHED / RECHECKING / SUCCESS / FAILED)
	 * carrying the taskId so it appears on the task timeline.
	 */
	public void feedbackEvent(EventType type, String taskId, String feedbackId,
			String fromStatus, String toStatus, String summary,
			Map<String, Object> metadata) {
		record(event(type, "feedback", feedbackId, fromStatus, toStatus, taskId, null, null,
			null, null, null, null, null, null, null, null, "SYSTEM", "pr-feedback-service",
			summary, metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":feedback:" + feedbackId + ":" + value(fromStatus) + ":"
				+ value(toStatus)));
	}

	/**
	 * Records a codex execution lifecycle event (CODEX_EXEC_STARTED /
	 * COMPLETED / FAILED) carrying the taskId so it appears on the task
	 * timeline alongside the agent plan events.
	 */
	public void codexExecutionEvent(EventType type, String taskId, String executionId,
			String workspace, String summary, Map<String, Object> metadata) {
		record(event(type, "execution", executionId, null, null, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "codex", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":execution:" + executionId + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an execution graph lifecycle event (GRAPH_CREATED /
	 * NODE_STARTED / NODE_COMPLETED / NODE_FAILED) carrying the taskId so
	 * the graph and its node statuses appear on the task timeline.
	 */
	/**
	 * Records a memory retrieval event (MEMORY_SEARCHED / MEMORY_MATCHED /
	 * MEMORY_APPLIED) carrying the taskId so the search and the matched
	 * experience appear on the task timeline before agent execution.
	 */
	public void memoryEvent(EventType type, String taskId, String query, int matchCount,
			java.util.List<String> memoryIds, String summary,
			Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("query", value(query));
		enriched.put("matchCount", matchCount);
		if (memoryIds != null && !memoryIds.isEmpty()) {
			enriched.put("memoryIds", java.util.List.copyOf(memoryIds));
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "memory", "memory-search-" + UUID.randomUUID(), null, null,
			taskId, null, null, null, null, null, null, null, null, null, null,
			"SYSTEM", "memory-search", summary, Map.copyOf(enriched),
			type + ":memory:" + value(taskId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an MCP tool layer event (TOOL_REGISTERED, TOOL_SELECTED,
	 * TOOL_STARTED, TOOL_COMPLETED, TOOL_FAILED, TOOL_DENIED) with the
	 * toolId, agentType, taskId and duration metadata.
	 */
	public void toolExecutionEvent(EventType type, String toolId, String agentType,
			String taskId, String status, String summary, Map<String, Object> metadata) {
		String aggregateId = toolId == null || toolId.isBlank() ? "mcp-tool" : toolId;
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("toolId", value(toolId));
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (taskId != null) {
			enriched.put("taskId", taskId);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "mcp-tool", aggregateId, null, status, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM",
			agentType == null ? "mcp-router" : agentType, summary, Map.copyOf(enriched),
			type + ":tool:" + value(toolId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a security layer event (SECURITY_CHECK, PERMISSION_GRANTED,
	 * PERMISSION_DENIED) with the agentType and permission metadata.
	 */
	public void securityEvent(EventType type, String taskId, String agentType,
			String permission, String status, String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		if (taskId != null) {
			enriched.put("taskId", taskId);
		}
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (permission != null) {
			enriched.put("permission", permission);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "security", "security-" + UUID.randomUUID(), null, status,
			taskId, null, null, null, null, null, null, null, null, null, null,
			"SYSTEM", agentType == null ? "security" : agentType, summary,
			Map.copyOf(enriched),
			type + ":security:" + value(taskId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a sandbox lifecycle event (SANDBOX_CREATED / SANDBOX_DESTROYED)
	 * with the sandboxId metadata.
	 */
	public void sandboxEvent(EventType type, String sandboxId, String taskId,
			String agentType, String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("sandboxId", value(sandboxId));
		if (taskId != null) {
			enriched.put("taskId", taskId);
		}
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "sandbox", sandboxId, null, null, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM",
			agentType == null ? "sandbox" : agentType, summary, Map.copyOf(enriched),
			type + ":sandbox:" + value(sandboxId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a secret access event (SECRET_ACCESSED / SECRET_DENIED). The raw
	 * secret value is never part of the audit trail.
	 */
	public void secretEvent(EventType type, String key, String agentType, String taskId,
			String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("key", value(key));
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (taskId != null) {
			enriched.put("taskId", taskId);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "secret", "secret-" + value(key), null, null, taskId, null,
			null, null, null, null, null, null, null, null, null, "SYSTEM",
			agentType == null ? "secret-manager" : agentType, summary,
			Map.copyOf(enriched),
			type + ":secret:" + value(key) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a project lifecycle event (PROJECT_CREATED, PROJECT_ARCHIVED,
	 * PROJECT_AGENT_BOUND, PROJECT_WORKSPACE_CREATED) with the projectId and
	 * workspaceId metadata.
	 */
	public void projectEvent(EventType type, String projectId, String summary,
			Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("projectId", value(projectId));
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "project", projectId, null, null, null, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "project", summary,
			Map.copyOf(enriched),
			type + ":project:" + value(projectId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a trace lifecycle event (TRACE_STARTED / TRACE_COMPLETED /
	 * TRACE_FAILED) with the traceId metadata.
	 */
	public void traceEvent(EventType type, String traceId, String taskId, String status,
			String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("traceId", value(traceId));
		if (taskId != null) {
			enriched.put("taskId", taskId);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "trace", traceId, null, status, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "observability", summary,
			Map.copyOf(enriched),
			type + ":trace:" + value(traceId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a usage event (USAGE_RECORDED) with the token and cost metadata.
	 */
	public void usageEvent(EventType type, String usageId, String taskId, String projectId,
			String agentType, long inputTokens, long outputTokens, long totalTokens,
			double estimatedCost, String summary) {
		Map<String, Object> metadata = new java.util.LinkedHashMap<>();
		metadata.put("usageId", value(usageId));
		metadata.put("inputTokens", inputTokens);
		metadata.put("outputTokens", outputTokens);
		metadata.put("totalTokens", totalTokens);
		metadata.put("estimatedCost", estimatedCost);
		record(event(type, "usage", usageId, null, null, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM",
			agentType == null ? "usage" : agentType, summary, Map.copyOf(metadata),
			type + ":usage:" + value(usageId) + ":" + UUID.randomUUID()));
	}

	public void graphEvent(EventType type, String graphId, String taskId, String nodeId,
			String agentType, String status, String summary,
			Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("graphId", value(graphId));
		if (nodeId != null) {
			enriched.put("nodeId", nodeId);
		}
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "execution-graph", graphId, null, status, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM",
			agentType == null ? "graph-executor" : agentType, summary,
			Map.copyOf(enriched),
			type + ":graph:" + graphId + ":" + value(nodeId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an agent runtime session lifecycle event (SESSION_STARTED /
	 * PAUSED / RESUMED / STOPPED / COMPLETED / FAILED and
	 * CHECKPOINT_CREATED) carrying the taskId so it appears on the task
	 * timeline alongside the graph node events.
	 */
	public void sessionEvent(EventType type, String sessionId, String taskId,
			String fromStatus, String toStatus, String summary,
			Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("sessionId", value(sessionId));
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "agent-session", sessionId, fromStatus, toStatus, taskId, null,
			null, null, null, null, null, null, null, null, null, "SYSTEM", "agent-runtime",
			summary, Map.copyOf(enriched),
			type + ":session:" + value(sessionId) + ":" + value(fromStatus) + ":"
				+ value(toStatus) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an agent collaboration event (AGENT_TEAM_CREATED /
	 * AGENT_JOINED_TEAM / AGENT_MESSAGE_SENT / AGENT_HANDOFF /
	 * AGENT_COLLABORATION_COMPLETED / AGENT_COLLABORATION_FAILED) carrying
	 * the taskId and the team/from/to/messageType metadata so it appears on
	 * the task timeline alongside the runtime session events.
	 */
	public void collaborationEvent(EventType type, String teamId, String taskId,
			String sessionId, String fromAgent, String toAgent,
			AgentMessageType messageType, String fromStatus, String toStatus,
			String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("teamId", value(teamId));
		if (sessionId != null) {
			enriched.put("sessionId", sessionId);
		}
		if (fromAgent != null) {
			enriched.put("fromAgent", fromAgent);
		}
		if (toAgent != null) {
			enriched.put("toAgent", toAgent);
		}
		if (messageType != null) {
			enriched.put("messageType", messageType.name());
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "agent-team", teamId, fromStatus, toStatus, taskId, null,
			null, null, null, null, null, null, null, null, null, "SYSTEM",
			"agent-collaboration", summary, Map.copyOf(enriched),
			type + ":team:" + value(teamId) + ":" + value(fromAgent) + ":"
				+ value(toAgent) + ":" + value(messageType == null ? null
					: messageType.name()) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a human collaboration event (HUMAN_APPROVAL_CREATED /
	 * HUMAN_APPROVED / HUMAN_REJECTED / HUMAN_FEEDBACK_ADDED /
	 * HUMAN_RESUMED) carrying the taskId, sessionId and agentType so the
	 * approval and its resume appear on the task timeline between the agent
	 * team events and the session result.
	 */
	public void humanEvent(EventType type, String humanId, String taskId, String sessionId,
			String agentType, String fromStatus, String toStatus, String summary,
			Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("humanId", value(humanId));
		if (sessionId != null) {
			enriched.put("sessionId", sessionId);
		}
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "human-collaboration", humanId, fromStatus, toStatus, taskId,
			null, null, null, null, null, null, null, null, null, null, "SYSTEM",
			"human-collaboration", summary, Map.copyOf(enriched),
			type + ":human:" + value(humanId) + ":" + UUID.randomUUID()));
	}

	public void taskEvent(EventType type, String taskId, String fromStatus, String toStatus,
			String summary, Map<String, Object> metadata) {
		record(event(type, "task", taskId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "task-center", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":task:" + taskId + ":" + value(fromStatus) + ":" + value(toStatus)));
	}

	public void taskSubmitted(String taskId, String summary, Map<String, Object> metadata) {
		record(event(EventType.USER_OPERATION, "task", taskId, null, null, taskId, null, null,
			null, null, null, null, null, null, null, null, "USER", "USER", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			"USER_OPERATION:task:" + taskId + ":" + UUID.randomUUID()));
	}

	public void backlogEvent(EventType type, String backlogItemId, String taskId,
			String fromStatus, String toStatus, String summary, Map<String, Object> metadata) {
		record(event(type, "backlog-item", backlogItemId, fromStatus, toStatus, taskId,
			null, null, null, null, null, null, null, null, null, null, "USER",
			"backlog-center", summary, metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":backlog:" + backlogItemId + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an autonomous optimization event (OPTIMIZATION_STARTED /
	 * OPTIMIZATION_COMPLETED / OPTIMIZATION_RECOMMENDED / AGENT_SCORE_UPDATED)
	 * carrying the taskId, sessionId and optimization type so the analysis and
	 * its recommendations appear on the task timeline after the human events
	 * and before the learning (memory) writes.
	 */
	public void optimizationEvent(EventType type, String optimizationId, String taskId,
			String sessionId, String optimizationType, String summary,
			Map<String, Object> metadata) {
		String aggregateId = value(optimizationId).isBlank()
			? "optimization-" + UUID.randomUUID() : optimizationId;
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("optimizationId", value(optimizationId));
		if (sessionId != null) {
			enriched.put("sessionId", sessionId);
		}
		if (optimizationType != null) {
			enriched.put("optimizationType", optimizationType);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "optimization", aggregateId, null, null, taskId, null,
			null, null, null, null, null, null, null, null, null, "SYSTEM",
			"agent-optimization", summary, Map.copyOf(enriched),
			type + ":optimization:" + value(optimizationId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an autonomous orchestrator event (ORCHESTRATOR_STARTED /
	 * TASK_QUEUED / TASK_PRIORITIZED / AGENT_AUTO_SELECTED /
	 * DYNAMIC_GRAPH_CREATED) carrying the taskId and the pool / graph
	 * metadata so the queue -> priority -> agent selection -> dynamic graph
	 * trail appears on the task timeline before the runtime session events.
	 */
	public void orchestratorEvent(EventType type, String orchestrationId, String taskId,
			String fromStatus, String toStatus, String summary,
			Map<String, Object> metadata) {
		String aggregateId = value(orchestrationId).isBlank()
			? "orchestrator-" + UUID.randomUUID() : orchestrationId;
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("orchestrationId", value(orchestrationId));
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "orchestrator", aggregateId, fromStatus, toStatus, taskId,
			null, null, null, null, null, null, null, null, null, null, "SYSTEM",
			"autonomous-orchestrator", summary, Map.copyOf(enriched),
			type + ":orchestrator:" + value(orchestrationId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records a dynamic planning event (PLAN_CREATED / PLAN_EVALUATED /
	 * PLAN_OPTIMIZED / GRAPH_GENERATED) carrying the planId and the taskId so
	 * the plan -> evaluation -> optimization -> graph trail appears on the
	 * task timeline between the orchestrator selection and the runtime
	 * session events.
	 */
	public void plannerEvent(EventType type, String planId, String taskId,
			String fromStatus, String toStatus, String summary,
			Map<String, Object> metadata) {
		String aggregateId = value(planId).isBlank()
			? "plan-" + UUID.randomUUID() : planId;
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("planId", value(planId));
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "planner", aggregateId, fromStatus, toStatus, taskId,
			null, null, null, null, null, null, null, null, null, null, "SYSTEM",
			"dynamic-planner", summary, Map.copyOf(enriched),
			type + ":planner:" + value(planId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an adaptive execution event (EXECUTION_FEEDBACK_RECEIVED /
	 * ADAPTATION_STARTED / ADAPTATION_DECIDED / GRAPH_REPLANNED) carrying the
	 * taskId, sessionId, nodeId and agentType so the feedback -> decision ->
	 * replan trail appears on the task timeline after the runtime session
	 * events.
	 */
	public void adaptiveEvent(EventType type, String adaptiveId, String taskId,
			String sessionId, String nodeId, String agentType, String summary,
			Map<String, Object> metadata) {
		String aggregateId = value(adaptiveId).isBlank()
			? "adaptive-" + UUID.randomUUID() : adaptiveId;
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("adaptiveId", value(adaptiveId));
		if (sessionId != null) {
			enriched.put("sessionId", sessionId);
		}
		if (nodeId != null) {
			enriched.put("nodeId", nodeId);
		}
		if (agentType != null) {
			enriched.put("agentType", agentType);
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "adaptive", aggregateId, null, null, taskId,
			null, null, null, null, null, null, null, null, null, null, "SYSTEM",
			"adaptive-execution", summary, Map.copyOf(enriched),
			type + ":adaptive:" + value(adaptiveId) + ":" + UUID.randomUUID()));
	}

	/**
	 * Records an autonomous goal event (GOAL_CREATED / GOAL_PLANNING_STARTED /
	 * GOAL_DECOMPOSED / GOAL_TASK_CREATED / GOAL_PROGRESS_UPDATED /
	 * GOAL_COMPLETED / GOAL_FAILED) carrying the goal status transition and
	 * the goal metadata so the goal -> milestone -> task -> progress trail is
	 * visible on the goal aggregate.
	 */
	public void goalEvent(EventType type, String goalId, String fromStatus, String toStatus,
			String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new java.util.LinkedHashMap<>();
		enriched.put("goalId", value(goalId));
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		record(event(type, "goal", goalId, fromStatus, toStatus, null,
			null, null, null, null, null, null, null, null, null, null, "SYSTEM",
			"goal-manager", summary, Map.copyOf(enriched),
			type + ":goal:" + value(goalId) + ":" + UUID.randomUUID()));
	}

	public void planRunCreated(PlanRun run) {
		planRunEvent(EventType.PLAN_RUN_CREATED, run, null, run.getStatus().name());
	}

	public void planRunTransition(PlanRun run, String fromStatus, String toStatus) {
		if (value(fromStatus).equals(value(toStatus))) return;
		EventType type = switch (run.getStatus()) {
			case RUNNING -> "WAITING_APPROVAL".equals(fromStatus)
				? EventType.PLAN_RUN_RESUMED : EventType.PLAN_RUN_STARTED;
			case WAITING_APPROVAL -> EventType.PLAN_RUN_WAITING_APPROVAL;
			case SUCCESS -> EventType.PLAN_RUN_SUCCEEDED;
			case FAILED -> EventType.PLAN_RUN_FAILED;
			case REPLAN_REQUIRED -> EventType.PLAN_RUN_REPLAN_REQUIRED;
			case DRAFT -> EventType.PLAN_RUN_CREATED;
		};
		planRunEvent(type, run, fromStatus, toStatus);
	}

	public void planApprovalEvent(EventType type, PlanApprovalRequest approval, String fromStatus,
			String toStatus) {
		record(event(type, "plan-approval", approval.getId(), fromStatus, toStatus,
			approval.getRequestId(),
			approval.getPlanId(), approval.getPlanVersion(), null, null, null, null, null, null,
			null, approval.getId(), approval.getApprover() == null ? "SYSTEM" : "USER",
			approval.getApprover(), type.name(), Map.of("requestId", approval.getRequestId()),
			type + ":approval:" + approval.getId() + ":" + value(toStatus)));
	}

	private void planRunEvent(EventType type, PlanRun run, String fromStatus, String toStatus) {
		record(event(type, "plan-run", run.getId(), fromStatus, toStatus,
			run.getOriginalTaskId(), run.getPlanId(),
			run.getPlanVersion(), run.getId(), null, null, null, null, null, null,
			run.getApprovalId(), "SYSTEM", "plan-scheduler", type.name(), Map.of(),
			type + ":plan-run:" + run.getId() + ":" + value(fromStatus) + ":" + value(toStatus)
				+ ":" + currentAttemptId(run)));
	}

	private String currentAttemptId(PlanRun run) {
		return run.getSteps().stream().map(StepRun::getCurrentAttempt).filter(java.util.Objects::nonNull)
			.map(StepAttempt::getId).reduce((first, second) -> second).orElse("");
	}

	private EventRecord event(EventType type, String aggregateType, String aggregateId,
			String fromStatus, String toStatus, String taskId, String planId, Integer planVersion,
			String planRunId, String stepRunId, String attemptId, String jobId, String executionId,
			String executionRecordId, String invocationId, String approvalId, String actorType,
			String actorId, String summary, Map<String, Object> metadata, String idempotencyKey) {
		return eventAt(Instant.now(clock), type, aggregateType, aggregateId, fromStatus, toStatus,
			taskId, planId, planVersion, planRunId, stepRunId, attemptId, jobId, executionId,
			executionRecordId, invocationId, approvalId, actorType, actorId, summary, metadata,
			idempotencyKey);
	}

	private String taskId(TaskDefinition task) {
		String original = metadata(task, "originalTaskId");
		return original == null || original.isBlank() ? task.getId() : original;
	}

	private EventRecord eventAt(Instant occurredAt, EventType type, String aggregateType,
			String aggregateId, String fromStatus, String toStatus, String taskId, String planId,
			Integer planVersion, String planRunId, String stepRunId, String attemptId, String jobId,
			String executionId, String executionRecordId, String invocationId, String approvalId,
			String actorType, String actorId, String summary, Map<String, Object> metadata,
			String idempotencyKey) {
		return new EventRecord(UUID.randomUUID().toString(), type,
			occurredAt == null ? Instant.now(clock) : occurredAt, 0,
			aggregateType, aggregateId, fromStatus, toStatus, taskId, planId, planVersion,
			planRunId, stepRunId, attemptId, jobId, executionId, executionRecordId, invocationId,
			approvalId, actorType, actorId, summary, metadata, idempotencyKey, 1);
	}

	private String metadata(TaskDefinition task, String key) {
		Object value = task.getMetadata() == null ? null : task.getMetadata().get(key);
		return value instanceof String text ? text : null;
	}

	private Integer metadataInteger(TaskDefinition task, String key) {
		Object value = task.getMetadata() == null ? null : task.getMetadata().get(key);
		return value instanceof Number number ? number.intValue() : null;
	}

	private String value(String value) { return value == null ? "" : value; }
}
