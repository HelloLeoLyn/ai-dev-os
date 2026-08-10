package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.List;

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
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectStatus;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairStatus;
import com.aidevos.orchestrator.repair.RepairTask;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresJdbc jdbc;
	private ObjectMapper mapper;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		this.jdbc = new PostgresJdbc(dataSource);
		this.mapper = new ObjectMapper();
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
		assertEquals(1, repository.listByProject("project-1").size());
		assertTrue(repository.list().stream().anyMatch(item -> "task-1".equals(item.getTaskId())));
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
