package com.aidevos.orchestrator.remote;

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
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit verification of remote git integration: only SUCCESS commits can be
 * pushed, the remote URL is resolved from the workspace remotes, the push
 * goes through GitCommandExecutor and the REMOTE_PUSH_* audit trail is
 * emitted.
 */
class RemoteGitServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryRemoteRepository repository;
	private InMemoryAuditRepository auditRepository;
	private CommitService commitService;
	private WorkspaceService workspaceService;
	private GitCommandExecutor gitCommandExecutor;
	private RemoteGitService remoteGitService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryRemoteRepository();
		auditRepository = new InMemoryAuditRepository();
		commitService = mock(CommitService.class);
		workspaceService = mock(WorkspaceService.class);
		gitCommandExecutor = mock(GitCommandExecutor.class);
		remoteGitService = new RemoteGitService(repository, commitService, workspaceService,
			gitCommandExecutor, new AuditService(auditRepository));

		when(commitService.getCommit("commit-1")).thenReturn(Optional.of(successfulCommit()));
		when(workspaceService.getWorkspace("workspace-1")).thenReturn(
			Optional.of(new Workspace("workspace-1", "project-a", "/tmp/repo", "main",
				WorkspaceStatus.READY, NOW, NOW)));
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\norigin\tfile:///tmp/bare.git (push)\n");
	}

	@Test
	void shouldPushSuccessfullyToDefaultOrigin() {
		when(gitCommandExecutor.push("/tmp/repo", "origin", "main")).thenReturn(true);

		RemoteBranchRecord record = remoteGitService.push("commit-1", null);

		assertEquals(RemoteStatus.SUCCESS, record.getStatus());
		assertEquals("origin", record.getRemote());
		assertEquals("file:///tmp/bare.git", record.getUrl());
		assertEquals("commit-1", record.getCommitId());
		assertEquals("task-1", record.getTaskId());
		assertEquals("main", record.getBranch());
		assertEquals(record, repository.get(record.getRemoteId()));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REMOTE_PUSH_STARTED
			&& "task-1".equals(event.taskId())
			&& record.getRemoteId().equals(event.aggregateId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REMOTE_PUSH_SUCCESS
			&& "task-1".equals(event.taskId())
			&& "file:///tmp/bare.git".equals(event.metadata().get("url"))));
	}

	@Test
	void shouldPushToProvidedRemoteName() {
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"upstream\tfile:///tmp/upstream.git (fetch)\nupstream\tfile:///tmp/upstream.git (push)\n");
		when(gitCommandExecutor.push("/tmp/repo", "upstream", "main")).thenReturn(true);

		RemoteBranchRecord record = remoteGitService.push("commit-1", "upstream");

		assertEquals(RemoteStatus.SUCCESS, record.getStatus());
		assertEquals("upstream", record.getRemote());
		assertEquals("file:///tmp/upstream.git", record.getUrl());
	}

	@Test
	void shouldRejectPushWhenCommitNotSuccess() {
		CommitRecord failed = failedCommit();
		when(commitService.getCommit("commit-1")).thenReturn(Optional.of(failed));

		assertThrows(IllegalStateException.class, () -> remoteGitService.push("commit-1", null));
		verify(gitCommandExecutor, never()).push(org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		assertTrue(repository.list().isEmpty());
	}

	@Test
	void shouldFailPushWhenGitFails() {
		when(gitCommandExecutor.push("/tmp/repo", "origin", "main")).thenReturn(false);

		assertThrows(IllegalStateException.class, () -> remoteGitService.push("commit-1", null));

		RemoteBranchRecord record = repository.list().get(0);
		assertEquals(RemoteStatus.FAILED, record.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REMOTE_PUSH_FAILED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldReturnRecordAndTaskRemotes() {
		when(gitCommandExecutor.push("/tmp/repo", "origin", "main")).thenReturn(true);
		RemoteBranchRecord record = remoteGitService.push("commit-1", null);

		assertEquals(record, remoteGitService.get(record.getRemoteId()).orElseThrow());
		assertFalse(remoteGitService.get("missing").isPresent());
		assertEquals(1, remoteGitService.getByTask("task-1").size());
		assertTrue(remoteGitService.getByTask("other-task").isEmpty());
	}

	@Test
	void shouldThrowForMissingCommit() {
		when(commitService.getCommit("missing")).thenReturn(Optional.empty());
		assertThrows(ResourceNotFoundException.class, () -> remoteGitService.push("missing", null));
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

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
