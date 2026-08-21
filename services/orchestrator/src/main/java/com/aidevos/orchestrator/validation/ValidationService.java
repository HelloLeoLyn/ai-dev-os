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
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.CheckExecutionStatus;
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
	private volatile com.aidevos.orchestrator.validationplan.ValidationPlanService validationPlanService;
	private volatile com.aidevos.orchestrator.validationplan.ValidationPlanExecutionService validationPlanExecutionService;

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
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setValidationPlanServices(
			com.aidevos.orchestrator.validationplan.ValidationPlanService planService,
			com.aidevos.orchestrator.validationplan.ValidationPlanExecutionService executionService) {
		this.validationPlanService = planService;
		this.validationPlanExecutionService = executionService;
	}

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
		String fingerprint = executionWorkspaces.changeFingerprint(change.getTaskId());
		ValidationRun reused = reusableDeliveryRun(change, fingerprint);
		if (reused != null) {
			audit(reused, EventType.VALIDATION_REUSED, null, null,
				"Reused unchanged delivery validation " + reused.getValidationRunId(),
				Map.of("changeSetId", changeSetId, "fingerprint", fingerprint));
			return reused;
		}
		// V1-C：唯一主链 = Multi-Mode Validation Planning (AUTO) + Deterministic Execution。
		// ValidationService 不再自己决定跑什么测试/workingDirectory——全部来自 Final ValidationPlan。
		if (validationPlanService == null || validationPlanExecutionService == null) {
			throw new IllegalStateException("V1-C validation planning/execution is not configured");
		}
		ValidationRun run = new ValidationRun("validation-" + UUID.randomUUID(), change.getTaskId(),
			change.getProjectId(), workspace.getId(), null, change.getExecutionId());
		run.setExecutionWorkspaceId(workspace.getId());
		run.setExecutionBranch(workspace.getExecutionBranch());
		run.setBaseRevision(workspace.getBaseRevision());
		run.setChangeSetId(changeSetId);
		run.setDelivery(true);
		run.setValidatedChangeFingerprint(fingerprint);
		run.setStatus(ValidationStatus.RUNNING); run.setStartedAt(Instant.now()); repository.save(run);
		audit(run, EventType.VALIDATION_STARTED, null, ValidationStatus.RUNNING,
			"Delivery validation started", Map.of("changeSetId", changeSetId,
				"executionWorkspaceId", workspace.getId()));
		try {
			List<String> files = parseDiffFiles(change.getDiff());
			com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan plan =
				validationPlanService.generate(change.getTaskId(), changeSetId, files,
					com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode.AUTO,
					null);
			run.getMetadata().put("planMode", plan.mode() == null ? "" : plan.mode().name());
			run.getMetadata().put("planProfile", plan.profile() == null ? "" : plan.profile());
			run.getMetadata().put("planRisk", plan.risk() == null ? "" : plan.risk().name());
			run.getMetadata().put("planConfidence",
				plan.confidence() == null ? "" : plan.confidence().name());
			run.getMetadata().put("planFingerprint", validationPlanExecutionService.planFingerprint(plan));
			com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult result =
				validationPlanExecutionService.execute(change.getTaskId(), changeSetId, plan);
			mapPlanResult(run, result);
		}
		catch (RuntimeException exception) {
			run.setStatus(ValidationStatus.ERROR);
			run.setCompletedAt(Instant.now());
			run.setSummary("Validation error: " + exception.getMessage());
			repository.save(run);
			audit(run, EventType.VALIDATION_FAILED, null, ValidationStatus.ERROR,
				"Delivery validation error: " + exception.getMessage(),
				Map.of("changeSetId", changeSetId));
			throw exception;
		}
		if (run.getStatus() == ValidationStatus.SUCCESS) {
			audit(run, EventType.VALIDATION_SUCCEEDED, null, ValidationStatus.SUCCESS,
				"Delivery validation succeeded",
				Map.of("changeSetId", changeSetId, "checks", run.getChecks().size()));
		}
		else if (run.getStatus() == ValidationStatus.FAILED) {
			audit(run, EventType.VALIDATION_FAILED, null, ValidationStatus.FAILED,
				failureReason(run), Map.of("changeSetId", changeSetId));
		}
		return run;
	}

	/**
	 * Reuses a previous SUCCESS delivery validation for the same ChangeSet
	 * when the execution-workspace fingerprint is unchanged. A different
	 * fingerprint (or a FAILED/ERROR run) invalidates the cache and forces a
	 * fresh run.
	 */
	private ValidationRun reusableDeliveryRun(ChangeSet change, String fingerprint) {
		if (fingerprint == null || fingerprint.isBlank()) {
			return null;
		}
		for (ValidationRun candidate : repository.findByTaskId(change.getTaskId())) {
			if (candidate.isDelivery()
					&& change.getChangeId().equals(candidate.getChangeSetId())
					&& candidate.getStatus() == ValidationStatus.SUCCESS
					&& fingerprint.equals(candidate.getValidatedChangeFingerprint())) {
				return candidate;
			}
		}
		return null;
	}

	/** git diff 文本 → changed file paths（diff --git a/x b/y 行取 b/ 路径）。 */
	private static List<String> parseDiffFiles(String diff) {
		List<String> files = new ArrayList<>();
		if (diff == null) {
			return files;
		}
		for (String line : diff.split("\\R")) {
			if (line.startsWith("diff --git ")) {
				int bIndex = line.indexOf(" b/");
				if (bIndex >= 0) {
					String path = line.substring(bIndex + 3).trim();
					if (!path.isBlank() && !path.endsWith("/dev/null")) {
						files.add(path);
					}
				}
			}
		}
		return files;
	}

	/**
	 * V1-C Failure Mapping：把最关键失败 Check 映射成 Delivery/Diagnosis 可消费的
	 * 结构化失败描述（checkType / errorCode / selectedTest / exitCode / workingDirectory）。
	 */
	public String failureReason(ValidationRun run) {
		for (ValidationCheck check : run.getChecks()) {
			if (check.getStatus() == ValidationStatus.FAILED
					|| check.getStatus() == ValidationStatus.ERROR) {
				String checkType = (String) check.getMetadata().getOrDefault("planCheckType",
					check.getType().name());
				String errorCode = (String) check.getMetadata().getOrDefault("errorCode",
					check.getErrorMessage() == null ? "" : check.getErrorMessage());
				String selectedTest = (String) check.getMetadata().getOrDefault("selectedTest", "");
				String exitCode = (String) check.getMetadata().getOrDefault("exitCode", "");
				String wd = (String) check.getMetadata().getOrDefault("workingDirectory", "");
				StringBuilder reason = new StringBuilder("Validation failed: ")
					.append(checkType == null ? "" : checkType)
					.append(" / ").append(errorCode == null ? "" : errorCode);
				if (selectedTest != null && !selectedTest.isBlank()) {
					reason.append(" / ").append(selectedTest);
				}
				if (exitCode != null && !exitCode.isBlank()) {
					reason.append(" / exitCode=").append(exitCode);
				}
				if (wd != null && !wd.isBlank()) {
					reason.append(" / ").append(wd);
				}
				return reason.toString();
			}
		}
		return "Validation failed";
	}

	/** V1-C：ValidationRunResult → 现有 ValidationRun（最小 adapter，不重写 Validation domain）。 */
	private void mapPlanResult(ValidationRun run,
			com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult result) {
		run.getMetadata().put("planFingerprint", result.planFingerprint());
		run.getMetadata().put("reused", result.reused());
		int passed = 0;
		int index = 0;
		for (com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationCheckResult check
				: result.checks()) {
			index++;
			ValidationCheck mapped = new ValidationCheck("check-" + index,
				mapCheckType(check.checkType()), check.checkType(),
				check.status() != CheckExecutionStatus.SKIPPED, true);
			mapped.setStatus(mapCheckStatus(check.status()));
			mapped.setStartedAt(check.startedAt());
			mapped.setCompletedAt(check.finishedAt());
			mapped.setDurationMs(check.durationMillis());
			mapped.setSummary(check.commandSummary());
			if (check.status() == CheckExecutionStatus.FAILED) {
				mapped.setErrorMessage(check.errorCode());
				mapped.getMetadata().put("planCheckType", check.checkType());
				mapped.getMetadata().put("errorCode", check.errorCode());
				mapped.getMetadata().put("selectedTest",
					check.selectedTest() == null ? "" : check.selectedTest());
				mapped.getMetadata().put("exitCode",
					check.exitCode() == null ? "" : String.valueOf(check.exitCode()));
				mapped.getMetadata().put("workingDirectory",
					check.workingDirectory() == null ? "" : check.workingDirectory());
				mapped.getMetadata().put("outputSnippet",
					check.outputSnippet() == null ? "" : check.outputSnippet());
			}
			run.getChecks().add(mapped);
			if (check.status() == CheckExecutionStatus.SUCCESS) {
				passed++;
			}
		}
		boolean success = result.reused()
			|| result.status() == com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationStatus.SUCCESS;
		run.setStatus(success ? ValidationStatus.SUCCESS : ValidationStatus.FAILED);
		run.setCompletedAt(result.finishedAt() == null ? Instant.now() : result.finishedAt());
		run.setDecision(success ? ValidationDecision.PASS : ValidationDecision.FAIL);
		run.getMetadata().put("checksPassed", passed);
		run.getMetadata().put("checksTotal", result.checks().size());
		run.setSummary(result.reused() ? "Validation reused (SUCCESS)"
			: success ? "Validation passed " + passed + "/" + result.checks().size()
				: failureReason(run));
		repository.save(run);
	}

	private ValidationCheckType mapCheckType(String planCheckType) {
		if (planCheckType == null) {
			return ValidationCheckType.GENERIC;
		}
		return switch (planCheckType) {
			case "BACKEND_COMPILE", "FRONTEND_BUILD" -> planCheckType.equals("FRONTEND_BUILD")
				? ValidationCheckType.FRONTEND_BUILD : ValidationCheckType.BACKEND_BUILD;
			case "MAVEN_TARGETED_TEST", "MAVEN_MODULE_TEST" -> ValidationCheckType.BACKEND_TEST;
			case "FRONTEND_TYPECHECK", "FRONTEND_TARGETED_TEST" -> ValidationCheckType.FRONTEND_TEST;
			case "GIT_DIFF_CHECK" -> ValidationCheckType.GENERIC;
			default -> ValidationCheckType.GENERIC;
		};
	}

	private ValidationStatus mapCheckStatus(
			com.aidevos.orchestrator.validationplan.ValidationExecutionModels.CheckExecutionStatus status) {
		return switch (status) {
			case SUCCESS -> ValidationStatus.SUCCESS;
			case FAILED -> ValidationStatus.FAILED;
			case SKIPPED -> ValidationStatus.SKIPPED;
		};
	}

	/**
	 * V1-FLOW-CONFORMANCE：pipeline 重建/历史任务复用——不要求 change 为 APPROVED，
	 * 只要有该 change 的已有 SUCCESS delivery run 就直接复用（不重复测试）。
	 * fingerprint 精确复用仍由 startDelivery 内部的 reusableDeliveryRun 负责。
	 */
	public ValidationRun findReusableDeliveryRun(String taskId, String changeSetId) {
		if (taskId == null || changeSetId == null) {
			return null;
		}
		for (ValidationRun candidate : repository.findByTaskId(taskId)) {
			if (candidate.isDelivery()
					&& changeSetId.equals(candidate.getChangeSetId())
					&& candidate.getStatus() == ValidationStatus.SUCCESS) {
				return candidate;
			}
		}
		return null;
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
