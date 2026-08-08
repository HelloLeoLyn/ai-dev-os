package com.aidevos.orchestrator.audit;

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
		record(event(type, "execution", aggregateId, null, status, task.getId(),
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
		Map<String, Object> metadata = new java.util.LinkedHashMap<>();
		metadata.put("providerId", invocation.providerId());
		metadata.put("toolName", invocation.toolName());
		if (resultCode != null) metadata.put("resultCode", resultCode);
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
		record(event(type, "agent-execution", aggregateId, null, status, task.getId(),
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
		record(event(type, "planning-request", requestId, null, status, null, null, null,
			null, null, null, null, null, null, null, null, "USER", requestId, type.name(),
			metadata, type + ":planning-request:" + requestId));
	}

	public void stepEvent(EventType type, PlanRun run, StepRun step, StepAttempt attempt,
			String fromStatus, String toStatus) {
		String attemptId = attempt == null ? null : attempt.getId();
		String jobId = attempt == null ? null : attempt.getJobId();
		String recordId = attempt == null ? null : attempt.getExecutionRecordId();
		record(event(type, "step-run", step.getId(), fromStatus, toStatus, null, run.getPlanId(),
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

	public void taskEvent(EventType type, String taskId, String fromStatus, String toStatus,
			String summary, Map<String, Object> metadata) {
		record(event(type, "task", taskId, fromStatus, toStatus, taskId, null, null, null,
			null, null, null, null, null, null, null, "SYSTEM", "task-center", summary,
			metadata == null ? Map.of() : Map.copyOf(metadata),
			type + ":task:" + taskId + ":" + value(fromStatus) + ":" + value(toStatus)));
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
		record(event(type, "plan-approval", approval.getId(), fromStatus, toStatus, null,
			approval.getPlanId(), approval.getPlanVersion(), null, null, null, null, null, null,
			null, approval.getId(), approval.getApprover() == null ? "SYSTEM" : "USER",
			approval.getApprover(), type.name(), Map.of("requestId", approval.getRequestId()),
			type + ":approval:" + approval.getId() + ":" + value(toStatus)));
	}

	private void planRunEvent(EventType type, PlanRun run, String fromStatus, String toStatus) {
		record(event(type, "plan-run", run.getId(), fromStatus, toStatus, null, run.getPlanId(),
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
