package com.aidevos.orchestrator.validation.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.InMemoryValidationArtifactRepository;
import com.aidevos.orchestrator.validation.InMemoryValidationRepository;
import com.aidevos.orchestrator.validation.ValidationArtifact;
import com.aidevos.orchestrator.validation.ValidationCheck;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationEvidenceService;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.validation.provider.GitleaksValidationProvider;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.validation.provider.SemgrepValidationProvider;
import com.aidevos.orchestrator.validation.provider.TrivyValidationProvider;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealSecurityScannerEndToEndTest {
	private static final String FAKE_SECRET = "AKIAQYLPMN5HHDE4VP5K";
	private static final Set<EventType> REQUIRED_EVENTS = EnumSet.of(
		EventType.SECURITY_VALIDATION_STARTED, EventType.SECURITY_SCANNER_STARTED,
		EventType.SECURITY_SCANNER_COMPLETED, EventType.SECURITY_REPORT_CREATED,
		EventType.SECURITY_VALIDATION_COMPLETED);

	@TempDir Path tempDir;

	@Test
	@Timeout(value = 25, unit = java.util.concurrent.TimeUnit.MINUTES)
	void realScannersUseProductionChainWithoutChangingWorkspaceOrLeakingSecret() throws Exception {
		for (String scanner : List.of("gitleaks", "semgrep", "trivy")) {
			Assumptions.assumeTrue(command(tempDir, List.of(scanner, "--version")).isSuccess(),
				scanner + " is required for real scanner E2E");
		}
		Fixture fixture = fixture();
		GitState before = gitState(fixture.workspace());

		ValidationRun run = fixture.service().start(fixture.taskId());

		assertEquals(before, gitState(fixture.workspace()), "security scanners must be read-only");
		assertEquals(3, securityChecks(run).size());
		assertTrue(securityChecks(run).stream().allMatch(c -> c.getStatus() == ValidationStatus.SUCCESS),
			diagnostics(run));
		assertTrue(securityChecks(run).stream().allMatch(c -> "AVAILABLE".equals(c.getMetadata().get("availability"))));
		List<SecurityReport> reports = fixture.reports().findByValidationRunId(run.getValidationRunId());
		assertEquals(3, reports.size());

		SecurityReport gitleaks = report(reports, SecurityScannerType.GITLEAKS);
		SecurityFinding secret = gitleaks.getFindings().stream()
			.filter(f -> f.getCategory() == SecurityCategory.SECRET).findFirst()
			.orElseThrow(() -> new AssertionError("Gitleaks produced no secret finding: " + gitleaks.getSummary()));
		assertEquals(SecurityScannerType.GITLEAKS, secret.getScanner());
		assertNotNull(secret.getRuleId()); assertFalse(secret.getRuleId().isBlank());
		assertTrue(secret.getFile().contains("test-secrets.env"));
		assertEquals(1, secret.getLine()); assertNotNull(secret.getFingerprint());

		SecurityReport semgrep = report(reports, SecurityScannerType.SEMGREP);
		SecurityFinding sast = semgrep.getFindings().stream()
			.filter(f -> f.getCategory() == SecurityCategory.SAST
				&& "Dockerfile".equals(f.getFile()))
			.findFirst().orElseThrow(() -> new AssertionError("Semgrep did not detect security fixture; findings="
				+ semgrep.getFindings().stream().map(f -> f.getRuleId() + "@" + f.getFile()).toList()));
		assertEquals(SecurityScannerType.SEMGREP, sast.getScanner());
		assertNotNull(sast.getSeverity()); assertNotNull(sast.getRuleId());
		assertEquals("Dockerfile", sast.getFile());
		assertNotNull(sast.getLine()); assertNotNull(sast.getMessage()); assertNotNull(sast.getFingerprint());

		SecurityReport trivy = report(reports, SecurityScannerType.TRIVY);
		SecurityFinding trivyFinding = trivy.getFindings().stream()
			.filter(f -> f.getCategory() == SecurityCategory.DEPENDENCY
				|| f.getCategory() == SecurityCategory.CONFIGURATION
				|| f.getCategory() == SecurityCategory.IAC).findFirst().orElseThrow();
		assertEquals(SecurityScannerType.TRIVY, trivyFinding.getScanner());
		assertNotNull(trivyFinding.getRuleId()); assertNotNull(trivyFinding.getSeverity());
		assertNotNull(trivyFinding.getFile()); assertNotNull(trivyFinding.getFingerprint());
		if (trivyFinding.getCategory() == SecurityCategory.DEPENDENCY) {
			assertNotNull(trivyFinding.getVulnerabilityId()); assertNotNull(trivyFinding.getPackageName());
			assertNotNull(trivyFinding.getInstalledVersion());
		}

		ObjectMapper mapper = new ObjectMapper();
		assertFalse(mapper.writeValueAsString(run).contains(FAKE_SECRET));
		assertFalse(mapper.writeValueAsString(reports).contains(FAKE_SECRET));
		for (SecurityReport report : reports) {
			assertEquals(2, report.getArtifactIds().size(), report.getScanner().name());
			for (String artifactId : report.getArtifactIds()) {
				ValidationArtifact artifact = fixture.artifacts().get(artifactId);
				assertNotNull(artifact); assertEquals(fixture.taskId(), artifact.getTaskId());
				assertEquals(run.getValidationRunId(), artifact.getValidationRunId());
				assertEquals(report.getReportId(), artifact.getMetadata().get("securityReportId"));
				assertEquals(report.getScanner().name(), artifact.getMetadata().get("scanner"));
				assertFalse(String.valueOf(artifact.getContent()).contains(FAKE_SECRET));
			}
		}

		List<EventRecord> events = fixture.audit().query(EventQuery.all());
		assertTrue(events.stream().map(EventRecord::type).collect(java.util.stream.Collectors.toSet())
			.containsAll(REQUIRED_EVENTS));
		assertTrue(events.stream().filter(e -> REQUIRED_EVENTS.contains(e.type()))
			.allMatch(e -> fixture.taskId().equals(e.taskId())
				&& run.getValidationRunId().equals(e.aggregateId())));
		assertEquals(3, events.stream().filter(e -> e.type() == EventType.SECURITY_SCANNER_STARTED).count());
		assertEquals(3, events.stream().filter(e -> e.type() == EventType.SECURITY_SCANNER_COMPLETED).count());
		assertEquals(3, events.stream().filter(e -> e.type() == EventType.SECURITY_REPORT_CREATED).count());
		assertFalse(mapper.writeValueAsString(events).contains(FAKE_SECRET));
		System.out.printf("REAL_SECURITY_E2E GITLEAKS=%d SEMGREP=%d TRIVY=%d TRIVY_SAMPLE=%s:%s GIT_UNCHANGED=true ARTIFACTS=%d AUDIT_EVENTS=%d%n",
			gitleaks.getFindings().size(), semgrep.getFindings().size(), trivy.getFindings().size(),
			trivyFinding.getCategory(), trivyFinding.getRuleId(),
			reports.stream().mapToInt(r -> r.getArtifactIds().size()).sum(), events.size());
	}

	private Fixture fixture() throws Exception {
		Path root = Files.createDirectories(tempDir.resolve("security-real-e2e"));
		Files.createDirectories(root.resolve("src/main/java/fixture"));
		Files.writeString(root.resolve("test-secrets.env"), "AWS_ACCESS_KEY_ID=" + FAKE_SECRET + "\n");
		Files.writeString(root.resolve("src/main/java/fixture/CommandController.java"), """
			package fixture;
			import java.io.IOException;
			class CommandController {
			  void run(String userInput) throws IOException {
			    Runtime.getRuntime().exec(userInput);
			  }
			}
			""");
		Files.writeString(root.resolve("unsafe.js"), "function run(userInput) { return eval(userInput); }\n");
		Files.writeString(root.resolve("Dockerfile"), "FROM ubuntu:18.04\nUSER root\n");
		Files.writeString(root.resolve("package.json"), "{\"name\":\"security-e2e\",\"version\":\"1.0.0\",\"dependencies\":{\"lodash\":\"4.17.19\"}}\n");
		Files.writeString(root.resolve("package-lock.json"), """
			{"name":"security-e2e","version":"1.0.0","lockfileVersion":2,"requires":true,
			"packages":{"":{"name":"security-e2e","version":"1.0.0","dependencies":{"lodash":"4.17.19"}},
			"node_modules/lodash":{"version":"4.17.19","resolved":"https://registry.npmjs.org/lodash/-/lodash-4.17.19.tgz"}},
			"dependencies":{"lodash":{"version":"4.17.19","resolved":"https://registry.npmjs.org/lodash/-/lodash-4.17.19.tgz"}}}
			""");
		git(root, "init", "-b", "main"); git(root, "config", "user.email", "security@example.test");
		git(root, "config", "user.name", "Security E2E Fixture"); git(root, "add", ".");
		git(root, "commit", "-m", "security fixture");

		String taskId = "task-security-real-e2e";
		TaskRecord task = new TaskRecord(taskId, "Security scanner E2E", "Real scanners", "project-security", "workspace-security");
		TaskCenterService tasks = mock(TaskCenterService.class); when(tasks.getTask(taskId)).thenReturn(Optional.of(task));
		Workspace workspace = new Workspace("workspace-security", "project-security", root.toString(), "main",
			WorkspaceStatus.READY, Instant.now(), Instant.now());
		WorkspaceService workspaces = mock(WorkspaceService.class);
		when(workspaces.getWorkspace("workspace-security")).thenReturn(Optional.of(workspace));
		when(workspaces.checkProjectOwnership("project-security", "workspace-security")).thenReturn(true);

		CommandExecutor executor = new CommandExecutor(); ObjectMapper mapper = new ObjectMapper();
		InMemoryValidationArtifactRepository artifacts = new InMemoryValidationArtifactRepository();
		ValidationEvidenceService evidence = new ValidationEvidenceService(artifacts, new ArtifactContentLimiter(1024 * 1024));
		InMemorySecurityReportRepository reports = new InMemorySecurityReportRepository();
		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository(); AuditService audit = new AuditService(auditRepository);
		SecurityRedactor redactor = new SecurityRedactor();
		SecurityValidationService security = new SecurityValidationService(executor,
			new SecurityScannerAvailability(executor), new SecurityFindingParser(mapper, redactor), redactor,
			reports, evidence, audit, mapper);
		List<ValidationProvider> providers = List.of(new GitleaksValidationProvider(security),
			new SemgrepValidationProvider(security), new TrivyValidationProvider(security));
		ValidationService service = new ValidationService(new InMemoryValidationRepository(), tasks, workspaces,
			new ProjectCapabilityDetector(mapper), providers, evidence, audit);
		return new Fixture(root, taskId, service, reports, artifacts, auditRepository);
	}

	private List<ValidationCheck> securityChecks(ValidationRun run) {
		return run.getChecks().stream().filter(c -> c.getType() == ValidationCheckType.SECURITY).toList();
	}
	private SecurityReport report(List<SecurityReport> reports, SecurityScannerType scanner) {
		return reports.stream().filter(r -> r.getScanner() == scanner).findFirst().orElseThrow();
	}
	private String diagnostics(ValidationRun run) {
		return securityChecks(run).stream().map(c -> c.getName() + "=" + c.getStatus() + ":" + c.getErrorMessage()).toList().toString();
	}
	private GitState gitState(Path root) {
		return new GitState(output(root, "git", "rev-parse", "HEAD"), output(root, "git", "status", "--porcelain"),
			output(root, "git", "diff", "--binary"));
	}
	private void git(Path root, String... args) {
		List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
		CommandResult result = command(root, command); assertTrue(result.isSuccess(), result.getError());
	}
	private String output(Path root, String... args) {
		CommandResult result = command(root, List.of(args)); assertTrue(result.isSuccess(), result.getError());
		return result.getOutput().strip();
	}
	private CommandResult command(Path root, List<String> command) {
		CommandOptions options = new CommandOptions(); options.setCommand(command);
		options.setWorkingDirectory(root.toString()); options.setTimeout(Duration.ofMinutes(20));
		return new CommandExecutor().execute(options);
	}
	private record Fixture(Path workspace, String taskId, ValidationService service,
		InMemorySecurityReportRepository reports, InMemoryValidationArtifactRepository artifacts,
		InMemoryAuditRepository audit) { }
	private record GitState(String head, String status, String diff) { }
}
