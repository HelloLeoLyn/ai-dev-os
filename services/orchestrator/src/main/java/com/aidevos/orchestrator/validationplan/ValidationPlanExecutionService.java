package com.aidevos.orchestrator.validationplan;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.CheckExecutionStatus;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationCheckResult;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationStatus;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * V1-B ValidationPlanExecutor：确定性执行 Final ValidationPlan。
 *
 * - 0 LLM / 0 AI Job（执行阶段永不调用 AI）
 * - 严格使用 plan 的 module workingDirectory（映射到 Execution Workspace，越界 fail closed）
 * - required check FAILED → 停止后续 required checks
 * - 同 change fingerprint + 同 plan fingerprint + 历史 SUCCESS → VALIDATION_REUSED（不重跑）
 * - 每个 check 从结构化 ValidationCheck 构造命令（禁止自然语言生成 shell）
 */
@Service
public class ValidationPlanExecutionService {

	private static final int MAX_OUTPUT_CHARS = 500;
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(600);

	private final CommandExecutor commandExecutor;
	private final ExecutionWorkspacePromotionService promotionService;
	private final ValidationRunResultRepository runRepository;
	private volatile AuditService audit;

	public ValidationPlanExecutionService(CommandExecutor commandExecutor,
			ExecutionWorkspacePromotionService promotionService,
			ValidationRunResultRepository runRepository) {
		this.commandExecutor = commandExecutor;
		this.promotionService = promotionService;
		this.runRepository = runRepository;
	}

	@Autowired(required = false)
	public void setAuditService(AuditService audit) {
		this.audit = audit;
	}

	public ValidationRunResult execute(String taskId, String changeSetId, ValidationPlan plan) {
		Instant startedAt = Instant.now();
		emit(EventType.VALIDATION_STARTED, taskId, null, "Validation started",
			Map.of("mode", plan.mode() == null ? "" : plan.mode().name()));

		ExecutionWorkspace workspace = promotionService.findWorkspace(taskId);
		if (workspace == null || workspace.getExecutionWorkspace() == null
				|| workspace.getExecutionWorkspace().isBlank()) {
			ValidationRunResult failed = failedRun(taskId, changeSetId, plan, startedAt,
				List.of(), "WORKING_DIRECTORY_INVALID", "No execution workspace for task");
			emit(EventType.VALIDATION_FAILED, taskId, null, "Validation failed: no workspace",
				Map.of());
			runRepository.save(failed);
			return failed;
		}
		Path executionRoot = Path.of(workspace.getExecutionWorkspace()).toAbsolutePath().normalize();
		String changeFingerprint = promotionService.changeFingerprint(taskId);
		String planFingerprint = planFingerprint(plan);

		// 八、幂等 / Reuse：同 change + 同 plan + 历史 SUCCESS → REUSE
		ValidationRunResult prior = runRepository.findReusable(taskId, changeFingerprint,
			planFingerprint);
		if (prior != null) {
			emit(EventType.VALIDATION_REUSED, taskId, null, "Validation reused",
				Map.of("planFingerprint", planFingerprint));
			return prior.withReused();
		}

		List<ValidationCheckResult> results = new ArrayList<>();
		ValidationStatus runStatus = ValidationStatus.SUCCESS;
		String failureCode = null;
		boolean stopped = false;

		for (ValidationCheck check : plan.checks()) {
			if (stopped) {
				results.add(skipped(check));
				continue;
			}
			emit(EventType.VALIDATION_CHECK_STARTED, taskId, check.type().name(),
				"Check started: " + check.type(), Map.of());
			ValidationCheckResult result = executeCheck(check, executionRoot);
			results.add(result);
			if (result.status() == CheckExecutionStatus.SUCCESS) {
				emit(EventType.VALIDATION_CHECK_SUCCEEDED, taskId, check.type().name(),
					"Check succeeded: " + check.type(), Map.of());
			}
			else {
				emit(EventType.VALIDATION_CHECK_FAILED, taskId, check.type().name(),
					"Check failed: " + check.type(), Map.of(
						"errorCode", result.errorCode() == null ? "" : result.errorCode(),
						"exitCode", result.exitCode() == null ? "" : String.valueOf(result.exitCode())));
				if (check.required()) {
					runStatus = ValidationStatus.FAILED;
					failureCode = result.errorCode();
					stopped = true; // required 失败 → 停止后续 required checks
				}
			}
		}

		ValidationRunResult run = new ValidationRunResult("validation-run-" + UUID.randomUUID(),
			taskId, changeSetId, planFingerprint, changeFingerprint,
			plan.mode() == null ? "" : plan.mode().name(),
			plan.profile() == null ? "" : plan.profile(), runStatus, startedAt,
			Instant.now(), false, List.copyOf(results));
		runRepository.save(run);
		emit(runStatus == ValidationStatus.SUCCESS
			? EventType.VALIDATION_SUCCEEDED : EventType.VALIDATION_FAILED,
			taskId, null,
			runStatus == ValidationStatus.SUCCESS ? "Validation succeeded"
				: "Validation failed" + (failureCode == null ? "" : ": " + failureCode),
			Map.of("planFingerprint", planFingerprint));
		return run;
	}

	// ==================== check 执行 ====================

	private ValidationCheckResult executeCheck(ValidationCheck check, Path executionRoot) {
		Instant startedAt = Instant.now();
		// 三、workingDirectory 严格映射 + 越界 fail closed
		Path workingDirectory;
		try {
			workingDirectory = resolveWorkingDirectory(check.workingDirectory(), executionRoot);
		}
		catch (IllegalArgumentException exception) {
			return result(check, CheckExecutionStatus.FAILED, "WORKING_DIRECTORY_INVALID",
				exception.getMessage(), startedAt, null);
		}
		if (!java.nio.file.Files.isDirectory(workingDirectory)) {
			return result(check, CheckExecutionStatus.FAILED, "WORKING_DIRECTORY_INVALID",
				"Working directory does not exist: " + workingDirectory, startedAt, null);
		}
		// 二、check → 确定性命令（从结构化 check 构造，无 shell 注入）
		List<String> command = commandOf(check);
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		options.setWorkingDirectory(workingDirectory.toString());
		options.setTimeout(check.timeoutSeconds() > 0
			? Duration.ofSeconds(check.timeoutSeconds()) : DEFAULT_TIMEOUT);
		CommandResult commandResult;
		try {
			commandResult = commandExecutor.execute(options);
		}
		catch (RuntimeException exception) {
			return result(check, CheckExecutionStatus.FAILED, "TOOL_EXECUTION_FAILED",
				exception.getMessage(), startedAt, null);
		}
		boolean success = commandResult.isSuccess() && commandResult.getExitCode() == 0;
		return result(check, success ? CheckExecutionStatus.SUCCESS : CheckExecutionStatus.FAILED,
			success ? null : errorCodeOf(check.type()), null, startedAt,
			commandResult.getExitCode() == 0 ? null : commandResult.getExitCode());
	}

	private Path resolveWorkingDirectory(String moduleWorkingDirectory, Path executionRoot) {
		String module = moduleWorkingDirectory == null || moduleWorkingDirectory.isBlank()
			? "." : moduleWorkingDirectory;
		Path resolved = executionRoot.resolve(module).normalize();
		// 逃逸 workspace boundary → fail closed
		if (!resolved.startsWith(executionRoot)) {
			throw new IllegalArgumentException(
				"Working directory escapes execution workspace: " + module);
		}
		return resolved;
	}

	/** 确定性映射（从 check.type + arguments 构造，禁止自然语言生成 shell）。 */
	private List<String> commandOf(ValidationCheck check) {
		return switch (check.type()) {
			case GIT_DIFF_CHECK -> List.of("git", "diff", "--check");
			case BACKEND_COMPILE -> List.of("mvn", "compile");
			case MAVEN_TARGETED_TEST -> List.of("mvn", "test",
				"-Dtest=" + selectedTestOf(check));
			case MAVEN_MODULE_TEST -> List.of("mvn", "test");
			case FRONTEND_TYPECHECK -> List.of("npm", "run", "type-check", "--",
				"vue-tsc", "--noEmit");
			case FRONTEND_TARGETED_TEST -> List.of("npm", "test", "--",
				selectedTestOf(check));
			case FRONTEND_BUILD -> List.of("npm", "run", "build");
		};
	}

	private String selectedTestOf(ValidationCheck check) {
		if (check.arguments() == null) {
			return "";
		}
		for (String argument : check.arguments()) {
			if (argument.startsWith("-Dtest=")) {
				return argument.substring("-Dtest=".length());
			}
		}
		return check.arguments().isEmpty() ? "" : check.arguments().get(check.arguments().size() - 1);
	}

	private String errorCodeOf(CheckType type) {
		return switch (type) {
			case GIT_DIFF_CHECK -> "DIFF_CHECK_FAILED";
			case BACKEND_COMPILE -> "BUILD_FAILED";
			case MAVEN_TARGETED_TEST, MAVEN_MODULE_TEST -> "TEST_FAILED";
			case FRONTEND_TYPECHECK -> "TYPECHECK_FAILED";
			case FRONTEND_TARGETED_TEST -> "TEST_FAILED";
			case FRONTEND_BUILD -> "BUILD_FAILED";
		};
	}

	private ValidationCheckResult result(ValidationCheck check, CheckExecutionStatus status,
			String errorCode, String failureMessage, Instant startedAt, Integer exitCode) {
		String snippet = failureMessage == null ? "" : snippet(failureMessage);
		return new ValidationCheckResult(check.type().name(), status,
			String.join(" ", commandOf(check)),
			check.workingDirectory() == null ? "" : check.workingDirectory(),
			exitCode, 0, snippet, errorCode,
			check.type() == CheckType.MAVEN_TARGETED_TEST
				|| check.type() == CheckType.FRONTEND_TARGETED_TEST ? selectedTestOf(check) : null,
			startedAt, Instant.now());
	}

	private ValidationCheckResult skipped(ValidationCheck check) {
		return new ValidationCheckResult(check.type().name(), CheckExecutionStatus.SKIPPED,
			String.join(" ", commandOf(check)),
			check.workingDirectory() == null ? "" : check.workingDirectory(),
			null, 0, "", null, null, null, null);
	}

	private ValidationRunResult failedRun(String taskId, String changeSetId, ValidationPlan plan,
			Instant startedAt, List<ValidationCheckResult> checks, String errorCode,
			String reason) {
		return new ValidationRunResult("validation-run-" + UUID.randomUUID(), taskId,
			changeSetId, planFingerprint(plan), "", plan.mode() == null ? ""
				: plan.mode().name(),
			plan.profile() == null ? "" : plan.profile(), ValidationStatus.FAILED,
			startedAt, Instant.now(), false, checks);
	}

	// ==================== fingerprint ====================

	/** plan fingerprint：mode + profile + checks（type+tool+args）摘要。 */
	public String planFingerprint(ValidationPlan plan) {
		StringBuilder raw = new StringBuilder();
		raw.append(plan.mode()).append("|").append(plan.profile()).append("|");
		for (ValidationCheck check : plan.checks()) {
			raw.append(check.type()).append(":").append(check.tool()).append(":")
				.append(check.arguments()).append(";");
		}
		return hash(raw.toString());
	}

	private String hash(String value) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++) {
				hex.append(String.format("%02x", digest[i]));
			}
			return hex.toString();
		}
		catch (Exception exception) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private void emit(EventType type, String taskId, String checkType, String summary,
			Map<String, Object> metadata) {
		if (audit == null) {
			return;
		}
		Map<String, Object> enriched = new java.util.LinkedHashMap<>(metadata);
		enriched.put("source", "validation-executor");
		audit.taskEvent(type, taskId, null, null, summary, enriched);
	}

	private static String snippet(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		return trimmed.length() <= MAX_OUTPUT_CHARS
			? trimmed : trimmed.substring(0, MAX_OUTPUT_CHARS) + "…";
	}
}
