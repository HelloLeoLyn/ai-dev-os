package com.aidevos.orchestrator.backlog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectTaskService;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BacklogServiceTest {
	private InMemoryBacklogRepository repository;
	private ProjectService projects;
	private WorkspaceService workspaces;
	private ProjectTaskService projectTasks;
	private TaskCenterService taskCenter;
	private InMemoryAuditRepository auditRepository;
	private BacklogService service;
	private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

	@BeforeEach void setUp() {
		repository = new InMemoryBacklogRepository();
		projects = mock(ProjectService.class);
		workspaces = mock(WorkspaceService.class);
		projectTasks = mock(ProjectTaskService.class);
		taskCenter = mock(TaskCenterService.class);
		auditRepository = new InMemoryAuditRepository();
		service = new BacklogService(repository, projects, workspaces, projectTasks, taskCenter,
			new AuditService(auditRepository), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test void createsUpdatesAndDoesNotAuditNoopUpdate() {
		BacklogItem item = service.create(create("Future work", BacklogStatus.IDEA, List.of()));
		assertEquals(BacklogStatus.IDEA, item.getStatus());
		UpdateBacklogRequest update = new UpdateBacklogRequest("Future work", null,
			BacklogPriority.HIGH, null, null, BacklogSourceType.LESSON, "LESSON-1", List.of(), List.of("security"));
		service.update(item.getBacklogItemId(), update);
		service.update(item.getBacklogItemId(), update);
		assertEquals(BacklogPriority.HIGH, item.getPriority());
		assertEquals(2, events().size());
		assertEquals(List.of(EventType.BACKLOG_CREATED, EventType.BACKLOG_UPDATED),
			events().stream().map(EventRecord::type).toList());
	}

	@Test void enforcesStateMachineAndBlockedReason() {
		BacklogItem item = service.create(create("Blocked", BacklogStatus.PLANNED, List.of()));
		assertThrows(IllegalArgumentException.class, () -> service.changeStatus(item.getBacklogItemId(),
			new ChangeBacklogStatusRequest(BacklogStatus.BLOCKED, " ")));
		service.changeStatus(item.getBacklogItemId(), new ChangeBacklogStatusRequest(BacklogStatus.BLOCKED, "Waiting for access"));
		assertEquals("Waiting for access", item.getBlockedReason());
		service.changeStatus(item.getBacklogItemId(), new ChangeBacklogStatusRequest(BacklogStatus.BLOCKED, "Waiting for decision"));
		assertEquals("Waiting for decision", item.getBlockedReason());
		service.changeStatus(item.getBacklogItemId(), new ChangeBacklogStatusRequest(BacklogStatus.PLANNED, null));
		assertNull(item.getBlockedReason());
		assertThrows(IllegalArgumentException.class, () -> service.changeStatus(item.getBacklogItemId(),
			new ChangeBacklogStatusRequest(BacklogStatus.DONE, null)));
	}

	@Test void validatesDependenciesDeduplicatesAndRejectsCycles() {
		BacklogItem first = service.create(create("First", BacklogStatus.IDEA, List.of()));
		BacklogItem second = service.create(create("Second", BacklogStatus.IDEA,
			List.of(first.getBacklogItemId(), first.getBacklogItemId())));
		assertEquals(List.of(first.getBacklogItemId()), second.getDependsOn());
		assertThrows(IllegalArgumentException.class, () -> service.update(first.getBacklogItemId(),
			new UpdateBacklogRequest("First", null, BacklogPriority.MEDIUM, null, null,
				BacklogSourceType.MANUAL, null, List.of(second.getBacklogItemId()), List.of())));
		service.changeStatus(second.getBacklogItemId(), new ChangeBacklogStatusRequest(BacklogStatus.PLANNED, null));
		assertThrows(IllegalArgumentException.class, () -> service.changeStatus(second.getBacklogItemId(),
			new ChangeBacklogStatusRequest(BacklogStatus.READY, null)));
	}

	@Test void readyRequiresDoneDependencies() {
		BacklogItem dependency = service.create(create("Dependency", BacklogStatus.IDEA, List.of()));
		BacklogItem item = service.create(create("Dependent", BacklogStatus.PLANNED, List.of(dependency.getBacklogItemId())));
		assertThrows(IllegalArgumentException.class, () -> service.changeStatus(item.getBacklogItemId(),
			new ChangeBacklogStatusRequest(BacklogStatus.READY, null)));
		dependency.changeStatus(BacklogStatus.DONE, null, NOW); repository.save(dependency);
		assertEquals(BacklogStatus.READY, service.changeStatus(item.getBacklogItemId(),
			new ChangeBacklogStatusRequest(BacklogStatus.READY, null)).getStatus());
	}

	@Test void validatesProjectWorkspaceOwnership() {
		Project project = mock(Project.class);
		when(projects.getProject("project-1")).thenReturn(Optional.of(project));
		Workspace workspace = new Workspace("workspace-1", "project-other", "/tmp/work", "main",
			WorkspaceStatus.READY, NOW, NOW);
		when(workspaces.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		CreateBacklogRequest request = new CreateBacklogRequest("Owned", null, BacklogStatus.IDEA,
			BacklogPriority.MEDIUM, "project-1", "workspace-1", BacklogSourceType.MANUAL,
			null, null, List.of(), List.of());
		assertThrows(IllegalArgumentException.class, () -> service.create(request));
	}

	@Test void convertsThroughProjectTaskServiceAndIsIdempotent() {
		BacklogItem item = service.create(create("Convert", BacklogStatus.READY, List.of()));
		TaskRecord task = new TaskRecord("task-1", "Convert", null, "project-1", "workspace-1", ExecutionMode.READ_ONLY);
		task.markPlanning("approval-1");
		when(projectTasks.createTask(eq("project-1"), any(), eq(item.getBacklogItemId()))).thenReturn(task);
		when(taskCenter.getTask("task-1")).thenReturn(Optional.of(task));
		ConvertBacklogToTaskRequest request = new ConvertBacklogToTaskRequest("Implement", "hermes",
			"project-1", "workspace-1", ExecutionMode.READ_ONLY);
		BacklogConversionResult first = service.convertToTask(item.getBacklogItemId(), request);
		BacklogConversionResult duplicate = service.convertToTask(item.getBacklogItemId(), request);
		assertEquals("task-1", first.task().getTaskId());
		assertEquals("task-1", duplicate.task().getTaskId());
		assertEquals(BacklogStatus.CONVERTED, item.getStatus());
		assertEquals(TaskStatus.PLANNING, task.getStatus());
		verify(projectTasks, times(1)).createTask(eq("project-1"), any(), eq(item.getBacklogItemId()));
		EventRecord converted = events().stream().filter(event -> event.type() == EventType.BACKLOG_CONVERTED_TO_TASK).findFirst().orElseThrow();
		assertEquals("task-1", converted.taskId()); assertEquals("backlog-item", converted.aggregateType());
	}

	@Test void linkedSuccessfulTaskMarksBacklogDoneButFailureDoesNot() {
		BacklogItem item = service.create(create("Convert", BacklogStatus.READY, List.of()));
		TaskRecord task = new TaskRecord("task-1", "Convert", null, "project-1", "workspace-1");
		when(projectTasks.createTask(eq("project-1"), any(), eq(item.getBacklogItemId()))).thenReturn(task);
		when(taskCenter.getTask("task-1")).thenReturn(Optional.of(task));
		service.convertToTask(item.getBacklogItemId(), new ConvertBacklogToTaskRequest("Goal", "hermes", "project-1", "workspace-1", ExecutionMode.READ_ONLY));
		task.markFailed("failed"); assertEquals(BacklogStatus.CONVERTED, service.get(item.getBacklogItemId()).getStatus());
		task.markSuccess(); assertEquals(BacklogStatus.DONE, service.get(item.getBacklogItemId()).getStatus());
		assertNotNull(item.getCompletedAt());
	}

	@Test void recommendationContextSurvivesOrdinaryUpdateAndManualItemsHaveNone() {
		BacklogItem manual = service.create(create("Manual", BacklogStatus.IDEA, List.of()));
		assertNull(manual.getRecommendationContext());
		BacklogRecommendationContext context = new BacklogRecommendationContext("r1", "a1", "t1",
			"Goal", List.of("Done"), com.aidevos.orchestrator.analysis.AnalysisEnums.Level.HIGH,
			List.of("src"), ExecutionMode.READ_WRITE, false);
		BacklogItem candidate = service.createRecommendationCandidate("backlog-rec",
			create("Recommendation", BacklogStatus.IDEA, List.of()), context);
		service.update(candidate.getBacklogItemId(), new UpdateBacklogRequest("Edited", "Safe edit",
			BacklogPriority.HIGH, null, null, BacklogSourceType.TASK, "forged-looking-reference",
			List.of(), List.of()));
		assertSame(context, candidate.getRecommendationContext());
		assertEquals("r1", candidate.getRecommendationContext().recommendationId());
	}

	private CreateBacklogRequest create(String title, BacklogStatus status, List<String> dependencies) {
		return new CreateBacklogRequest(title, null, status, BacklogPriority.MEDIUM, null, null,
			BacklogSourceType.MANUAL, null, status == BacklogStatus.BLOCKED ? "blocked" : null,
			dependencies, List.of());
	}
	private List<EventRecord> events() { return auditRepository.query(EventQuery.all()); }
}
