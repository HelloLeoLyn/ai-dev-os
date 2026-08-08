package com.aidevos.orchestrator.commit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
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
 * Unit verification of the commit flow: only APPROVED changes can be
 * committed, the git commit goes through GitCommandExecutor, the CommitRecord
 * and ChangeSet COMMITTED are persisted and the COMMIT_* audit trail is
 * emitted.
 */
class CommitServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryCommitRepository commitRepository;
	private InMemoryAuditRepository auditRepository;
	private WorkspaceService workspaceService;
	private GitCommandExecutor gitCommandExecutor;
	private ChangeService changeService;
	private CommitService commitService;

	@BeforeEach
	void setUp() {
		commitRepository = new InMemoryCommitRepository();
		auditRepository = new InMemoryAuditRepository();
		workspaceService = mock(WorkspaceService.class);
		gitCommandExecutor = mock(GitCommandExecutor.class);
		AuditService auditService = new AuditService(auditRepository);
		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		commitService = new CommitService(commitRepository, changeService, workspaceService,
			gitCommandExecutor, auditService);

		when(workspaceService.getWorkspace("workspace-1")).thenReturn(
			Optional.of(new Workspace("workspace-1", "project-a", "/tmp/repo", "main",
				WorkspaceStatus.READY, NOW, NOW)));
		when(workspaceService.checkGitStatus("workspace-1")).thenReturn(
			new GitStatus("main", 1, 0, 0));
		when(workspaceService.getGitDiff("workspace-1")).thenReturn(
			new GitDiff(1, 1, 0, "1 file changed, 1 insertion(+)"));
		when(workspaceService.getGitDiffContent("workspace-1")).thenReturn(
			"diff --git a/a.txt b/a.txt\n@@ -1 +1,2 @@\n");
	}

	@Test
	void shouldCommitApprovedChange() {
		ChangeSet change = approvedChange();
		when(gitCommandExecutor.commit("/tmp/repo", "AI change " + change.getChangeId()
			+ " for task task-1")).thenReturn("abc123def");

		CommitRecord record = commitService.commit(change.getChangeId());

		assertEquals(CommitStatus.SUCCESS, record.getStatus());
		assertEquals("abc123def", record.getGitHash());
		assertEquals(change.getChangeId(), record.getChangeId());
		assertEquals("task-1", record.getTaskId());
		assertEquals("workspace-1", record.getWorkspaceId());
		assertEquals("main", record.getBranch());
		assertEquals(ChangeStatus.COMMITTED, changeService.getChange(change.getChangeId())
			.orElseThrow().getStatus());
		assertEquals(record, commitRepository.get(record.getCommitId()));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.COMMIT_STARTED
			&& "task-1".equals(event.taskId())
			&& record.getCommitId().equals(event.aggregateId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.COMMIT_SUCCESS
			&& "abc123def".equals(event.metadata().get("gitHash"))));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CHANGE_COMMITTED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldRejectCommitWhenChangeNotApproved() {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");

		assertThrows(IllegalStateException.class,
			() -> commitService.commit(change.getChangeId()));
		verify(gitCommandExecutor, never()).commit(org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any());
		assertTrue(commitRepository.list().isEmpty());
	}

	@Test
	void shouldFailCommitWhenGitFails() {
		ChangeSet change = approvedChange();
		when(gitCommandExecutor.commit(org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any())).thenReturn("");

		assertThrows(IllegalStateException.class,
			() -> commitService.commit(change.getChangeId()));

		CommitRecord record = commitRepository.list().get(0);
		assertEquals(CommitStatus.FAILED, record.getStatus());
		assertEquals(ChangeStatus.APPROVED, changeService.getChange(change.getChangeId())
			.orElseThrow().getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.COMMIT_FAILED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldReturnCommitAndTaskCommits() {
		ChangeSet change = approvedChange();
		when(gitCommandExecutor.commit(org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any())).thenReturn("abc123def");
		CommitRecord record = commitService.commit(change.getChangeId());

		assertEquals(record, commitService.getCommit(record.getCommitId()).orElseThrow());
		assertFalse(commitService.getCommit("missing").isPresent());
		assertEquals(1, commitService.getCommitsByTask("task-1").size());
		assertTrue(commitService.getCommitsByTask("other-task").isEmpty());
	}

	@Test
	void shouldThrowForMissingChange() {
		assertThrows(ResourceNotFoundException.class, () -> commitService.commit("missing"));
	}

	private ChangeSet approvedChange() {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");
		changeService.startReview(change.getChangeId());
		changeService.approve(change.getChangeId(), "user-1");
		return changeService.getChange(change.getChangeId()).orElseThrow();
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
