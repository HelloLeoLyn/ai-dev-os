package com.aidevos.orchestrator.persistence.postgresql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionReport;
import com.aidevos.orchestrator.feedback.FeedbackStatus;
import com.aidevos.orchestrator.feedback.PrFeedbackRecord;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.observability.TraceStatus;
import com.aidevos.orchestrator.observability.usage.UsageRecord;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.project.CreateProjectRequest;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.project.ProjectStatus;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairStatus;
import com.aidevos.orchestrator.repair.RepairTask;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-D: verifies the PostgreSQL JDBC repositories perform full CRUD
 * against a real database (project, workspace, task and the remaining domain
 * stores), including the JSON columns and state reconstruction.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@TempDir
	Path tempDir;

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresJdbc jdbc;
	private ObjectMapper mapper;
	private javax.sql.DataSource dataSource;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		this.jdbc = new PostgresJdbc(dataSource);
		this.dataSource = dataSource;
		this.mapper = new ObjectMapper();
		this.jdbc.update("DELETE FROM workspaces");
		this.jdbc.update("DELETE FROM projects");
		this.jdbc.update("DELETE FROM tasks");
	}

	@Test
	void projectCrud() {
		PostgresProjectRepository repository = new PostgresProjectRepository(jdbc);
		Project project = new Project("project-1", "Demo", "/work/demo", "Demo project",
			ProjectStatus.ACTIVE, NOW, NOW, "https://example.com/demo.git", "main");

		repository.save(project);
		Project loaded = repository.get("project-1");
		assertNotNull(loaded);
		assertEquals("Demo", loaded.getName());
		assertEquals("https://example.com/demo.git", loaded.getRepositoryUrl());
		assertEquals(ProjectStatus.ACTIVE, loaded.getStatus());
		assertEquals(1, repository.list().size());

		loaded.markArchived();
		repository.save(loaded);
		assertEquals(ProjectStatus.ARCHIVED, repository.get("project-1").getStatus());

		assertTrue(repository.delete("project-1"));
		assertNull(repository.get("project-1"));
	}

	@Test
	void projectAllowsMissingGitRemoteAndBranch() {
		PostgresProjectRepository repository = new PostgresProjectRepository(jdbc);
		Project project = new Project("project-local", "Local", "/work/local", null,
			ProjectStatus.ACTIVE, NOW, NOW, null, null);

		repository.save(project);
		Project loaded = repository.get("project-local");

		assertNotNull(loaded);
		assertNull(loaded.getRepositoryUrl());
		assertNull(loaded.getDefaultBranch());
	}

	@Test
	void projectServiceCreatesLocalGitProjectAndPersistsDetectedMetadata() throws Exception {
		Path repositoryPath = Files.createDirectories(tempDir.resolve("git-project"));
		git(repositoryPath, "init", "-b", "dev");
		git(repositoryPath, "remote", "add", "origin",
			"git@github.com:example/git-project.git");
		PostgresProjectRepository repository = new PostgresProjectRepository(jdbc);
		ProjectService service = new ProjectService(repository, AuditService.noop(),
			new ProcessGitCommandExecutor(new CommandExecutor()));

		Project created = service.createProject(new CreateProjectRequest(
			"Git project", repositoryPath.toString(), "Existing local project"));
		Project reloaded = new PostgresProjectRepository(jdbc).get(created.getProjectId());

		assertNotNull(reloaded);
		assertEquals("git@github.com:example/git-project.git", reloaded.getRepositoryUrl());
		assertEquals("dev", reloaded.getDefaultBranch());
	}

	@Test
	void projectServicePersistsNullRemoteForLocalOnlyGitProject() throws Exception {
		Path repositoryPath = Files.createDirectories(tempDir.resolve("local-only"));
		git(repositoryPath, "init", "-b", "local-dev");
		PostgresProjectRepository repository = new PostgresProjectRepository(jdbc);
		ProjectService service = new ProjectService(repository, AuditService.noop(),
			new ProcessGitCommandExecutor(new CommandExecutor()));

		Project created = service.createProject(new CreateProjectRequest(
			"Local only", repositoryPath.toString(), null));
		Project reloaded = new PostgresProjectRepository(jdbc).get(created.getProjectId());

		assertNotNull(reloaded);
		assertNull(reloaded.getRepositoryUrl());
		assertEquals("local-dev", reloaded.getDefaultBranch());
	}

	@Test
	void localOnlyProjectAttachesAndReloadsWorkspaceWithNullRepositoryUrl() throws Exception {
		Path repositoryPath = Files.createDirectories(tempDir.resolve("local-workspace"));
		git(repositoryPath, "init", "-b", "local-main");
		PostgresProjectRepository projects = new PostgresProjectRepository(jdbc);
		ProjectService projectService = new ProjectService(projects, AuditService.noop(),
			new ProcessGitCommandExecutor(new CommandExecutor()));
		Project project = projectService.createProject(new CreateProjectRequest(
			"Local workspace", repositoryPath.toString(), null));
		assertNull(project.getRepositoryUrl());
		WorkspaceService workspaceService = new WorkspaceService(
			new PostgresWorkspaceRepository(jdbc),
			new ProcessGitCommandExecutor(new CommandExecutor()), AuditService.noop());

		Workspace created = workspaceService.createProjectWorkspace(project.getProjectId(),
			repositoryPath.toString(), project.getRepositoryUrl());
		Workspace reloaded = new PostgresWorkspaceRepository(new PostgresJdbc(dataSource))
			.get(created.getWorkspaceId());

		assertNotNull(reloaded);
		assertEquals(project.getProjectId(), reloaded.getProjectId());
		assertEquals(repositoryPath.toString(), reloaded.getPath());
		assertEquals("local-main", reloaded.getBranch());
		assertNull(reloaded.getRepositoryUrl());
	}

	private void git(Path directory, String... arguments) throws Exception {
		List<String> command = new java.util.ArrayList<>();
		command.add("git");
		command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
		int exitCode = process.waitFor();
		String error = new String(process.getErrorStream().readAllBytes());
		assertEquals(0, exitCode, error);
	}

	@Test
	void workspaceCrud() {
		PostgresWorkspaceRepository repository = new PostgresWorkspaceRepository(jdbc);
		Workspace workspace = new Workspace("workspace-1", "project-1", "/work/repo", "main",
			WorkspaceStatus.READY, NOW, NOW, "https://example.com/repo.git");

		repository.save(workspace);
		Workspace loaded = repository.get("workspace-1");
		assertNotNull(loaded);
		assertEquals("project-1", loaded.getProjectId());
		assertEquals("main", loaded.getBranch());
		assertEquals(WorkspaceStatus.READY, loaded.getStatus());
		assertTrue(repository.getByProjectId("project-1").isPresent());
		assertEquals(1, repository.listByProjectId("project-1").size());

		loaded.lock();
		repository.save(loaded);
		assertEquals(WorkspaceStatus.LOCKED, repository.get("workspace-1").getStatus());

		assertTrue(repository.delete("workspace-1"));
		assertNull(repository.get("workspace-1"));
	}

	@Test
	void taskCrud() {
		PostgresTaskRepository repository = new PostgresTaskRepository(jdbc);
		TaskRecord task = TaskRecord.restore("task-1", "实现登录", "实现登录功能", "project-1",
			"workspace-1", TaskStatus.APPROVED, NOW, NOW, "approval-1", null, null);

		repository.save(task);
		TaskRecord loaded = repository.get("task-1");
		assertNotNull(loaded);
		assertEquals("实现登录", loaded.getName());
		assertEquals(TaskStatus.APPROVED, loaded.getStatus());
		assertEquals("approval-1", loaded.getApprovalId());
		assertNull(loaded.getSourceBacklogItemId());
		assertEquals(1, repository.listByProject("project-1").size());
		assertTrue(repository.list().stream().anyMatch(item -> "task-1".equals(item.getTaskId())));
	}

	@Test void taskSourceBacklogLineageSurvivesRepositoryRestart() {
		PostgresTaskRepository first = new PostgresTaskRepository(jdbc);
		TaskRecord task = TaskRecord.restore("task-lineage", "Converted", "Description", "project-1",
			"workspace-1", ExecutionMode.READ_WRITE, TaskStatus.PLANNING, NOW, NOW,
			"approval-1", null, null, "backlog-1");
		first.save(task);
		TaskRecord loaded = new PostgresTaskRepository(jdbc).get("task-lineage");
		assertEquals("backlog-1", loaded.getSourceBacklogItemId());
	}

	@Test
	void taskCenterSurvivesServiceRestartInPostgresMode() {
		PostgresTaskRepository repository = new PostgresTaskRepository(jdbc);
		com.aidevos.orchestrator.planner.PlannerService planner = org.mockito.Mockito.mock(
			com.aidevos.orchestrator.planner.PlannerService.class);
		com.aidevos.orchestrator.plan.approval.PlanApprovalService approvals = org.mockito.Mockito.mock(
			com.aidevos.orchestrator.plan.approval.PlanApprovalService.class);
		com.aidevos.orchestrator.plan.run.PlanRunRepository runs = org.mockito.Mockito.mock(
			com.aidevos.orchestrator.plan.run.PlanRunRepository.class);
		org.mockito.Mockito.when(planner.createPlan(org.mockito.ArgumentMatchers.any()))
			.thenReturn(com.aidevos.orchestrator.planner.PlanningResult.failure("hermes", null,
				List.of("analysis-only")));
		TaskCenterService first = new TaskCenterService(planner, approvals, runs, null,
			AuditService.noop(), repository);
		TaskRecord created = first.createTask(new CreateTaskRequest("Analyze JJX", "Inspect",
			"Analyze", "hermes", "project-jjx", "workspace-jjx", ExecutionMode.READ_ONLY),
			"/home/administrator/jjx");

		TaskCenterService restarted = new TaskCenterService(planner, approvals, runs, null,
			AuditService.noop(), new PostgresTaskRepository(jdbc));
		TaskRecord reloaded = restarted.getTask(created.getTaskId()).orElseThrow();

		assertEquals("project-jjx", reloaded.getProjectId());
		assertEquals("workspace-jjx", reloaded.getWorkspaceId());
		assertEquals(ExecutionMode.READ_ONLY, reloaded.getExecutionMode());
	}

	@Test
	void planRunRetainsOriginalTaskAndApprovedContextAfterRepositoryRestart() {
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource, mapper);
		PostgresPlanRunRepository first = new PostgresPlanRunRepository(store, dataSource, mapper);
		com.aidevos.orchestrator.plan.PlanSnapshot snapshot =
			new com.aidevos.orchestrator.plan.PlanSnapshot(List.of(), Set.of(), List.of(), Set.of(),
				"v1", Map.of("projectId", "project-1", "workspaceId", "workspace-1",
					"workspacePath", "/workspace/project", "executionMode", "READ_ONLY"));
		com.aidevos.orchestrator.plan.Plan plan = new com.aidevos.orchestrator.plan.Plan("plan-1",
			1, "Analyze", com.aidevos.orchestrator.plan.PlanStatus.DRAFT, List.of(), List.of(),
			snapshot, NOW);
		com.aidevos.orchestrator.plan.run.PlanRun run =
			new com.aidevos.orchestrator.plan.run.PlanRun("run-1", "approval-1", "task-1",
				plan, List.of(), NOW);
		first.create("approval-1", run);

		PostgresPlanRunRepository restarted = new PostgresPlanRunRepository(
			new PostgresDocumentStore(dataSource, mapper), dataSource, mapper);
		com.aidevos.orchestrator.plan.run.PlanRun loaded = restarted.get("run-1");

		assertEquals("task-1", loaded.getOriginalTaskId());
		assertEquals("project-1", loaded.getPlan().snapshot().plannerMetadata().get("projectId"));
		assertEquals("READ_ONLY",
			loaded.getPlan().snapshot().plannerMetadata().get("executionMode"));
	}

	@Test
	void executionRecordRoundTripWithJsonFields() {
		PostgresExecutionRecordRepository repository =
			new PostgresExecutionRecordRepository(jdbc, mapper);
		ExecutionRecord record = new ExecutionRecord();
		record.setId("exec-1");
		record.setTaskId("task-1");
		record.setAgentName("CODEX");
		record.setStatus("SUCCESS");
		record.setOutput("done");
		record.setBranch("main");
		record.setGitDiffStat("1 file changed");
		record.setStartedAt(NOW);
		record.setCompletedAt(NOW.plusSeconds(5));
		ExecutionReport report = new ExecutionReport();
		report.setTaskId("task-1");
		report.setAgentName("CODEX");
		report.setSuccess(true);
		report.setOutput("ok");
		record.setReport(report);
		ExecutionArtifact artifact = new ExecutionArtifact();
		artifact.setType("log");
		artifact.setName("run.log");
		record.setArtifacts(List.of(artifact));

		repository.save(record);
		ExecutionRecord loaded = repository.get("exec-1");
		assertNotNull(loaded);
		assertEquals("SUCCESS", loaded.getStatus());
		assertEquals(NOW, loaded.getStartedAt());
		assertNotNull(loaded.getReport());
		assertTrue(loaded.getReport().isSuccess());
		assertEquals("CODEX", loaded.getReport().getAgentName());
		assertEquals(1, loaded.getArtifacts().size());
		assertEquals("run.log", loaded.getArtifacts().getFirst().getName());
		assertEquals(1, repository.getAll().size());
	}

	@Test
	void changeCommitCiFeedbackRoundTrip() {
		PostgresChangeRepository changes = new PostgresChangeRepository(jdbc);
		ChangeSet change = new ChangeSet("change-1", "task-1", "workspace-1", "project-1",
			"exec-1", "main", "diff", "stat", 1, 2, 1, 1, 0, 0, NOW);
		change.markReviewing();
		change.markApproved("user-1");
		changes.save(change);
		ChangeSet loadedChange = changes.get("change-1");
		assertEquals(ChangeStatus.APPROVED, loadedChange.getStatus());
		assertEquals("user-1", loadedChange.getReviewedBy());
		assertEquals(1, changes.getByTaskId("task-1").size());

		PostgresCommitRepository commits = new PostgresCommitRepository(jdbc);
		CommitRecord commit = new CommitRecord("commit-1", "change-1", "task-1", "workspace-1",
			"main", "feat: login", NOW);
		commit.markCommitting();
		commit.markSuccess("abc123");
		commits.save(commit);
		CommitRecord loadedCommit = commits.get("commit-1");
		assertEquals(CommitStatus.SUCCESS, loadedCommit.getStatus());
		assertEquals("abc123", loadedCommit.getGitHash());

		PostgresCiRepository ci = new PostgresCiRepository(jdbc);
		CiRunRecord run = new CiRunRecord("ci-1", "task-1", "pr-1", "github", "main",
			"abc123", NOW);
		run.markRunning();
		run.markSuccess();
		ci.save(run);
		CiRunRecord loadedRun = ci.get("ci-1");
		assertEquals(CiStatus.SUCCESS, loadedRun.getStatus());
		assertNotNull(loadedRun.getFinishedAt());
		assertEquals(1, ci.getByPullRequestId("pr-1").size());

		PostgresFeedbackRepository feedback = new PostgresFeedbackRepository(jdbc);
		PrFeedbackRecord record = new PrFeedbackRecord("feedback-1", "task-1", "pr-1",
			"repair-1", "", "", "ci-1", FeedbackStatus.CREATED, 0, NOW);
		record.markRepairing();
		feedback.save(record);
		PrFeedbackRecord loadedFeedback = feedback.get("feedback-1");
		assertEquals(FeedbackStatus.REPAIRING, loadedFeedback.getStatus());
		assertEquals("repair-1", loadedFeedback.getRepairTaskId());
		assertEquals(1, feedback.getByCiRunId("ci-1").size());
	}

	@Test
	void changeServiceStateTransitionsPersistAcrossPostgresReloads() {
		PostgresChangeRepository changes = new PostgresChangeRepository(jdbc);
		WorkspaceService workspaces = org.mockito.Mockito.mock(WorkspaceService.class);
		Workspace workspace = new Workspace("workspace-state", "project-1", tempDir.resolve("repo").toString(),
			"main", WorkspaceStatus.READY, NOW, NOW);
		org.mockito.Mockito.when(workspaces.getWorkspace("workspace-state"))
			.thenReturn(java.util.Optional.of(workspace));
		org.mockito.Mockito.when(workspaces.checkGitStatus("workspace-state"))
			.thenReturn(new com.aidevos.orchestrator.workspace.git.GitStatus("main", 1, 0, 0));
		org.mockito.Mockito.when(workspaces.getGitDiff("workspace-state"))
			.thenReturn(new com.aidevos.orchestrator.workspace.git.GitDiff(1, 1, 0, "1 file changed"));
		org.mockito.Mockito.when(workspaces.getGitDiffContent("workspace-state"))
			.thenReturn("diff --git a/a.txt b/a.txt\n");
		com.aidevos.orchestrator.change.ChangeService service =
			new com.aidevos.orchestrator.change.ChangeService(changes, workspaces, AuditService.noop());

		ChangeSet change = service.createChange("task-state", "workspace-state", "project-1", "exec-state");
		assertEquals(ChangeStatus.CREATED, changes.get(change.getChangeId()).getStatus());
		service.startReview(change.getChangeId());
		assertEquals(ChangeStatus.REVIEWING, changes.get(change.getChangeId()).getStatus());
		service.approve(change.getChangeId(), "user-1");
		assertEquals(ChangeStatus.APPROVED, changes.get(change.getChangeId()).getStatus());
		service.markCommitted(change.getChangeId());
		assertEquals(ChangeStatus.COMMITTED, changes.get(change.getChangeId()).getStatus());

		ChangeSet rejected = service.createChange("task-state-reject", "workspace-state", "project-1", "exec-reject");
		service.startReview(rejected.getChangeId());
		service.reject(rejected.getChangeId(), "user-2");
		assertEquals(ChangeStatus.REJECTED, changes.get(rejected.getChangeId()).getStatus());
	}

	@Test
	void repairTraceUsageRoundTrip() {
		PostgresRepairRepository repairs = new PostgresRepairRepository(jdbc, mapper);
		FailureContext context = new FailureContext("task-1", "workspace-1", "test-1",
			"boom", "stack", "report", "diff", "TEST_FAILURE", "test-1", "", "main", 1, NOW);
		RepairTask repair = new RepairTask("repair-1", "task-1", "workspace-1", context);
		repair.markAnalyzing();
		repair.incrementRetry();
		repairs.save(repair);
		RepairTask loadedRepair = repairs.get("repair-1");
		assertEquals(RepairStatus.ANALYZING, loadedRepair.getStatus());
		assertEquals(1, loadedRepair.getRetryCount());
		assertEquals("boom", loadedRepair.getFailureContext().errorMessage());
		assertEquals(1, repairs.getByTaskId("task-1").size());

		PostgresTraceRepository traces = new PostgresTraceRepository(jdbc);
		TraceRecord trace = new TraceRecord("trace-1", "task-1", "project-1", "graph-1",
			TraceStatus.RUNNING, NOW);
		trace.setNodeId("CODEX_IMPLEMENTATION");
		trace.setAgentType("CODEX");
		trace.complete();
		traces.save(trace);
		TraceRecord loadedTrace = traces.get("trace-1");
		assertEquals(TraceStatus.SUCCESS, loadedTrace.getStatus());
		assertEquals("CODEX", loadedTrace.getAgentType());
		assertTrue(loadedTrace.getDuration() >= 0);
		assertEquals(1, traces.listByTask("task-1").size());

		PostgresUsageRepository usage = new PostgresUsageRepository(jdbc);
		usage.save(new UsageRecord("usage-1", "task-1", "project-1", "CODEX", "codex-test",
			1000, 500, 1500, 0.0105, NOW));
		assertEquals(1, usage.list().size());
		assertEquals(1500, usage.list().getFirst().totalTokens());
	}
}
