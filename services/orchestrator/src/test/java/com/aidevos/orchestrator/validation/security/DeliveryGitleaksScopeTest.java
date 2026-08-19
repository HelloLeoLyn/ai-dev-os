package com.aidevos.orchestrator.validation.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.human.InMemoryHumanApprovalRepository;
import com.aidevos.orchestrator.qualitygate.InMemoryQualityGateRepository;
import com.aidevos.orchestrator.qualitygate.QualityGateDecision;
import com.aidevos.orchestrator.qualitygate.QualityGatePolicy;
import com.aidevos.orchestrator.qualitygate.QualityGateResult;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.InMemoryValidationArtifactRepository;
import com.aidevos.orchestrator.validation.InMemoryValidationRepository;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationEvidenceService;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.provider.GitleaksValidationProvider;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.validation.provider.SemgrepValidationProvider;
import com.aidevos.orchestrator.validation.provider.TrivyValidationProvider;
import com.aidevos.orchestrator.validation.provider.ValidationContext;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryGitleaksScopeTest {
	@TempDir Path repoDir;

	@Test
	void deliveryGitleaksIgnoresHistoricalFixtureRemovedFromTree() throws Exception {
		assumeScanners();
		initRepoWithHistoricalFixture();

		Fixture fixture = fixture();
		ValidationRun run = fixture.service().startDelivery("change-delivery");
		SecurityReport gitleaks = report(fixture, run, SecurityScannerType.GITLEAKS);
		assertEquals(0, gitleaks.getFindings().stream()
			.filter(f -> f.getCategory() == SecurityCategory.SECRET).count());

		QualityGateResult gate = fixture.gates().evaluate(run.getValidationRunId());
		assertEquals(QualityGateDecision.PASS, gate.getDecision());
		assertTrue(gate.getReasons().stream().noneMatch(r -> "SECRET_DETECTED".equals(r.code())));
	}

	@Test
	void regularValidationStillScansHistoryForAudit() throws Exception {
		assumeScanners();
		initRepoWithHistoricalFixture();

		Fixture fixture = fixture();
		ValidationRun run = fixture.service().start("task-1");
		SecurityReport gitleaks = report(fixture, run, SecurityScannerType.GITLEAKS);
		assertTrue(gitleaks.getFindings().stream()
			.anyMatch(f -> f.getCategory() == SecurityCategory.SECRET));

		QualityGateResult gate = fixture.gates().evaluate(run.getValidationRunId());
		assertEquals(QualityGateDecision.BLOCK, gate.getDecision());
	}

	@Test
	void deliveryGitleaksBlocksUntrackedSecret() throws Exception {
		assumeScanners();
		initRepo("hello.txt", "hello\n");
		Files.writeString(repoDir.resolve("secret.txt"), "AWS_ACCESS_KEY_ID=" + fakeSecret() + "\n");

		Fixture fixture = fixture();
		ValidationRun run = fixture.service().startDelivery("change-delivery");
		SecurityReport gitleaks = report(fixture, run, SecurityScannerType.GITLEAKS);
		assertTrue(gitleaks.getFindings().stream()
			.anyMatch(f -> f.getCategory() == SecurityCategory.SECRET));

		QualityGateResult gate = fixture.gates().evaluate(run.getValidationRunId());
		assertEquals(QualityGateDecision.BLOCK, gate.getDecision());
		assertTrue(gate.getReasons().stream().anyMatch(r -> "SECRET_DETECTED".equals(r.code())));
	}

	@Test
	void deliveryGitleaksBlocksSecretAddedToTrackedFile() throws Exception {
		assumeScanners();
		initRepo("hello.txt", "hello\n");
		Files.writeString(repoDir.resolve("hello.txt"), "hello\nAWS_ACCESS_KEY_ID=" + fakeSecret() + "\n");

		Fixture fixture = fixture();
		ValidationRun run = fixture.service().startDelivery("change-delivery");
		SecurityReport gitleaks = report(fixture, run, SecurityScannerType.GITLEAKS);
		assertTrue(gitleaks.getFindings().stream()
			.anyMatch(f -> f.getCategory() == SecurityCategory.SECRET));

		QualityGateResult gate = fixture.gates().evaluate(run.getValidationRunId());
		assertEquals(QualityGateDecision.BLOCK, gate.getDecision());
		assertTrue(gate.getReasons().stream().anyMatch(r -> "SECRET_DETECTED".equals(r.code())));
	}

	@Test
	void deliveryGitleaksCommandAddsNoGitOnlyForDelivery() {
		RecordingExecutor executor = new RecordingExecutor();
		InMemoryValidationArtifactRepository artifacts = new InMemoryValidationArtifactRepository();
		SecurityValidationService security = new SecurityValidationService(executor,
			new SecurityScannerAvailability(executor), new SecurityFindingParser(new ObjectMapper(),
				new SecurityRedactor()), new SecurityRedactor(), new InMemorySecurityReportRepository(),
			new ValidationEvidenceService(artifacts, new ArtifactContentLimiter(1024)),
			AuditService.noop(), new ObjectMapper());
		GitleaksValidationProvider provider = new GitleaksValidationProvider(security);
		provider.execute(context(true));
		assertTrue(executor.lastDetect().contains("--no-git"));
		provider.execute(context(false));
		assertFalse(executor.lastDetect().contains("--no-git"));
	}

	private ValidationContext context(boolean delivery) {
		return new ValidationContext("run-1", "task-1", "project-1", "workspace-1", repoDir,
			ValidationCheckType.SECURITY, Map.of("securityScanner", "GITLEAKS"), delivery);
	}

	private Fixture fixture() {
		TaskCenterService tasks = mock(TaskCenterService.class);
		TaskRecord task = new TaskRecord("task-1", "name", "description", "project-1", "workspace-1");
		when(tasks.getTask("task-1")).thenReturn(Optional.of(task));
		WorkspaceService workspaces = mock(WorkspaceService.class);
		Workspace workspace = new Workspace("workspace-1", "project-1", repoDir.toString(), "main",
			WorkspaceStatus.READY, Instant.now(), Instant.now());
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		when(workspaces.checkProjectOwnership("project-1", "workspace-1")).thenReturn(true);

		ChangeService changes = mock(ChangeService.class);
		ChangeSet change = new ChangeSet("change-delivery", "task-1", "exec-ws-1", "project-1", "exec-1",
			"ai-dev-os/task-1", "diff", "stat", 1, 1, 0, 1, 0, 0, Instant.now());
		change.markReviewing(); change.markApproved("user");
		when(changes.getChange("change-delivery")).thenReturn(Optional.of(change));
		ExecutionWorkspacePromotionService execution = mock(ExecutionWorkspacePromotionService.class);
		ExecutionWorkspace executionWorkspace = new ExecutionWorkspace("exec-ws-1", "task-1", "project-1",
			"source-1", "/source", repoDir.toString(), "GIT_WORKTREE", "ai-dev-os/task-1",
			ExecutionWorkspaceStatus.COMPLETED, "base-1", Instant.now(), Instant.now());
		when(execution.findWorkspace("task-1")).thenReturn(executionWorkspace);
		when(execution.changeFingerprint("task-1")).thenReturn("fp-1");

		ObjectMapper mapper = new ObjectMapper();
		InMemoryValidationRepository runs = new InMemoryValidationRepository();
		InMemorySecurityReportRepository reports = new InMemorySecurityReportRepository();
		InMemoryValidationArtifactRepository artifacts = new InMemoryValidationArtifactRepository();
		ValidationEvidenceService evidence = new ValidationEvidenceService(artifacts,
			new ArtifactContentLimiter(1024));
		CommandExecutor executor = new CommandExecutor();
		SecurityRedactor redactor = new SecurityRedactor();
		SecurityValidationService security = new SecurityValidationService(executor,
			new SecurityScannerAvailability(executor), new SecurityFindingParser(mapper, redactor),
			redactor, reports, evidence, AuditService.noop(), mapper);
		List<ValidationProvider> providers = List.of(new GitleaksValidationProvider(security),
			new SemgrepValidationProvider(security), new TrivyValidationProvider(security));
		ValidationService service = new ValidationService(runs, tasks, workspaces,
			new ProjectCapabilityDetector(mapper), providers, evidence, AuditService.noop());
		service.setChangeService(changes);
		service.setExecutionWorkspaces(execution);
		QualityGateService gates = new QualityGateService(new InMemoryQualityGateRepository(), runs, reports,
			new QualityGatePolicy(), new InMemoryHumanApprovalRepository(), tasks, AuditService.noop(), mapper);
		return new Fixture(service, gates, reports);
	}

	private void initRepoWithHistoricalFixture() throws Exception {
		initRepo("hello.txt", "hello\n");
		Files.writeString(repoDir.resolve("secret.txt"), "AWS_ACCESS_KEY_ID=" + fakeSecret() + "\n");
		git("add", "."); git("commit", "-m", "add historical fixture");
		Files.delete(repoDir.resolve("secret.txt"));
		git("add", "-A"); git("commit", "-m", "remove fixture");
	}

	private void initRepo(String name, String content) throws Exception {
		git("init", "-b", "main"); git("config", "user.email", "security@example.test");
		git("config", "user.name", "Security E2E Fixture");
		Files.writeString(repoDir.resolve(name), content);
		git("add", "."); git("commit", "-m", "init");
	}

	private SecurityReport report(Fixture fixture, ValidationRun run, SecurityScannerType scanner) {
		return fixture.reports().findByValidationRunId(run.getValidationRunId()).stream()
			.filter(r -> r.getScanner() == scanner).findFirst().orElseThrow();
	}

	private void git(String... args) throws Exception {
		List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
		CommandOptions options = new CommandOptions(); options.setCommand(command);
		options.setWorkingDirectory(repoDir.toString());
		CommandResult result = new CommandExecutor().execute(options);
		assertTrue(result.isSuccess(), result.getError());
	}

	private static String fakeSecret() {
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
		java.security.SecureRandom random = new java.security.SecureRandom();
		StringBuilder value = new StringBuilder(16);
		for (int i = 0; i < 16; i++) {
			value.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return "AKIA" + value;
	}

	private static void assumeScanners() {
		for (String scanner : List.of("gitleaks", "semgrep", "trivy")) {
			CommandOptions options = new CommandOptions();
			options.setCommand(List.of(scanner, "--version"));
			CommandResult result = new CommandExecutor().execute(options);
			Assumptions.assumeTrue(result.isSuccess(), scanner + " is required for delivery gitleaks scope E2E");
		}
	}

	private record Fixture(ValidationService service, QualityGateService gates,
		InMemorySecurityReportRepository reports) { }

	private static class RecordingExecutor extends CommandExecutor {
		private final List<String> detect = new ArrayList<>();
		@Override public CommandResult execute(CommandOptions options) {
			List<String> command = options.getCommand();
			if (command.contains("detect")) {
				detect.clear(); detect.addAll(command);
				return result(true, "[]");
			}
			return result(true, command.isEmpty() ? "" : command.get(0) + " --version");
		}
		List<String> lastDetect() { return List.copyOf(detect); }
		private CommandResult result(boolean success, String output) {
			CommandResult value = new CommandResult();
			value.setSuccess(success); value.setOutput(output); value.setExitCode(0);
			return value;
		}
	}
}
