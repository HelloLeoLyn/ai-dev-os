package com.aidevos.orchestrator.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.validation.provider.ValidationCheckResult;
import com.aidevos.orchestrator.validation.provider.ValidationContext;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import com.aidevos.orchestrator.validation.browser.BrowserScenario;
import com.aidevos.orchestrator.validation.browser.BrowserScenarioCatalog;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
	private static final List<ValidationCheckType> V1_CHECKS = List.of(
		ValidationCheckType.BACKEND_TEST, ValidationCheckType.BACKEND_BUILD,
		ValidationCheckType.FRONTEND_TEST, ValidationCheckType.FRONTEND_BUILD,
		ValidationCheckType.E2E, ValidationCheckType.CI);
	private static final List<String> SECURITY_SCANNERS = List.of("GITLEAKS", "SEMGREP", "TRIVY");

	private final ValidationRepository repository;
	private final TaskCenterService taskCenterService;
	private final WorkspaceService workspaceService;
	private final ProjectCapabilityDetector detector;
	private final List<ValidationProvider> providers;
	private final ValidationEvidenceService evidenceService;
	private final AuditService auditService;
	private final BrowserScenarioCatalog browserScenarios;
	private volatile ChangeService changeService;
	private volatile ExecutionWorkspacePromotionService executionWorkspaces;

	public ValidationService(ValidationRepository repository, TaskCenterService taskCenterService,
			WorkspaceService workspaceService, ProjectCapabilityDetector detector,
			List<ValidationProvider> providers, ValidationEvidenceService evidenceService,
			AuditService auditService) {
		this.repository = repository;
		this.taskCenterService = taskCenterService;
		this.workspaceService = workspaceService;
		this.detector = detector;
		this.providers = List.copyOf(providers);
		this.evidenceService = evidenceService;
		this.auditService = auditService;
		this.browserScenarios = null;
	}

	@org.springframework.beans.factory.annotation.Autowired
	public ValidationService(ValidationRepository repository, TaskCenterService taskCenterService,
			WorkspaceService workspaceService, ProjectCapabilityDetector detector,
			List<ValidationProvider> providers, ValidationEvidenceService evidenceService,
			AuditService auditService, BrowserScenarioCatalog browserScenarios) {
		this.repository = repository; this.taskCenterService = taskCenterService;
		this.workspaceService = workspaceService; this.detector = detector;
		this.providers = List.copyOf(providers); this.evidenceService = evidenceService;
		this.auditService = auditService; this.browserScenarios = browserScenarios;
	}

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setChangeService(ChangeService value) { this.changeService = value; }
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setExecutionWorkspaces(ExecutionWorkspacePromotionService value) { this.executionWorkspaces = value; }

	public ValidationRun start(String taskId) {
		return start(taskId, null);
	}

	public ValidationRun start(String taskId, String browserScenarioId) {
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
		Workspace workspace = requireOwnedWorkspace(task);
		Path workspacePath = Path.of(workspace.getPath()).toAbsolutePath().normalize();
		if (!Files.isDirectory(workspacePath)) {
			throw new IllegalArgumentException("Task workspace is not a directory: " + workspacePath);
		}
		String runId = "validation-" + UUID.randomUUID();
		ValidationRun run = new ValidationRun(runId, taskId, task.getProjectId(),
			workspace.getWorkspaceId(), task.getPlanRunId(), null);
		run.setStatus(ValidationStatus.RUNNING);
		run.setStartedAt(Instant.now());
		repository.save(run);
		audit(run, EventType.VALIDATION_STARTED, null, ValidationStatus.RUNNING,
			"Validation started", Map.of("workspaceId", workspace.getWorkspaceId()));

		return executeChecks(run, workspacePath, browserScenarioId);
	}

	public ValidationRun startDelivery(String changeSetId) {
		if (changeService == null || executionWorkspaces == null)
			throw new IllegalStateException("Delivery validation is not configured");
		ChangeSet change = changeService.getChange(changeSetId)
			.orElseThrow(() -> new ResourceNotFoundException("Change", changeSetId));
		if (change.getStatus() != ChangeStatus.APPROVED)
			throw new IllegalStateException("Change must be APPROVED before validation");
		ExecutionWorkspace workspace = executionWorkspaces.findWorkspace(change.getTaskId());
		if (workspace == null || !workspace.getId().equals(change.getWorkspaceId())
				|| workspace.getStatus() != ExecutionWorkspaceStatus.COMPLETED)
			throw new IllegalStateException("Approved change is not bound to a completed execution workspace");
		Path path = Path.of(workspace.getExecutionWorkspace()).toAbsolutePath().normalize();
		if (!Files.isDirectory(path)) throw new IllegalStateException("Execution workspace is unavailable");
		ValidationRun run = new ValidationRun("validation-" + UUID.randomUUID(), change.getTaskId(),
			change.getProjectId(), workspace.getId(), null, change.getExecutionId());
		run.setExecutionWorkspaceId(workspace.getId());
		run.setExecutionBranch(workspace.getExecutionBranch());
		run.setBaseRevision(workspace.getBaseRevision());
		run.setChangeSetId(changeSetId);
		run.setDelivery(true);
		run.setValidatedChangeFingerprint(executionWorkspaces.changeFingerprint(change.getTaskId()));
		run.setStatus(ValidationStatus.RUNNING); run.setStartedAt(Instant.now()); repository.save(run);
		audit(run, EventType.VALIDATION_STARTED, null, ValidationStatus.RUNNING,
			"Delivery validation started", Map.of("changeSetId", changeSetId,
				"executionWorkspaceId", workspace.getId()));
		return executeChecks(run, path, null);
	}

	private ValidationRun executeChecks(ValidationRun run, Path workspacePath, String browserScenarioId) {
		Map<String, Object> capabilities = detector.detect(workspacePath);
		for (ValidationCheckType type : V1_CHECKS) {
			run.getChecks().add(executeCheck(run, workspacePath, capabilities, type));
			repository.save(run);
		}
		if (capabilities.containsKey("engineeringPlatformProjectYaml")) {
			run.getChecks().add(executeCheck(run, workspacePath, capabilities, ValidationCheckType.CONTRACT));
			repository.save(run);
		}
		audit(run, EventType.SECURITY_VALIDATION_STARTED, null, ValidationStatus.RUNNING,
			"Security validation started", Map.of("scannerCount", SECURITY_SCANNERS.size()));
		for (String scanner : SECURITY_SCANNERS) {
			Map<String,Object> securityCapabilities = new java.util.LinkedHashMap<>(capabilities);
			securityCapabilities.put("securityScanner", scanner);
			run.getChecks().add(executeCheck(run, workspacePath, Map.copyOf(securityCapabilities), ValidationCheckType.SECURITY));
			repository.save(run);
		}
		BrowserScenario browserScenario = browserScenarioId == null || browserScenarioId.isBlank()
			? null : requireBrowserCatalog().require(browserScenarioId);
		if (browserScenario != null) {
			Map<String,Object> browserCapabilities = new java.util.LinkedHashMap<>(capabilities);
			browserCapabilities.put("browserScenario", browserScenario);
			run.getChecks().add(executeCheck(run, workspacePath, Map.copyOf(browserCapabilities), ValidationCheckType.BROWSER));
			repository.save(run);
		}
		if (run.isDelivery() && executionWorkspaces != null) {
			run.setValidatedChangeFingerprint(executionWorkspaces.changeFingerprint(run.getTaskId()));
		}
		audit(run, EventType.SECURITY_VALIDATION_COMPLETED, ValidationStatus.RUNNING,
			ValidationStatus.SUCCESS, "Security validation completed", Map.of("scannerCount", SECURITY_SCANNERS.size()));
		complete(run);
		return run;
	}

	public List<ValidationRun> findByTask(String taskId) {
		if (taskCenterService.getTask(taskId).isEmpty()) {
			throw new ResourceNotFoundException("Task", taskId);
		}
		return repository.findByTaskId(taskId);
	}

	public List<ValidationRun> list() { return repository.list(); }

	public ValidationRun get(String runId) {
		ValidationRun run = repository.get(runId);
		if (run == null) throw new ResourceNotFoundException("ValidationRun", runId);
		return run;
	}

	private Workspace requireOwnedWorkspace(TaskRecord task) {
		if (task.getWorkspaceId() == null || task.getWorkspaceId().isBlank()) {
			throw new IllegalArgumentException("Task has no bound workspace: " + task.getTaskId());
		}
		Workspace workspace = workspaceService.getWorkspace(task.getWorkspaceId())
			.orElseThrow(() -> new ResourceNotFoundException("Workspace", task.getWorkspaceId()));
		if (!workspaceService.checkProjectOwnership(task.getProjectId(), task.getWorkspaceId())) {
			throw new IllegalArgumentException("Task workspace does not belong to project");
		}
		return workspace;
	}

	private ValidationCheck executeCheck(ValidationRun run, Path workspace,
			Map<String, Object> capabilities, ValidationCheckType type) {
		boolean required = type == ValidationCheckType.BROWSER
			? ((BrowserScenario) capabilities.get("browserScenario")).required()
			: type != ValidationCheckType.CI && type != ValidationCheckType.E2E && type != ValidationCheckType.SECURITY;
		ValidationCheck check = new ValidationCheck("check-" + UUID.randomUUID(), type,
			type==ValidationCheckType.SECURITY?"Security / "+capabilities.get("securityScanner"):name(type), required, required);
		check.setStatus(ValidationStatus.RUNNING);
		check.setStartedAt(Instant.now());
		audit(run, EventType.VALIDATION_CHECK_STARTED, null, ValidationStatus.RUNNING,
			check.getName() + " started", Map.of("checkId", check.getCheckId(), "checkType", type.name()));
		Map<String,Object> contextCapabilities = new java.util.LinkedHashMap<>(capabilities);
		contextCapabilities.put("validationCheckId", check.getCheckId());
		ValidationContext context = new ValidationContext(run.getValidationRunId(), run.getTaskId(),
			run.getProjectId(), run.getWorkspaceId(), workspace, type, Map.copyOf(contextCapabilities),
			run.isDelivery());
		ValidationProvider provider = providers.stream().filter(candidate -> candidate.supports(context))
			.findFirst().orElse(null);
		ValidationCheckResult result;
		if (provider == null) result = ValidationCheckResult.skipped("Not applicable for this workspace");
		else {
			try { result = provider.execute(context); }
			catch (RuntimeException exception) {
				result = new ValidationCheckResult(type == ValidationCheckType.BROWSER ? ValidationStatus.ERROR : ValidationStatus.FAILED, "Provider failed",
					errorMessage(exception), null, null, List.of(), Map.of());
			}
			check.getMetadata().put("provider", provider.name());
		}
		applyResult(run, check, result);
		return check;
	}

	private void applyResult(ValidationRun run, ValidationCheck check, ValidationCheckResult result) {
		check.setStatus(result.status());
		check.setSummary(result.summary());
		check.setErrorMessage(result.errorMessage());
		if (result.metadata() != null) check.getMetadata().putAll(result.metadata());
		Object existingArtifacts=check.getMetadata().get("artifactIds");
		if(existingArtifacts instanceof List<?> ids) for(Object id:ids) if(id!=null) check.getArtifactIds().add(id.toString());
		String log = joinOutput(result.stdout(), result.stderr());
		if (log != null || result.metadata().containsKey("command")) {
			check.getArtifactIds().add(evidenceService.saveLog(run.getValidationRunId(),
				check.getCheckId(), run.getTaskId(), log == null ? "" : log, check.getMetadata()));
		}
		if (result.reportPaths() != null) for (String path : result.reportPaths()) {
			check.getArtifactIds().add(evidenceService.saveReference(run.getValidationRunId(),
				check.getCheckId(), run.getTaskId(), path, check.getMetadata()));
		}
		check.setCompletedAt(Instant.now());
		check.setDurationMs(Duration.between(check.getStartedAt(), check.getCompletedAt()).toMillis());
		EventType event = result.status() == ValidationStatus.FAILED || result.status() == ValidationStatus.ERROR
			? EventType.VALIDATION_CHECK_FAILED : EventType.VALIDATION_CHECK_SUCCEEDED;
		audit(run, event, ValidationStatus.RUNNING, result.status(), check.getName() + " "
			+ result.status().name().toLowerCase(), Map.of("checkId", check.getCheckId(),
				"checkType", check.getType().name()));
	}

	private void complete(ValidationRun run) {
		boolean failed = run.getChecks().stream().anyMatch(check -> check.isRequired()
			&& (check.getStatus() == ValidationStatus.FAILED || check.getStatus() == ValidationStatus.ERROR));
		run.setDecision(failed ? ValidationDecision.FAIL : ValidationDecision.PASS);
		run.setStatus(failed ? ValidationStatus.FAILED : ValidationStatus.SUCCESS);
		run.setCompletedAt(Instant.now());
		long passed = run.getChecks().stream().filter(check -> check.getStatus() == ValidationStatus.SUCCESS).count();
		long skipped = run.getChecks().stream().filter(check -> check.getStatus() == ValidationStatus.SKIPPED).count();
		run.setSummary(passed + " succeeded, " + skipped + " skipped, "
			+ (run.getChecks().size() - passed - skipped) + " incomplete or failed");
		repository.save(run);
		audit(run, EventType.VALIDATION_COMPLETED, ValidationStatus.RUNNING, run.getStatus(),
			"Validation completed: " + run.getDecision(), Map.of("decision", run.getDecision().name()));
	}

	private void audit(ValidationRun run, EventType type, ValidationStatus from,
			ValidationStatus to, String summary, Map<String, Object> metadata) {
		auditService.validationEvent(type, run.getTaskId(), run.getValidationRunId(),
			from == null ? null : from.name(), to == null ? null : to.name(), summary, metadata);
	}

	private String name(ValidationCheckType type) {
		return switch (type) {
			case BACKEND_TEST -> "Backend Test";
			case BACKEND_BUILD -> "Backend Build";
			case FRONTEND_TEST -> "Frontend Test";
			case FRONTEND_BUILD -> "Frontend Build";
			case E2E -> "E2E";
			case CI -> "CI";
			default -> type.name();
		};
	}

	private String joinOutput(String stdout, String stderr) {
		if ((stdout == null || stdout.isBlank()) && (stderr == null || stderr.isBlank())) return null;
		return (stdout == null ? "" : stdout) + (stderr == null || stderr.isBlank() ? ""
			: System.lineSeparator() + stderr);
	}

	private String errorMessage(Exception exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private BrowserScenarioCatalog requireBrowserCatalog() {
		if (browserScenarios == null) throw new IllegalStateException("Browser scenario catalog is not configured");
		return browserScenarios;
	}
}
