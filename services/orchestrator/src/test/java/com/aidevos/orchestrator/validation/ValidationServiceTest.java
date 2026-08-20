package com.aidevos.orchestrator.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.validation.provider.ValidationCheckResult;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

	@Test void deliveryValidationUsesApprovedExecutionWorkspaceLineage() {
		TaskCenterService tasks = mock(TaskCenterService.class);
		WorkspaceService sources = mock(WorkspaceService.class);
		ChangeService changes = mock(ChangeService.class);
		ExecutionWorkspacePromotionService execution = mock(ExecutionWorkspacePromotionService.class);
		ChangeSet change = new ChangeSet("change-delivery", "task-1", "exec-ws-1", "project-1", "exec-1",
			"ai-dev-os/task-1", "diff", "stat", 1, 1, 0, 1, 0, 0, Instant.now());
		change.markReviewing(); change.markApproved("user");
		when(changes.getChange("change-delivery")).thenReturn(Optional.of(change));
		ExecutionWorkspace workspace = new ExecutionWorkspace("exec-ws-1", "task-1", "project-1", "source-1",
			"/source", workspacePath.toString(), "GIT_WORKTREE", "ai-dev-os/task-1",
			ExecutionWorkspaceStatus.COMPLETED, "base-1", Instant.now(), Instant.now());
		when(execution.findWorkspace("task-1")).thenReturn(workspace);
		when(execution.changeFingerprint("task-1")).thenReturn("fp-1");
		ValidationService service = fixture(true).service();
		service.setChangeService(changes); service.setExecutionWorkspaces(execution);

		ValidationRun run = service.startDelivery("change-delivery");
		assertTrue(run.isDelivery());
		assertEquals("exec-ws-1", run.getExecutionWorkspaceId());
		assertEquals("change-delivery", run.getChangeSetId());
		assertEquals("fp-1", run.getValidatedChangeFingerprint());
		assertEquals(ValidationDecision.PASS, run.getDecision());
	}

	@Test void deliveryValidationReusesUnchangedSuccessAndRefreshesOnFingerprintChange() {
		TaskCenterService tasks = mock(TaskCenterService.class);
		WorkspaceService sources = mock(WorkspaceService.class);
		ChangeService changes = mock(ChangeService.class);
		ExecutionWorkspacePromotionService execution = mock(ExecutionWorkspacePromotionService.class);
		ChangeSet change = new ChangeSet("change-delivery", "task-1", "exec-ws-1", "project-1", "exec-1",
			"ai-dev-os/task-1", "diff", "stat", 1, 1, 0, 1, 0, 0, Instant.now());
		change.markReviewing(); change.markApproved("user");
		when(changes.getChange("change-delivery")).thenReturn(Optional.of(change));
		ExecutionWorkspace workspace = new ExecutionWorkspace("exec-ws-1", "task-1", "project-1", "source-1",
			"/source", workspacePath.toString(), "GIT_WORKTREE", "ai-dev-os/task-1",
			ExecutionWorkspaceStatus.COMPLETED, "base-1", Instant.now(), Instant.now());
		when(execution.findWorkspace("task-1")).thenReturn(workspace);
		when(execution.changeFingerprint("task-1")).thenReturn("fp-1");
		InMemoryValidationRepository repository = new InMemoryValidationRepository();
		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
		ValidationService service = new ValidationService(repository, tasks, sources,
			new ProjectCapabilityDetector(new ObjectMapper()), List.of(),
			new ValidationEvidenceService(new InMemoryValidationArtifactRepository(),
				new ArtifactContentLimiter(1024)), new AuditService(auditRepository));
		service.setChangeService(changes); service.setExecutionWorkspaces(execution);

		ValidationRun previous = new ValidationRun("validation-previous", "task-1", "project-1",
			"workspace-1", null, "exec-1");
		previous.setDelivery(true);
		previous.setChangeSetId("change-delivery");
		previous.setExecutionWorkspaceId("exec-ws-1");
		previous.setValidatedChangeFingerprint("fp-1");
		previous.setStatus(ValidationStatus.SUCCESS);
		previous.setStartedAt(Instant.now());
		repository.save(previous);

		// Unchanged fingerprint reuses the previous SUCCESS run without re-running checks.
		ValidationRun reused = service.startDelivery("change-delivery");
		assertSame(previous, reused);
		assertEquals(1, repository.findByTaskId("task-1").size());
		assertTrue(events(auditRepository).stream()
			.anyMatch(event -> event.type() == EventType.VALIDATION_REUSED));

		// A changed fingerprint invalidates the cache and forces a fresh run.
		when(execution.changeFingerprint("task-1")).thenReturn("fp-2");
		ValidationRun refreshed = service.startDelivery("change-delivery");
		assertNotEquals(previous.getValidationRunId(), refreshed.getValidationRunId());
		assertEquals("fp-2", refreshed.getValidatedChangeFingerprint());
		assertEquals(2, repository.findByTaskId("task-1").size());
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

	private List<EventRecord> events(InMemoryAuditRepository auditRepository) {
		return auditRepository.query(EventQuery.all());
	}
}
