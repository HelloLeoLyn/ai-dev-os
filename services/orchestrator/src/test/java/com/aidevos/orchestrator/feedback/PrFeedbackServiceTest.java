package com.aidevos.orchestrator.feedback;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit verification of the pull request feedback loop state machine:
 * CREATED -> REPAIRING -> WAITING_REVIEW -> PUSHED -> RECHECKING -> SUCCESS
 * (FAILED on repair/commit/push failure), plus retry and the FEEDBACK_*
 * audit events.
 */
class PrFeedbackServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryFeedbackRepository repository;
	private InMemoryAuditRepository auditRepository;
	private PullRequestService pullRequestService;
	private CommitService commitService;
	private RemoteGitService remoteGitService;
	private ChangeService changeService;
	private CiService ciService;
	private PrFeedbackService feedbackService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryFeedbackRepository();
		auditRepository = new InMemoryAuditRepository();
		pullRequestService = mock(PullRequestService.class);
		commitService = mock(CommitService.class);
		remoteGitService = mock(RemoteGitService.class);
		changeService = mock(ChangeService.class);
		ciService = mock(CiService.class);
		feedbackService = new PrFeedbackService(repository, pullRequestService, commitService,
			remoteGitService, changeService, new AuditService(auditRepository));
		feedbackService.setCiService(ciService);

		when(pullRequestService.getByTask("task-1")).thenReturn(List.of(pullRequest()));
		when(commitService.commit(any())).thenReturn(commit());
		when(remoteGitService.push(any(), any())).thenReturn(push());
		when(ciService.check(any(), any())).thenReturn(new CiRunRecord("ci-2", "task-1",
			"pr-1", "mock", "main", "abc123def", NOW));
	}

	@Test
	void shouldCreateFeedbackAndRepairFromCiFailure() {
		PrFeedbackRecord feedback = feedbackService.onRepairStarted(context(), repair("repair-1"));

		assertEquals("task-1", feedback.getTaskId());
		assertEquals("pr-1", feedback.getPullRequestId());
		assertEquals("repair-1", feedback.getRepairTaskId());
		assertEquals("ci-1", feedback.getCiRunId());
		assertEquals(FeedbackStatus.REPAIRING, feedback.getStatus());
		assertEquals(0, feedback.getRetryCount());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.FEEDBACK_CREATED
			&& "task-1".equals(event.taskId())
			&& feedback.getFeedbackId().equals(event.aggregateId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.FEEDBACK_REPAIRING
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldReuseFeedbackAndCountRetryOnAnotherRepairCycle() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		PrFeedbackRecord second = feedbackService.onRepairStarted(context(), repair("repair-2"));

		assertEquals(1, repository.list().size());
		assertEquals(FeedbackStatus.REPAIRING, second.getStatus());
		assertEquals(1, second.getRetryCount());
		assertEquals("repair-2", second.getRepairTaskId());
	}

	@Test
	void shouldWaitForReviewAfterRepairSucceeds() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		when(changeService.getChangesByTask("task-1")).thenReturn(List.of(change()));

		PrFeedbackRecord feedback = feedbackService.onRepairSucceeded(context(),
			repair("repair-1"));

		assertEquals(FeedbackStatus.WAITING_REVIEW, feedback.getStatus());
		assertEquals("change-1", feedback.getChangeId());
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.FEEDBACK_WAITING_REVIEW && "task-1".equals(event.taskId())
			&& "change-1".equals(event.metadata().get("changeId"))));
	}

	@Test
	void shouldMarkFailedWhenRepairFails() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));

		PrFeedbackRecord feedback = feedbackService.onRepairFailed(context(),
			repair("repair-1"));

		assertEquals(FeedbackStatus.FAILED, feedback.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.FEEDBACK_FAILED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldCommitPushAndRecheckAfterApproval() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		feedbackService.onRepairSucceeded(context(), repair("repair-1"));

		PrFeedbackRecord feedback = feedbackService.onChangeApproved("change-1", "task-1");

		assertEquals(FeedbackStatus.RECHECKING, feedback.getStatus());
		assertEquals("commit-1", feedback.getCommitId());
		verify(commitService).commit("change-1");
		verify(remoteGitService).push("commit-1", null);
		verify(ciService).check("pr-1", "abc123def");
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.FEEDBACK_PUSHED
			&& "task-1".equals(event.taskId())));
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.FEEDBACK_RECHECKING && "task-1".equals(event.taskId())));
	}

	@Test
	void shouldNotReactToApprovalWithoutWaitingFeedback() {
		PrFeedbackRecord feedback = feedbackService.onChangeApproved("change-1", "task-1");

		assertEquals(null, feedback);
		verify(commitService, never()).commit(any());
	}

	@Test
	void shouldMarkFailedWhenCommitFails() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		feedbackService.onRepairSucceeded(context(), repair("repair-1"));
		when(commitService.commit(any()))
			.thenThrow(new IllegalStateException("git commit failed"));

		PrFeedbackRecord feedback = feedbackService.onChangeApproved("change-1", "task-1");

		assertEquals(FeedbackStatus.FAILED, feedback.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.FEEDBACK_FAILED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldCompleteWhenRecheckCiSucceeds() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		feedbackService.onRepairSucceeded(context(), repair("repair-1"));
		PrFeedbackRecord feedback = feedbackService.onChangeApproved("change-1", "task-1");
		CiRunRecord run = new CiRunRecord("ci-2", "task-1", "pr-1", "mock", "main",
			"abc123def", NOW);

		PrFeedbackRecord completed = feedbackService.onCiSucceeded(run);

		assertEquals(FeedbackStatus.SUCCESS, completed.getStatus());
		assertEquals(feedback.getFeedbackId(), completed.getFeedbackId());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.FEEDBACK_SUCCESS
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldRetryFailedFeedbackByReRunningRepair() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		PrFeedbackRecord feedback = feedbackService.onRepairFailed(context(),
			repair("repair-1"));

		PrFeedbackRecord retried = feedbackService.retry(feedback.getFeedbackId());

		assertEquals(feedback.getFeedbackId(), retried.getFeedbackId());
		verify(ciService).retryRepairFromCiRun("ci-1");
	}

	@Test
	void shouldReturnRecordAndTaskRecords() {
		feedbackService.onRepairStarted(context(), repair("repair-1"));
		PrFeedbackRecord feedback = repository.list().get(0);

		assertEquals(feedback, feedbackService.get(feedback.getFeedbackId()).orElseThrow());
		assertFalse(feedbackService.get("missing").isPresent());
		assertEquals(1, feedbackService.getByTask("task-1").size());
		assertTrue(feedbackService.getByTask("other-task").isEmpty());
		assertNotNull(feedback.getCreatedAt());
		assertNotNull(feedback.getUpdatedAt());
	}

	private FailureContext context() {
		return new FailureContext("task-1", "workspace-1", null, "CI run failed: pipeline-1",
			null, "https://mock.dev/ci/pipeline-1", "1 file changed",
			"CI_FAILURE", "ci-1", "abc123def", "main", 1, NOW);
	}

	private RepairTask repair(String repairId) {
		return new RepairTask(repairId, "task-1", "workspace-1", context());
	}

	private PullRequestRecord pullRequest() {
		PullRequestRecord record = new PullRequestRecord("pr-1", "task-1", "commit-1",
			"remote-1", "main", "main", "title", "description",
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

	private RemoteBranchRecord push() {
		RemoteBranchRecord record = new RemoteBranchRecord("remote-1", "task-1", "workspace-1",
			"commit-1", "main", "origin", "https://mock.dev/git/repo.git", NOW);
		record.markPushing();
		record.markSuccess();
		return record;
	}

	private ChangeSet change() {
		return new ChangeSet("change-1", "task-1", "workspace-1", "project-x", "exec-1",
			"main", "diff", "1 file changed, 1 insertion(+)", 1, 1, 0, 1, 0, 0, NOW);
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
