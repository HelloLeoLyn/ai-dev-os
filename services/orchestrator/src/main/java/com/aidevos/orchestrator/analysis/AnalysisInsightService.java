package com.aidevos.orchestrator.analysis;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.aidevos.orchestrator.analysis.AnalysisEnums.ExtractorType;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Status;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskRepository;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisInsightService {
	public static final String EXTRACTOR_VERSION = "structured-v1";
	private static final String SCHEMA_VERSION = "1.0";
	private final AnalysisInsightRepository repository;
	private final TaskRepository tasks;
	private final PlanRunRepository planRuns;
	private final ExecutionRecordRepository executions;
	private final AnalysisPayloadValidator validator;
	private final AnalysisFingerprintService fingerprints;
	private final ObjectMapper mapper;
	private final AuditService audit;
	private final Clock clock;

	@Autowired
	public AnalysisInsightService(AnalysisInsightRepository repository, TaskRepository tasks,
			PlanRunRepository planRuns, ExecutionRecordRepository executions,
			AnalysisPayloadValidator validator, AnalysisFingerprintService fingerprints,
			ObjectMapper mapper, AuditService audit) {
		this(repository, tasks, planRuns, executions, validator, fingerprints, mapper, audit,
			Clock.systemUTC());
	}
	AnalysisInsightService(AnalysisInsightRepository repository, TaskRepository tasks,
			PlanRunRepository planRuns, ExecutionRecordRepository executions,
			AnalysisPayloadValidator validator, AnalysisFingerprintService fingerprints,
			ObjectMapper mapper, AuditService audit, Clock clock) {
		this.repository=repository; this.tasks=tasks; this.planRuns=planRuns; this.executions=executions;
		this.validator=validator; this.fingerprints=fingerprints; this.mapper=mapper; this.audit=audit;
		this.clock=clock;
	}

	public AnalysisInsightSet getByTaskId(String taskId) { return repository.findByTaskId(taskId); }

	public AnalysisInsightSet project(String taskId) { return project(taskId, false); }
	public AnalysisInsightSet retry(String taskId) { return project(taskId, true); }

	private AnalysisInsightSet project(String taskId, boolean retry) {
		TaskRecord task = requireEligibleTask(taskId);
		ExecutionRecord execution = latestSuccessfulExecution(taskId);
		AnalysisInsightSet existing = repository.findBySource(taskId, execution.getId(), EXTRACTOR_VERSION);
		if (!retry && existing != null && existing.status() == Status.SUCCEEDED) return existing;
		Instant now = Instant.now(clock);
		String id = existing == null ? stableId(taskId, execution.getId()) : existing.analysisId();
		AnalysisInsightSet pending = new AnalysisInsightSet(id, taskId, execution.getId(),
			task.getProjectId(), task.getWorkspaceId(), List.of(), ExtractorType.STRUCTURED,
			EXTRACTOR_VERSION, SCHEMA_VERSION, Status.PENDING, null, null, null,
			List.of(), List.of(), existing == null ? now : existing.createdAt(), now);
		AnalysisInsightSet current = repository.save(pending);
		current = repository.save(current.withStatus(Status.RUNNING, null, null, Instant.now(clock)));
		audit(current, EventType.ANALYSIS_PROJECTION_STARTED);
		try {
			// Re-read the durable record: success is forbidden until the artifact is actually persisted.
			ExecutionRecord persisted = executions.get(execution.getId());
			ExecutionArtifact artifact = analysisArtifact(persisted);
			JsonNode payload = mapper.readTree(artifact.getContent());
			String schema = requiredText(payload, "schemaVersion");
			List<Finding> findings = readList(payload.get("findings"), Finding.class);
			List<Recommendation> recommendations = readList(payload.get("recommendations"), Recommendation.class)
				.stream().map(recommendation -> new Recommendation(
					RecommendationIdentity.global(id, recommendation.localRecommendationId()),
					recommendation.localRecommendationId(), recommendation.findingIds(), recommendation.title(),
					recommendation.rationale(), recommendation.priority(), recommendation.risk(), recommendation.benefit(),
					recommendation.scope(), recommendation.dependencies(), recommendation.suggestedExecutionMode(),
					recommendation.approvalRequired(), recommendation.evidenceRefs(), recommendation.confidence(),
					recommendation.recommendedNextAction())).toList();
			validator.validate(findings, recommendations, persisted);
			String fingerprint = fingerprints.fingerprint(taskId, persisted.getId(), EXTRACTOR_VERSION, payload);
			EvidenceRef sourceRef = new EvidenceRef(AnalysisEnums.EvidenceType.ARTIFACT,
				artifact.getName(), "Structured analysis result", artifact.getType(), artifact.getUri(),
				null, sha256(artifact.getContent()));
			AnalysisInsightSet succeeded = new AnalysisInsightSet(id, taskId, persisted.getId(),
				task.getProjectId(), task.getWorkspaceId(), List.of(sourceRef), ExtractorType.STRUCTURED,
				EXTRACTOR_VERSION, schema, Status.SUCCEEDED, null, null, fingerprint, findings,
				recommendations, current.createdAt(), Instant.now(clock));
			succeeded = repository.save(succeeded); audit(succeeded, EventType.ANALYSIS_PROJECTION_SUCCEEDED);
			return succeeded;
		}
		catch (Exception exception) {
			AnalysisInsightSet failed = current.withStatus(Status.FAILED, "EXTRACTION_FAILED",
				message(exception), Instant.now(clock));
			failed = repository.save(failed); audit(failed, EventType.ANALYSIS_PROJECTION_FAILED);
			return failed;
		}
	}

	public void recoverInterrupted() {
		for (AnalysisInsightSet running : repository.findByStatus(Status.RUNNING)) {
			repository.save(running.withStatus(Status.FAILED, "PROJECTION_INTERRUPTED",
				"Projection was interrupted before completion; retry is safe",
				Instant.now(clock)));
		}
	}

	private TaskRecord requireEligibleTask(String id) {
		TaskRecord task = tasks.get(id);
		if (task == null) throw new IllegalArgumentException("Task not found: " + id);
		if (task.getStatus() != TaskStatus.SUCCESS && task.getStatus() != TaskStatus.COMPLETED)
			throw new IllegalStateException("Analysis projection requires a successful task");
		PlanRun run = task.getPlanRunId() == null ? null : planRuns.get(task.getPlanRunId());
		Object type = run == null || run.getPlan().snapshot() == null ? null
			: run.getPlan().snapshot().plannerMetadata().get("taskType");
		if (!"project-analysis".equals(type)) throw new IllegalStateException("Task is not project-analysis");
		return task;
	}
	private ExecutionRecord latestSuccessfulExecution(String taskId) {
		return executions.getAll().stream().filter(r -> taskId.equals(r.getTaskId()))
			.filter(r -> "SUCCESS".equals(r.getStatus()) || "SUCCEEDED".equals(r.getStatus()))
			.max(Comparator.comparing(ExecutionRecord::getCompletedAt,
				Comparator.nullsFirst(Comparator.naturalOrder()))).orElseThrow(() ->
				new IllegalStateException("Successful execution record not found"));
	}
	private ExecutionArtifact analysisArtifact(ExecutionRecord record) {
		if (record == null) throw new IllegalStateException("Persisted execution record not found");
		return record.getArtifacts().stream().filter(a -> "analysis-result".equals(a.getType())
			&& "analysis-result.json".equals(a.getName()) && a.getContent() != null
			&& !a.getContent().isBlank()).findFirst().orElseThrow(() ->
				new IllegalStateException("Complete analysis-result.json artifact not found"));
	}
	private <T> List<T> readList(JsonNode node, Class<T> type) {
		if (node == null || !node.isArray()) throw new IllegalArgumentException("Expected array payload");
		return mapper.convertValue(node, mapper.getTypeFactory().constructCollectionType(List.class, type));
	}
	private String requiredText(JsonNode node, String name) {
		JsonNode value=node.get(name); if (value==null || !value.isTextual() || value.asText().isBlank())
			throw new IllegalArgumentException(name + " is required"); return value.asText();
	}
	private String stableId(String task, String execution) {
		return "analysis-" + UUID.nameUUIDFromBytes((task+"\n"+execution+"\n"+EXTRACTOR_VERSION)
			.getBytes(StandardCharsets.UTF_8));
	}
	private String sha256(String value) {
		try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
			.digest(value.getBytes(StandardCharsets.UTF_8))); }
		catch (Exception exception) { throw new IllegalStateException(exception); }
	}
	private void audit(AnalysisInsightSet insight, EventType type) {
		audit.adminEvent(type, "analysis-insight", insight.analysisId(), "SYSTEM", type.name(),
			java.util.Map.of("taskId", insight.sourceTaskId(), "status", insight.status().name()));
	}
	private String message(Exception exception) {
		return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
