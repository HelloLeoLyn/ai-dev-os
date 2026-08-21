package com.aidevos.orchestrator.validationplan;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.CheckExecutionStatus;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationStatus;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.RiskLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V1-B 确定性执行核心测试（0 LLM）。
 */
class ValidationPlanExecutionServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@TempDir
	java.nio.file.Path executionWorkspaceDir;

	private CommandExecutor commandExecutor;
	private ExecutionWorkspacePromotionService promotion;
	private InMemoryValidationRunResultRepository repository;
	private ValidationPlanExecutionService service;

	@BeforeEach
	void setUp() throws Exception {
		commandExecutor = mock(CommandExecutor.class);
		promotion = mock(ExecutionWorkspacePromotionService.class);
		repository = new InMemoryValidationRunResultRepository();
		service = new ValidationPlanExecutionService(commandExecutor, promotion, repository);
		ExecutionWorkspace workspace = mock(ExecutionWorkspace.class);
		when(workspace.getExecutionWorkspace()).thenReturn(executionWorkspaceDir.toString());
		java.nio.file.Files.createDirectories(
			executionWorkspaceDir.resolve("services/orchestrator"));
		when(promotion.findWorkspace("task-1")).thenReturn(workspace);
		when(promotion.changeFingerprint("task-1")).thenReturn("change-fp-1");
	}

	private CommandResult success() {
		CommandResult result = new CommandResult();
		result.setSuccess(true);
		result.setExitCode(0);
		result.setOutput("ok");
		return result;
	}

	private CommandResult failed(int exitCode) {
		CommandResult result = new CommandResult();
		result.setSuccess(false);
		result.setExitCode(exitCode);
		result.setOutput("failure output");
		result.setError("boom");
		return result;
	}

	private ValidationCheck check(CheckType type, List<String> arguments, String wd,
			boolean required) {
		return new ValidationCheck(type, type == CheckType.GIT_DIFF_CHECK ? "git"
			: type.name().startsWith("FRONTEND") ? "npm" : "maven", wd, arguments,
			required, "reason", CheckSource.MANDATORY, 300);
	}

	private ValidationPlan plan(String changeSetId, List<ValidationCheck> checks) {
		return new ValidationPlan("task-1", changeSetId, ValidationMode.LOCAL, "TARGETED",
			RiskLevel.MEDIUM, ConfidenceLevel.HIGH, checks, null, null, List.of(), false,
			NOW);
	}

	/** 1. BACKEND_COMPILE → deterministic executor → SUCCESS（0 AI） */
	@Test
	void backendCompileSucceedsWithoutAi() {
		when(commandExecutor.execute(org.mockito.ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(success());
		ValidationPlan plan = plan("change-1",
			List.of(check(CheckType.BACKEND_COMPILE, List.of("compile"), "services/orchestrator", true)));

		ValidationRunResult run = service.execute("task-1", "change-1", plan);

		assertEquals(ValidationStatus.SUCCESS, run.status());
		assertEquals(CheckExecutionStatus.SUCCESS, run.checks().get(0).status());
		verify(commandExecutor, times(1)).execute(org.mockito.ArgumentMatchers.any(CommandOptions.class));
	}

	/** 2. MAVEN_TARGETED_TEST → 正确 testClass + module workingDirectory */
	@Test
	void targetedTestUsesCorrectClassAndModuleDirectory() {
		when(commandExecutor.execute(org.mockito.ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(success());
		ValidationPlan plan = plan("change-2", List.of(check(CheckType.MAVEN_TARGETED_TEST,
			List.of("test", "-Dtest=FooServiceTest"), "services/orchestrator", true)));

		service.execute("task-1", "change-2", plan);

		ArgumentCaptor<CommandOptions> captor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(captor.capture());
		CommandOptions options = captor.getValue();
		assertTrue(options.getCommand().contains("-Dtest=FooServiceTest"),
			"必须使用正确 testClass: " + options.getCommand());
		assertEquals(executionWorkspaceDir.resolve("services/orchestrator").toString(),
			options.getWorkingDirectory(),
			"必须使用模块 workingDirectory（映射到 Execution Workspace）");
	}

	/** 3. compile FAILED → ValidationRun FAILED → 后续 Maven test 不执行 */
	@Test
	void compileFailureStopsSubsequentRequiredChecks() {
		when(commandExecutor.execute(org.mockito.ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(failed(1));
		ValidationPlan plan = plan("change-3", List.of(
			check(CheckType.BACKEND_COMPILE, List.of("compile"), "services/orchestrator", true),
			check(CheckType.MAVEN_TARGETED_TEST, List.of("test", "-Dtest=FooServiceTest"),
				"services/orchestrator", true)));

		ValidationRunResult run = service.execute("task-1", "change-3", plan);

		assertEquals(ValidationStatus.FAILED, run.status());
		assertEquals("BUILD_FAILED", run.checks().get(0).errorCode());
		assertEquals(CheckExecutionStatus.SKIPPED, run.checks().get(1).status(),
			"compile 失败后 Maven test 不得执行");
		verify(commandExecutor, times(1)).execute(org.mockito.ArgumentMatchers.any(CommandOptions.class));
	}

	/** 4. workingDirectory 越界 → fail closed */
	@Test
	void escapingWorkingDirectoryFailsClosed() {
		ValidationPlan plan = plan("change-4",
			List.of(check(CheckType.BACKEND_COMPILE, List.of("compile"), "../../etc", true)));

		ValidationRunResult run = service.execute("task-1", "change-4", plan);

		assertEquals(ValidationStatus.FAILED, run.status());
		assertEquals("WORKING_DIRECTORY_INVALID", run.checks().get(0).errorCode());
		verify(commandExecutor, never()).execute(org.mockito.ArgumentMatchers.any(CommandOptions.class));
	}

	/** 5. 相同 change+plan 已 SUCCESS → VALIDATION_REUSED → tool calls = 0 */
	@Test
	void identicalChangeAndPlanReusesPreviousSuccess() {
		when(commandExecutor.execute(org.mockito.ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(success());
		ValidationPlan plan = plan("change-5",
			List.of(check(CheckType.BACKEND_COMPILE, List.of("compile"), "services/orchestrator", true)));

		ValidationRunResult first = service.execute("task-1", "change-5", plan);
		ValidationRunResult second = service.execute("task-1", "change-5", plan);

		assertEquals(ValidationStatus.SUCCESS, first.status());
		assertTrue(second.reused(), "相同 change+plan 必须 REUSE");
		verify(commandExecutor, times(1)).execute(org.mockito.ArgumentMatchers.any(CommandOptions.class));
	}

	/** 6. change fingerprint 变化 → 不 reuse → 新执行 */
	@Test
	void changedFingerprintDoesNotReuse() {
		when(promotion.changeFingerprint("task-1")).thenReturn("fp-1", "fp-2");
		when(commandExecutor.execute(org.mockito.ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(success());
		ValidationPlan plan = plan("change-6",
			List.of(check(CheckType.BACKEND_COMPILE, List.of("compile"), "services/orchestrator", true)));

		ValidationRunResult first = service.execute("task-1", "change-6", plan);
		ValidationRunResult second = service.execute("task-1", "change-6", plan);

		assertFalse(first.reused());
		assertFalse(second.reused(), "change fingerprint 变化不得 reuse");
		verify(commandExecutor, times(2)).execute(org.mockito.ArgumentMatchers.any(CommandOptions.class));
	}

	/** 7. 完整 FinalPlan：diff-check + compile + targeted test → 全部 SUCCESS → ValidationRun SUCCESS */
	@Test
	void fullPlanSucceedsEndToEnd() {
		when(commandExecutor.execute(org.mockito.ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(success());
		ValidationPlan plan = plan("change-7", List.of(
			check(CheckType.GIT_DIFF_CHECK, List.of("diff", "--check"), "services/orchestrator", true),
			check(CheckType.BACKEND_COMPILE, List.of("compile"), "services/orchestrator", true),
			check(CheckType.MAVEN_TARGETED_TEST, List.of("test", "-Dtest=FooServiceTest"),
				"services/orchestrator", true)));

		ValidationRunResult run = service.execute("task-1", "change-7", plan);

		assertEquals(ValidationStatus.SUCCESS, run.status());
		assertEquals(3, run.checks().size());
		assertTrue(run.checks().stream()
			.allMatch(result -> result.status() == CheckExecutionStatus.SUCCESS));
		assertNotNull(run.runId());
		assertNotNull(run.finishedAt());
	}
}
