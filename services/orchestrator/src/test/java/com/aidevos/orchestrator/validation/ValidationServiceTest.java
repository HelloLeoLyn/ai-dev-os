package com.aidevos.orchestrator.validation;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
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
		assertEquals(6, run.getChecks().size());
		assertTrue(run.getChecks().stream().allMatch(check -> check.getStatus() == ValidationStatus.SKIPPED));
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
