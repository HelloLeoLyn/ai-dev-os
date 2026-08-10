package com.aidevos.orchestrator.pr;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit verification of pull request management: only SUCCESS commits with a
 * SUCCESS remote push can open a PR, the provider URL is stored, close/merge
 * move state only, and the PR_* audit trail is emitted.
 */
class PullRequestServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryPullRequestRepository repository;
	private InMemoryAuditRepository auditRepository;
	private CommitService commitService;
	private RemoteGitService remoteGitService;
	private PullRequestProvider provider;
	private PullRequestService pullRequestService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryPullRequestRepository();
		auditRepository = new InMemoryAuditRepository();
		commitService = mock(CommitService.class);
		remoteGitService = mock(RemoteGitService.class);
		provider = mock(PullRequestProvider.class);
		pullRequestService = new PullRequestService(repository, commitService, remoteGitService,
			provider, new AuditService(auditRepository));

		when(commitService.getCommit("commit-1")).thenReturn(Optional.of(successfulCommit()));
		when(remoteGitService.getByTask("task-1")).thenReturn(List.of(successfulPush()));
		when(provider.create(anyString(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn("https://mock.dev/pr/pr-1");
	}

	@Test
	void shouldCreatePullRequestForCommittedAndPushedChange() {
		PullRequestRecord record = pullRequestService.createPullRequest("commit-1", null);

		assertEquals(PullRequestStatus.OPEN, record.getStatus());
		assertEquals("task-1", record.getTaskId());
		assertEquals("commit-1", record.getCommitId());
		assertEquals("remote-1", record.getRemoteId());
		assertEquals("main", record.getBranch());
		assertEquals("main", record.getTargetBranch());
		assertEquals("AI change for task task-1", record.getTitle());
		assertEquals("https://mock.dev/pr/pr-1", record.getUrl());
		assertEquals(record, repository.get(record.getPullRequestId()));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.PR_CREATED
			&& "task-1".equals(event.taskId())
			&& record.getPullRequestId().equals(event.aggregateId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.PR_OPENED
			&& "task-1".equals(event.taskId())
			&& "https://mock.dev/pr/pr-1".equals(event.metadata().get("url"))));
	}

	@Test
	void shouldApplyRequestOverrides() {
		PullRequestRecord record = pullRequestService.createPullRequest("commit-1",
			new PullRequestCreateRequest("develop", "My PR", "desc"));

		assertEquals("develop", record.getTargetBranch());
		assertEquals("My PR", record.getTitle());
		assertEquals("desc", record.getDescription());
	}

	@Test
	void shouldRejectWhenCommitNotSuccess() {
		when(commitService.getCommit("commit-1")).thenReturn(Optional.of(failedCommit()));

		assertThrows(IllegalStateException.class,
			() -> pullRequestService.createPullRequest("commit-1", null));
		assertTrue(repository.list().isEmpty());
		verify(provider, never()).create(anyString(), anyString(), anyString(), anyString(),
			anyString());
	}

	@Test
	void shouldRejectWhenNoSuccessfulRemotePush() {
		when(remoteGitService.getByTask("task-1")).thenReturn(List.of());

		assertThrows(IllegalStateException.class,
			() -> pullRequestService.createPullRequest("commit-1", null));
		assertTrue(repository.list().isEmpty());
	}

	@Test
	void shouldMarkFailedWhenProviderThrows() {
		when(provider.create(anyString(), anyString(), anyString(), anyString(), anyString()))
			.thenThrow(new IllegalStateException("provider down"));

		assertThrows(IllegalStateException.class,
			() -> pullRequestService.createPullRequest("commit-1", null));

		PullRequestRecord record = repository.list().get(0);
		assertEquals(PullRequestStatus.FAILED, record.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.PR_FAILED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldCloseOpenPullRequest() {
		PullRequestRecord created = pullRequestService.createPullRequest("commit-1", null);

		PullRequestRecord closed = pullRequestService.close(created.getPullRequestId());

		assertEquals(PullRequestStatus.CLOSED, closed.getStatus());
		verify(provider).close(created.getPullRequestId());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.PR_CLOSED
			&& "task-1".equals(event.taskId())
			&& created.getPullRequestId().equals(event.aggregateId())));
	}

	@Test
	void shouldMergeOpenPullRequest() {
		PullRequestRecord created = pullRequestService.createPullRequest("commit-1", null);

		PullRequestRecord merged = pullRequestService.merge(created.getPullRequestId());

		assertEquals(PullRequestStatus.MERGED, merged.getStatus());
		verify(provider).merge(created.getPullRequestId());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.PR_MERGED
			&& "task-1".equals(event.taskId())
			&& created.getPullRequestId().equals(event.aggregateId())));
	}

	@Test
	void shouldRejectCloseWhenNotOpen() {
		PullRequestRecord created = pullRequestService.createPullRequest("commit-1", null);
		pullRequestService.merge(created.getPullRequestId());

		assertThrows(IllegalStateException.class,
			() -> pullRequestService.close(created.getPullRequestId()));
	}

	@Test
	void shouldReturnRecordAndTaskPullRequests() {
		PullRequestRecord record = pullRequestService.createPullRequest("commit-1", null);

		assertEquals(record, pullRequestService.get(record.getPullRequestId()).orElseThrow());
		assertFalse(pullRequestService.get("missing").isPresent());
		assertEquals(1, pullRequestService.getByTask("task-1").size());
		assertTrue(pullRequestService.getByTask("other-task").isEmpty());
	}

	@Test
	void shouldThrowForMissingPullRequest() {
		assertThrows(ResourceNotFoundException.class, () -> pullRequestService.close("missing"));
		assertThrows(ResourceNotFoundException.class, () -> pullRequestService.merge("missing"));
	}

	private CommitRecord successfulCommit() {
		CommitRecord record = new CommitRecord("commit-1", "change-1", "task-1", "workspace-1",
			"main", "AI change change-1 for task task-1", NOW);
		record.markCommitting();
		record.markSuccess("abc123def");
		return record;
	}

	private CommitRecord failedCommit() {
		CommitRecord record = new CommitRecord("commit-1", "change-1", "task-1", "workspace-1",
			"main", "AI change change-1 for task task-1", NOW);
		record.markCommitting();
		record.markFailed();
		return record;
	}

	private RemoteBranchRecord successfulPush() {
		RemoteBranchRecord record = new RemoteBranchRecord("remote-1", "task-1", "workspace-1",
			"commit-1", "main", "origin", "file:///tmp/bare.git", NOW);
		record.markPushing();
		record.markSuccess();
		return record;
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
