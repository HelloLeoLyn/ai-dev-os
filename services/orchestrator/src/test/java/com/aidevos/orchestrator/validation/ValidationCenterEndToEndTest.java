package com.aidevos.orchestrator.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.ci.CiRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.provider.CiValidationProvider;
import com.aidevos.orchestrator.validation.provider.ExistingE2EValidationProvider;
import com.aidevos.orchestrator.validation.provider.FrontendValidationProvider;
import com.aidevos.orchestrator.validation.provider.MavenValidationProvider;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidationCenterEndToEndTest {
	@TempDir Path tempDir;

	@Test
	void realMavenAndFrontendChecksPassWithoutChangingGit() throws Exception {
		Fixture fixture = fixture("pass", false);
		GitState before = gitState(fixture.path());

		ValidationRun run = fixture.service().start(fixture.taskId());

		assertEquals(ValidationDecision.PASS, run.getDecision(), diagnostics(run));
		assertEquals(ValidationStatus.SUCCESS, check(run, ValidationCheckType.BACKEND_TEST).getStatus());
		assertEquals(ValidationStatus.SUCCESS, check(run, ValidationCheckType.BACKEND_BUILD).getStatus());
		assertEquals(ValidationStatus.SUCCESS, check(run, ValidationCheckType.FRONTEND_TEST).getStatus());
		assertEquals(ValidationStatus.SUCCESS, check(run, ValidationCheckType.FRONTEND_BUILD).getStatus());
		assertFalse(check(run, ValidationCheckType.BACKEND_TEST).getArtifactIds().isEmpty());
		assertEquals(before, gitState(fixture.path()));
	}

	@Test
	void realTestFailureProducesFailureErrorAndArtifactWithoutChangingGit() throws Exception {
		Fixture fixture = fixture("fail", true);
		GitState before = gitState(fixture.path());

		ValidationRun run = fixture.service().start(fixture.taskId());
		ValidationCheck failed = check(run, ValidationCheckType.BACKEND_TEST);

		assertEquals(ValidationDecision.FAIL, run.getDecision());
		assertEquals(ValidationStatus.FAILED, failed.getStatus());
		assertNotNull(failed.getErrorMessage());
		assertFalse(failed.getErrorMessage().isBlank());
		assertFalse(failed.getArtifactIds().isEmpty());
		assertNotNull(fixture.artifacts().get(failed.getArtifactIds().getFirst()));
		assertEquals(before, gitState(fixture.path()));
	}

	private Fixture fixture(String name, boolean fail) throws Exception {
		Path root = Files.createDirectories(tempDir.resolve(name));
		Files.createDirectories(root.resolve("backend/src/test/java/fixture"));
		Files.createDirectories(root.resolve("frontend"));
		Files.writeString(root.resolve(".gitignore"), "**/target/\n**/node_modules/\n");
		Files.writeString(root.resolve("backend/pom.xml"), pom());
		Files.writeString(root.resolve("backend/src/test/java/fixture/FixtureTest.java"), test(fail));
		Files.writeString(root.resolve("frontend/package.json"), packageJson());
		Files.writeString(root.resolve("frontend/pnpm-lock.yaml"), """
			lockfileVersion: '9.0'

			settings:
			  autoInstallPeers: true
			  excludeLinksFromLockfile: false

			importers:

			  .: {}
			""");
		Files.writeString(root.resolve("frontend/test.cjs"),
			"const test=require('node:test');const assert=require('node:assert');test('ok',()=>assert.equal(2+2,4));\n");
		git(root, "init", "-b", "main");
		git(root, "config", "user.email", "validation@example.test");
		git(root, "config", "user.name", "Validation Fixture");
		git(root, "add", ".");
		git(root, "commit", "-m", "fixture");

		String taskId = "task-" + name;
		TaskRecord task = new TaskRecord(taskId, "Validation fixture", "E2E", "project-1", "workspace-1");
		TaskCenterService tasks = mock(TaskCenterService.class);
		when(tasks.getTask(taskId)).thenReturn(Optional.of(task));
		Workspace workspace = new Workspace("workspace-1", "project-1", root.toString(), "main",
			WorkspaceStatus.READY, Instant.now(), Instant.now());
		WorkspaceService workspaces = mock(WorkspaceService.class);
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		when(workspaces.checkProjectOwnership("project-1", "workspace-1")).thenReturn(true);
		CommandExecutor executor = new CommandExecutor();
		CiRepository ci = mock(CiRepository.class);
		when(ci.getByTaskId(taskId)).thenReturn(List.of());
		List<ValidationProvider> providers = List.of(new MavenValidationProvider(executor),
			new FrontendValidationProvider(executor), new ExistingE2EValidationProvider(executor),
			new CiValidationProvider(ci));
		InMemoryValidationRepository repository = new InMemoryValidationRepository();
		InMemoryValidationArtifactRepository artifacts = new InMemoryValidationArtifactRepository();
		ValidationEvidenceService evidence = new ValidationEvidenceService(artifacts,
			new ArtifactContentLimiter(64 * 1024));
		ValidationService service = new ValidationService(repository, tasks, workspaces,
			new ProjectCapabilityDetector(new ObjectMapper()), providers, evidence, AuditService.noop());
		return new Fixture(root, taskId, service, artifacts);
	}

	private ValidationCheck check(ValidationRun run, ValidationCheckType type) {
		return run.getChecks().stream().filter(value -> value.getType() == type).findFirst().orElseThrow();
	}

	private String diagnostics(ValidationRun run) {
		return run.getChecks().stream().map(value -> value.getType() + "=" + value.getStatus()
			+ " (" + value.getErrorMessage() + ")").toList().toString();
	}

	private GitState gitState(Path root) {
		return new GitState(output(root, "git", "rev-parse", "HEAD"),
			output(root, "git", "status", "--porcelain"), output(root, "git", "diff"));
	}

	private void git(Path root, String... args) {
		List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
		CommandResult result = execute(root, command);
		assertTrue(result.isSuccess(), result.getError() + result.getOutput());
	}

	private String output(Path root, String... command) {
		CommandResult result = execute(root, List.of(command));
		assertTrue(result.isSuccess(), result.getError());
		return result.getOutput().strip();
	}

	private CommandResult execute(Path root, List<String> command) {
		CommandOptions options = new CommandOptions(); options.setCommand(command);
		options.setWorkingDirectory(root.toString());
		return new CommandExecutor().execute(options);
	}

	private String pom() {
		return """
			<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
			<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.0.0</version></parent>
			<groupId>fixture</groupId><artifactId>backend</artifactId><version>1</version>
			<properties><maven.compiler.release>21</maven.compiler.release></properties>
			<dependencies><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency></dependencies>
			</project>
			""";
	}

	private String test(boolean fail) {
		return "package fixture; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; class FixtureTest { @Test void validates() { assertTrue("
			+ (!fail) + ", \"fixture failure\"); } }";
	}

	private String packageJson() {
		return """
			{"scripts":{"test":"node --test test.cjs","build":"node --version"}}
			""";
	}

	private record Fixture(Path path, String taskId, ValidationService service,
			InMemoryValidationArtifactRepository artifacts) { }
	private record GitState(String head, String status, String diff) { }
}
