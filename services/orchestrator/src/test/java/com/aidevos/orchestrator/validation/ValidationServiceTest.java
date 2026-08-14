package com.aidevos.orchestrator.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.validation.provider.ValidationCheckResult;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidationServiceTest {
	@TempDir Path workspacePath;

	@Test void noApplicableProviderReturnsPassWithExplicitSkippedChecks() {
		Fixture fixture = fixture(true);
		ValidationRun run = fixture.service().start("task-1");
		assertEquals(ValidationDecision.PASS, run.getDecision());
		assertEquals(9, run.getChecks().size());
		assertTrue(run.getChecks().stream().allMatch(check -> check.getStatus() == ValidationStatus.SKIPPED));
		assertEquals(3, run.getChecks().stream()
			.filter(check -> check.getType() == ValidationCheckType.SECURITY).count());
		assertEquals(1, fixture.service().findByTask("task-1").size());
		assertEquals(run.getValidationRunId(), fixture.service().get(run.getValidationRunId()).getValidationRunId());
	}

	@Test void rejectsWorkspaceOwnershipMismatch() {
		Fixture fixture = fixture(false);
		assertThrows(IllegalArgumentException.class, () -> fixture.service().start("task-1"));
	}

	@Test void missingTaskAndInvalidRunAreNotFound() {
		Fixture fixture = fixture(true);
		assertThrows(com.aidevos.orchestrator.common.exception.ResourceNotFoundException.class,
			() -> fixture.service().start("missing"));
		assertThrows(com.aidevos.orchestrator.common.exception.ResourceNotFoundException.class,
			() -> fixture.service().get("invalid"));
	}

	@Test void engineeringConformanceEvidenceIsRecordedThroughValidationService() throws Exception {
		Files.writeString(workspacePath.resolve("project.yaml"), "project: trial");
		TaskCenterService tasks = mock(TaskCenterService.class);
		when(tasks.getTask("task-1")).thenReturn(Optional.of(new TaskRecord("task-1", "name",
			"description", "project-1", "workspace-1")));
		WorkspaceService workspaces = mock(WorkspaceService.class);
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(new Workspace("workspace-1",
			"project-1", workspacePath.toString(), "main", WorkspaceStatus.READY, Instant.now(), Instant.now())));
		when(workspaces.checkProjectOwnership("project-1", "workspace-1")).thenReturn(true);
		InMemoryValidationArtifactRepository artifacts = new InMemoryValidationArtifactRepository();
		ValidationProvider conformance = new ValidationProvider() {
			@Override public boolean supports(com.aidevos.orchestrator.validation.provider.ValidationContext context) {
				return context.type() == ValidationCheckType.CONTRACT;
			}
			@Override public ValidationCheckResult execute(com.aidevos.orchestrator.validation.provider.ValidationContext context) {
				return new ValidationCheckResult(ValidationStatus.SUCCESS, "conformance passed", null,
					"Conformance: PASS", "", List.of(), java.util.Map.of("operation", "CONFORMANCE",
						"exitCode", 0, "durationMs", 5));
			}
			@Override public String name() { return "engineering-platform-conformance"; }
		};
		ValidationService service = new ValidationService(new InMemoryValidationRepository(), tasks,
			workspaces, new ProjectCapabilityDetector(new ObjectMapper()), List.of(conformance),
			new ValidationEvidenceService(artifacts, new ArtifactContentLimiter(1024)), AuditService.noop());

		ValidationRun run = service.start("task-1");
		ValidationCheck check = run.getChecks().stream()
			.filter(item -> item.getType() == ValidationCheckType.CONTRACT).findFirst().orElseThrow();
		assertEquals(ValidationStatus.SUCCESS, check.getStatus());
		assertEquals(1, check.getArtifactIds().size());
		assertEquals("Conformance: PASS", artifacts.get(check.getArtifactIds().getFirst()).getContent());
	}

	private Fixture fixture(boolean ownership) {
		TaskCenterService tasks = mock(TaskCenterService.class);
		TaskRecord task = new TaskRecord("task-1", "name", "description", "project-1", "workspace-1");
		when(tasks.getTask("task-1")).thenReturn(Optional.of(task));
		WorkspaceService workspaces = mock(WorkspaceService.class);
		Workspace workspace = new Workspace("workspace-1", "project-1", workspacePath.toString(),
			"main", WorkspaceStatus.READY, Instant.now(), Instant.now());
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		when(workspaces.checkProjectOwnership("project-1", "workspace-1")).thenReturn(ownership);
		InMemoryValidationRepository repository = new InMemoryValidationRepository();
		ValidationEvidenceService evidence = new ValidationEvidenceService(
			new InMemoryValidationArtifactRepository(), new ArtifactContentLimiter(1024));
		return new Fixture(new ValidationService(repository, tasks, workspaces,
			new ProjectCapabilityDetector(new ObjectMapper()), List.of(), evidence, AuditService.noop()));
	}

	private record Fixture(ValidationService service) { }
}
