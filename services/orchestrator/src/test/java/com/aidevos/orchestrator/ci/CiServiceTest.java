package com.aidevos.orchestrator.ci;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.CiFailureAnalyzer;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit verification of CI status checks: a check creates a CiRunRecord and
 * emits CI_STARTED, polls provider status into CI_SUCCESS / CI_FAILED /
 * CI_CANCELLED, and on CI_FAILED builds a CI_FAILURE FailureContext and
 * starts the repair loop via the RepairCoordinator (once per run).
 */
class CiServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final String REPORT_URL = "https://mock.dev/ci/pipeline-1";

	private InMemoryCiRepository repository;
	private InMemoryAuditRepository auditRepository;
	private CiProvider provider;
	private PullRequestService pullRequestService;
	private CommitService commitService;
	private RepairCoordinator repairCoordinator;
	private CiService ciService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryCiRepository();
		auditRepository = new InMemoryAuditRepository();
		provider = mock(CiProvider.class);
		pullRequestService = mock(PullRequestService.class);
		commitService = mock(CommitService.class);
		repairCoordinator = mock(RepairCoordinator.class);
		ciService = new CiService(repository, provider, new CiProviderProperties(),
			pullRequestService, commitService, repairCoordinator,
			new CiFailureAnalyzer(mock(WorkspaceService.class), mock(ChangeService.class)),
			new AuditService(auditRepository));

		when(pullRequestService.get("pr-1")).thenReturn(Optional.of(pullRequest()));
		when(commitService.getCommit("commit-1")).thenReturn(Optional.of(commit()));
		when(provider.trigger(any(CiTriggerRequest.class)))
			.thenReturn(new CiTriggerResult("pipeline-1", REPORT_URL));
	}

	@Test
	void shouldCreateRunAndMarkSuccess() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.SUCCESS, REPORT_URL));

		CiRunRecord run = ciService.check("pr-1");

		assertEquals(CiStatus.SUCCESS, run.getStatus());
		assertEquals("pipeline-1", run.getPipelineId());
		assertEquals("task-1", run.getTaskId());
		assertEquals("pr-1", run.getPullRequestId());
		assertEquals("mock", run.getProvider());
		assertEquals("main", run.getBranch());
		assertEquals("abc123def", run.getCommitHash());
		assertEquals(REPORT_URL, run.getReportUrl());
		assertNotNull(run.getFinishedAt());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_STARTED
			&& "task-1".equals(event.taskId())
			&& run.getCiRunId().equals(event.aggregateId())
			&& "mock".equals(event.metadata().get("provider"))
			&& "pipeline-1".equals(event.metadata().get("pipelineId"))));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_SUCCESS
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldEmitCiRunningOnRecheckWhileInProgress() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.RUNNING, REPORT_URL));

		CiRunRecord first = ciService.check("pr-1");
		CiRunRecord second = ciService.check("pr-1");

		assertEquals(CiStatus.RUNNING, first.getStatus());
		assertEquals(CiStatus.RUNNING, second.getStatus());
		verify(provider, times(1)).trigger(any(CiTriggerRequest.class));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_STARTED
			&& "task-1".equals(event.taskId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_RUNNING
			&& "task-1".equals(event.taskId())));
	}

	/** V1-DELIVERY-AUTO-ADVANCE-CLOSEOUT：CI RUNNING 状态变更后必须持久化，reload 仍为 RUNNING */
	@Test
	void ciRunningStatePersistsAfterReload() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.RUNNING, REPORT_URL));

		CiRunRecord run = ciService.check("pr-1");
		assertEquals(CiStatus.RUNNING, run.getStatus());

		// reload：markRunning 后必须落库（修复前仓库/DB 停留在 PENDING）
		CiRunRecord reloaded = repository.get(run.getCiRunId());
		assertEquals(CiStatus.RUNNING, reloaded.getStatus());
	}

	@Test
	void shouldMarkFailedAndStartRepairFromCiFailure() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.FAILED, REPORT_URL));

		CiRunRecord run = ciService.check("pr-1");

		assertEquals(CiStatus.FAILED, run.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_FAILED
			&& "task-1".equals(event.taskId())));
		ArgumentCaptor<FailureContext> captor = ArgumentCaptor.forClass(FailureContext.class);
		verify(repairCoordinator).startRepairFromCiFailure(captor.capture());
		FailureContext context = captor.getValue();
		assertEquals("task-1", context.taskId());
		assertEquals("workspace-1", context.workspaceId());
		assertEquals(REPORT_URL, context.testReport());
		assertEquals("CI run failed: pipeline-1", context.errorMessage());
		assertEquals("CI_FAILURE", context.sourceType());
		assertEquals(run.getCiRunId(), context.sourceId());
		assertEquals("abc123def", context.commitHash());
		assertEquals("main", context.branch());
	}

	@Test
	void shouldNotStartRepairTwiceOnRecheck() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.FAILED, REPORT_URL));

		ciService.check("pr-1");
		ciService.check("pr-1");

		verify(repairCoordinator, times(1))
			.startRepairFromCiFailure(any(FailureContext.class));
	}

	@Test
	void shouldMarkCancelled() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.CANCELLED, REPORT_URL));

		CiRunRecord run = ciService.check("pr-1");

		assertEquals(CiStatus.CANCELLED, run.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_CANCELLED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldReturnRecordAndTaskRuns() {
		when(provider.getStatus("pipeline-1"))
			.thenReturn(new CiRunResult(CiStatus.SUCCESS, REPORT_URL));
		CiRunRecord run = ciService.check("pr-1");

		assertEquals(run, ciService.get(run.getCiRunId()).orElseThrow());
		assertFalse(ciService.get("missing").isPresent());
		assertEquals(1, ciService.getByTask("task-1").size());
		assertTrue(ciService.getByTask("other-task").isEmpty());
	}

	@Test
	void shouldThrowForMissingPullRequest() {
		when(pullRequestService.get("missing")).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class, () -> ciService.check("missing"));
	}

	private PullRequestRecord pullRequest() {
		PullRequestRecord record = new PullRequestRecord("pr-1", "task-1", "commit-1", "remote-1",
			"main", "main", "AI change for task task-1", "Auto-generated pull request by AI Dev OS",
			"https://mock.dev/pr/pr-1", NOW);
		record.markOpened();
		return record;
	}

	private CommitRecord commit() {
		CommitRecord record = new CommitRecord("commit-1", "change-1", "task-1", "workspace-1",
			"main", "AI change change-1 for task task-1", NOW);
		record.markCommitting();
		record.markSuccess("abc123def");
		return record;
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
