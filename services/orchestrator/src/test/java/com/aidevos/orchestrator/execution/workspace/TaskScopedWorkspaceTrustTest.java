package com.aidevos.orchestrator.execution.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitResult;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.project.CreateProjectRequest;
import com.aidevos.orchestrator.project.InMemoryProjectRepository;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.InMemoryTaskRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskScopedWorkspaceTrustTest {

	@TempDir
	Path tempDir;

	private ProjectService projectService;
	private WorkspaceService workspaceService;
	private TaskCenterService taskCenterService;
	private CodingWorkspaceProperties properties;

	@BeforeEach
	void setUp() throws Exception {
		GitCommandExecutor git = mock(GitCommandExecutor.class);
		when(git.status(anyString())).thenReturn(new GitStatus("main", 0, 0, 0));
		projectService = new ProjectService(new InMemoryProjectRepository(), AuditService.noop(), git);
		workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(), git);
		taskCenterService = new TaskCenterService(mock(PlannerService.class),
			mock(PlanApprovalService.class), mock(PlanRunRepository.class), null,
			AuditService.noop(), new InMemoryTaskRepository());
		Path staticRoot = Files.createDirectory(tempDir.resolve("static-root"));
		properties = new CodingWorkspaceProperties();
		properties.setAllowedRoots(List.of(staticRoot.toString()));
	}

	@Test
	void allowsRegisteredTaskWorkspaceOutsideStaticRoots() throws Exception {
		Fixture fixture = fixture("outside-static");

		WorkspaceSnapshot snapshot = resolver().resolve(context(fixture.task(),
			fixture.workspace(), fixture.path().toString()));

		assertEquals(fixture.path().toRealPath().toString(), snapshot.path());
	}

	@Test
	void rejectsUnregisteredTaskWorkspace() throws Exception {
		Fixture fixture = fixture("registered");
		TaskRecord unregistered = registerTask(fixture.project().getProjectId(), "workspace-missing");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
			() -> resolver().resolve(context(unregistered,
				new Workspace("workspace-missing", fixture.project().getProjectId(),
					fixture.path().toString(), "main", null, Instant.now(), Instant.now()),
				fixture.path().toString())));

		assertTrue(error.getMessage().contains("Task workspace not found"));
	}

	@Test
	void rejectsWorkspaceOwnedByAnotherProject() throws Exception {
		Fixture first = fixture("first");
		Fixture second = fixture("second");
		TaskRecord mismatched = registerTask(first.project().getProjectId(),
			second.workspace().getWorkspaceId());

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
			() -> resolver().resolve(context(mismatched, second.workspace(),
				second.path().toString())));

		assertEquals("Workspace does not belong to Task project", error.getMessage());
	}

	@Test
	void rejectsExecutionPathDifferentFromWorkspaceRecord() throws Exception {
		Fixture fixture = fixture("registered");
		Path other = gitDirectory("other");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
			() -> resolver().resolve(context(fixture.task(), fixture.workspace(), other.toString())));

		assertEquals("Execution workspace path does not match registered workspace",
			error.getMessage());
	}

	@Test
	void rejectsDotDotEscape() throws Exception {
		Fixture fixture = fixture("registered");
		Files.createDirectory(fixture.path().resolve("child"));
		Path outside = gitDirectory("escaped");
		String escaped = fixture.path().resolve("child/../../escaped").toString();

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
			() -> resolver().resolve(context(fixture.task(), fixture.workspace(), escaped)));

		assertEquals("Execution workspace path does not match registered workspace",
			error.getMessage());
		assertEquals(outside.toRealPath(), Path.of(escaped).toRealPath());
	}

	@Test
	void rejectsSymlinkToOutsideWorkspace() throws Exception {
		Fixture fixture = fixture("registered");
		Path outside = gitDirectory("symlink-target");
		Path link = tempDir.resolve("workspace-link");
		try {
			Files.createSymbolicLink(link, outside);
		}
		catch (UnsupportedOperationException | java.io.IOException exception) {
			assumeTrue(false, "Symbolic links are unavailable");
		}

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
			() -> resolver().resolve(context(fixture.task(), fixture.workspace(), link.toString())));

		assertEquals("Execution workspace path does not match registered workspace",
			error.getMessage());
	}

	@Test
	void registeredReadOnlyWorkspaceRemainsNonWritable() throws Exception {
		Fixture fixture = fixture("read-only");
		Path source = fixture.path().resolve("source.txt");
		Files.writeString(source, "readable\n");
		Files.setPosixFilePermissions(source, EnumSet.of(PosixFilePermission.OWNER_READ));
		ExecutionContext context = context(fixture.task(), fixture.workspace(),
			fixture.path().toString());
		context.getParameters().put("executionMode", ExecutionMode.READ_ONLY.name());

		WorkspaceSnapshot snapshot = resolver().resolve(context);

		assertEquals("readable\n", Files.readString(Path.of(snapshot.path()).resolve("source.txt")));
		assertThrows(java.io.IOException.class,
			() -> Files.writeString(Path.of(snapshot.path()).resolve("source.txt"), "changed\n"));
		assertEquals("readable\n", Files.readString(source));
	}

	private Fixture fixture(String name) throws Exception {
		Path path = gitDirectory(name);
		Project project = projectService.createProject(new CreateProjectRequest(name, path.toString(), "test"));
		Workspace workspace = workspaceService.createWorkspace(project.getProjectId(), path.toString());
		TaskRecord task = registerTask(project.getProjectId(), workspace.getWorkspaceId());
		return new Fixture(project, workspace, task, path);
	}

	private TaskRecord registerTask(String projectId, String workspaceId) {
		TaskRecord task = new TaskRecord("task-" + projectId + "-" + workspaceId,
			"Analyze", "Read only", projectId, workspaceId, ExecutionMode.READ_ONLY);
		return taskCenterService.registerTask(task);
	}

	private Path gitDirectory(String name) throws Exception {
		Path path = Files.createDirectory(tempDir.resolve(name));
		Files.createDirectory(path.resolve(".git"));
		return path;
	}

	private WorkspaceResolver resolver() {
		GitExecutor gitExecutor = mock(GitExecutor.class);
		GitResult result = new GitResult();
		result.setSuccess(true);
		result.setOutput("true\n");
		when(gitExecutor.isRepository(anyString())).thenReturn(result);
		TaskWorkspaceTrustService trust = new TaskWorkspaceTrustService(taskCenterService,
			projectService, workspaceService);
		return new WorkspaceResolver(properties, gitExecutor, trust);
	}

	private ExecutionContext context(TaskRecord task, Workspace workspace, String path) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId(task.getTaskId());
		context.setProjectId(task.getProjectId());
		context.setWorkspace(path);
		context.getMetadata().put("workspaceId", workspace.getWorkspaceId());
		context.getParameters().put("executionMode", task.getExecutionMode().name());
		return context;
	}

	private record Fixture(Project project, Workspace workspace, TaskRecord task, Path path) { }
}
