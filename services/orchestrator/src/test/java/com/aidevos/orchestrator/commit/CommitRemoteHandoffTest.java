package com.aidevos.orchestrator.commit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.aidevos.orchestrator.remote.InMemoryRemotePushApprovalRepository;
import com.aidevos.orchestrator.remote.InMemoryRemoteRepository;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApproval;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.remote.RemotePushApprovalStatus;
import com.aidevos.orchestrator.remote.RemoteRepository;
import com.aidevos.orchestrator.remote.RemoteStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the remote delivery commit handoff:
 * <ul>
 *   <li>CommitRecord transitions are persisted (PENDING -> COMMITTING -> SUCCESS)
 *       so a repository reload still sees SUCCESS with the real git hash;</li>
 *   <li>git remote -v TAB/whitespace separated output is recognised so a
 *       committed change on a workspace with origin auto-creates a PENDING
 *       RemotePushApproval and emits REMOTE_PUSH_APPROVAL_REQUESTED;</li>
 *   <li>requestApproval re-reads the commit from the repository and requires
 *       a persisted SUCCESS state;</li>
 *   <li>RemoteGitService.push persists SUCCESS instead of leaving the record
 *       in PUSHING.</li>
 * </ul>
 */
class CommitRemoteHandoffTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final String TASK_BRANCH = "ai-dev-os/task/task-1";

	private SnapshotCommitRepository commitRepository;
	private InMemoryAuditRepository auditRepository;
	private WorkspaceService workspaceService;
	private GitCommandExecutor gitCommandExecutor;
	private ChangeService changeService;
	private CommitService commitService;
	private InMemoryRemotePushApprovalRepository approvalRepository;
	private RemoteGitService remoteGitService;
	private RemotePushApprovalService approvalService;

	@BeforeEach
	void setUp() {
		commitRepository = new SnapshotCommitRepository();
		auditRepository = new InMemoryAuditRepository();
		workspaceService = mock(WorkspaceService.class);
		gitCommandExecutor = mock(GitCommandExecutor.class);
		AuditService auditService = new AuditService(auditRepository);
		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		commitService = new CommitService(commitRepository, changeService, workspaceService,
			gitCommandExecutor, auditService);
		approvalRepository = new InMemoryRemotePushApprovalRepository();
		approvalService = new RemotePushApprovalService(
			approvalRepository, auditService);
		remoteGitService = new RemoteGitService(new InMemoryRemoteRepository(), commitService,
			workspaceService, gitCommandExecutor, auditService, approvalService);
		commitService.setRemoteGitService(remoteGitService);

		Workspace workspace = new Workspace("workspace-1", "project-a", "/tmp/repo", "main",
			WorkspaceStatus.READY, NOW, NOW);
		when(workspaceService.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		when(workspaceService.checkGitStatus("workspace-1")).thenReturn(
			new GitStatus("main", 1, 0, 0));
		when(workspaceService.getGitDiff("workspace-1")).thenReturn(
			new GitDiff(1, 1, 0, "1 file changed"));
		when(workspaceService.getGitDiffContent("workspace-1")).thenReturn(
			"diff --git a/a.txt b/a.txt\n");
	}

	@Test
	void commitPersistsCommittingThenSuccessSoReloadSeesSuccessWithHash() {
		ChangeSet change = approvedChange("main");
		when(gitCommandExecutor.commit("/tmp/repo", "AI change " + change.getChangeId()
			+ " for task task-1")).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn("");

		CommitRecord record = commitService.commit(change.getChangeId());

		assertEquals(CommitStatus.SUCCESS, record.getStatus());
		CommitRecord reloaded = commitRepository.get(record.getCommitId());
		assertEquals(CommitStatus.SUCCESS, reloaded.getStatus());
		assertEquals("abc123def", reloaded.getGitHash());
		assertEquals(List.of(CommitStatus.PENDING, CommitStatus.COMMITTING,
			CommitStatus.SUCCESS), commitRepository.statuses(record.getCommitId()));
	}

	@Test
	void commitWithTabSeparatedOriginCreatesPendingRemotePushApproval() {
		ChangeSet change = approvedChange(TASK_BRANCH);
		when(gitCommandExecutor.commit("/tmp/repo", "AI change " + change.getChangeId()
			+ " for task task-1")).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\norigin\tfile:///tmp/bare.git (push)\n");

		CommitRecord record = commitService.commit(change.getChangeId());

		assertEquals(CommitStatus.SUCCESS, record.getStatus());
		assertEquals("abc123def", commitRepository.get(record.getCommitId()).getGitHash());
		assertEquals(1, approvalRepository.getAll().size());
		RemotePushApproval approval = approvalRepository.getAll().get(0);
		assertEquals(RemotePushApprovalStatus.PENDING, approval.getStatus());
		assertEquals(record.getCommitId(), approval.getCommitId());
		assertEquals("abc123def", approval.getCommitHash());
		assertEquals("origin", approval.getRemote());
		assertEquals(TASK_BRANCH, approval.getExecutionBranch());
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.REMOTE_PUSH_APPROVAL_REQUESTED && "task-1".equals(event.taskId())));
	}

	@Test
	void commitWithoutOriginDoesNotCreateRemotePushApproval() {
		ChangeSet change = approvedChange("main");
		when(gitCommandExecutor.commit(anyString(), anyString())).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn("");

		CommitRecord record = commitService.commit(change.getChangeId());

		assertEquals(CommitStatus.SUCCESS, record.getStatus());
		assertTrue(approvalRepository.getAll().isEmpty());
		assertFalse(events().stream().anyMatch(event -> event.type()
			== EventType.REMOTE_PUSH_APPROVAL_REQUESTED));
	}

	@Test
	void commitFailurePersistsFailedState() {
		ChangeSet change = approvedChange(TASK_BRANCH);
		when(gitCommandExecutor.commit(anyString(), anyString()))
			.thenThrow(new IllegalStateException("git exploded"));

		assertThrows(IllegalStateException.class,
			() -> commitService.commit(change.getChangeId()));

		CommitRecord reloaded = commitRepository.getByTaskId("task-1").get(0);
		assertEquals(CommitStatus.FAILED, reloaded.getStatus());
		assertEquals(List.of(CommitStatus.PENDING, CommitStatus.COMMITTING,
			CommitStatus.FAILED), commitRepository.statuses(reloaded.getCommitId()));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.COMMIT_FAILED
			&& "task-1".equals(event.taskId())));
		assertTrue(approvalRepository.getAll().isEmpty());
	}

	@Test
	void approveThenPushTaskBranchPersistsSuccessAndConsumesApproval() {
		ChangeSet change = approvedChange(TASK_BRANCH);
		when(gitCommandExecutor.commit(anyString(), anyString())).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\norigin\tfile:///tmp/bare.git (push)\n");
		when(gitCommandExecutor.push("/tmp/repo", "origin", TASK_BRANCH)).thenReturn(true);

		CommitRecord record = commitService.commit(change.getChangeId());
		assertEquals(CommitStatus.SUCCESS, record.getStatus());
		RemotePushApproval approval = approvalRepository.getAll().get(0);
		assertEquals(RemotePushApprovalStatus.PENDING, approval.getStatus());
		approvalService.approve(approval.getApprovalId());

		RemoteBranchRecord pushed = remoteGitService.push(record.getCommitId(), "origin",
			approval.getApprovalId());

		assertEquals(RemoteStatus.SUCCESS, pushed.getStatus());
		assertEquals(TASK_BRANCH, pushed.getBranch());
		assertEquals("origin", pushed.getRemote());
		assertEquals(RemotePushApprovalStatus.CONSUMED,
			approvalRepository.get(approval.getApprovalId()).getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.REMOTE_PUSH_APPROVAL_APPROVED && "task-1".equals(event.taskId())));
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.REMOTE_PUSH_SUCCESS && "task-1".equals(event.taskId())));
	}

	@Test
	void pushRetryAfterSuccessIsIdempotentAndNeverRepeatsPush() {
		ChangeSet change = approvedChange(TASK_BRANCH);
		when(gitCommandExecutor.commit(anyString(), anyString())).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\norigin\tfile:///tmp/bare.git (push)\n");
		when(gitCommandExecutor.push("/tmp/repo", "origin", TASK_BRANCH)).thenReturn(true);

		CommitRecord record = commitService.commit(change.getChangeId());
		RemotePushApproval approval = approvalRepository.getAll().get(0);
		approvalService.approve(approval.getApprovalId());

		RemoteBranchRecord first = remoteGitService.push(record.getCommitId(), "origin",
			approval.getApprovalId());
		RemoteBranchRecord second = remoteGitService.push(record.getCommitId(), "origin",
			approval.getApprovalId());

		assertEquals(first.getRemoteId(), second.getRemoteId());
		verify(gitCommandExecutor, times(1)).push("/tmp/repo", "origin", TASK_BRANCH);
	}

	@Test
	void pushRequiresApprovalWhenServiceWired() {
		ChangeSet change = approvedChange(TASK_BRANCH);
		when(gitCommandExecutor.commit(anyString(), anyString())).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\n");

		CommitRecord record = commitService.commit(change.getChangeId());

		assertThrows(IllegalStateException.class,
			() -> remoteGitService.push(record.getCommitId(), "origin"));
		verify(gitCommandExecutor, never()).push(anyString(), anyString(), anyString());
	}

	@Test
	void mainBranchPushFailsClosedWithoutAnyPush() {
		CommitRecord commit = new CommitRecord("commit-main", "change-main", "task-1",
			"workspace-1", "main", "AI change change-main for task task-1", NOW);
		commit.markCommitting();
		commit.markSuccess("deadbeef");
		commitRepository.save(commit);
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\n");

		assertThrows(IllegalStateException.class, () -> remoteGitService.push("commit-main",
			"origin", "approval-any"));
		verify(gitCommandExecutor, never()).push(anyString(), anyString(), anyString());
	}

	@Test
	void requestApprovalReReadsPersistedCommitAndRequiresSuccess() {
		CommitRecord pending = new CommitRecord("commit-pending", "change-1", "task-1",
			"workspace-1", TASK_BRANCH, "AI change change-1 for task task-1", NOW);
		commitRepository.save(pending);
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\n");

		assertThrows(IllegalStateException.class,
			() -> remoteGitService.requestApproval("commit-pending", "origin"));

		CommitRecord success = new CommitRecord("commit-success", "change-2", "task-1",
			"workspace-1", TASK_BRANCH, "AI change change-2 for task task-1", NOW);
		success.markCommitting();
		success.markSuccess("c0ffee");
		commitRepository.save(success);

		RemotePushApproval approval = remoteGitService.requestApproval("commit-success", "origin");
		assertEquals(RemotePushApprovalStatus.PENDING, approval.getStatus());
		assertEquals("c0ffee", approval.getCommitHash());
		assertEquals("commit-success", approval.getCommitId());
	}

	@Test
	void pushSuccessPersistsSuccessInsteadOfPushing() {
		SnapshotRemoteRepository remoteRepository = new SnapshotRemoteRepository();
		RemoteGitService service = new RemoteGitService(remoteRepository, commitService,
			workspaceService, gitCommandExecutor, new AuditService(auditRepository));
		CommitRecord commit = new CommitRecord("commit-1", "change-1", "task-1", "workspace-1",
			"main", "AI change change-1 for task task-1", NOW);
		commit.markCommitting();
		commit.markSuccess("abc123def");
		commitRepository.save(commit);
		when(gitCommandExecutor.push("/tmp/repo", "origin", "main")).thenReturn(true);
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\norigin\tfile:///tmp/bare.git (push)\n");

		RemoteBranchRecord record = service.push("commit-1", null);

		RemoteBranchRecord reloaded = remoteRepository.get(record.getRemoteId());
		assertEquals(RemoteStatus.SUCCESS, reloaded.getStatus());
		assertEquals(List.of(RemoteStatus.PENDING, RemoteStatus.PUSHING, RemoteStatus.SUCCESS),
			remoteRepository.statuses(record.getRemoteId()));
	}

	private ChangeSet approvedChange(String branch) {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1", branch);
		changeService.startReview(change.getChangeId());
		changeService.approve(change.getChangeId(), "user-1");
		return changeService.getChange(change.getChangeId()).orElseThrow();
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	/** CommitRepository that snapshots every save like the PostgreSQL repo. */
	private static final class SnapshotCommitRepository implements CommitRepository {

		private final Map<String, List<CommitRecord>> history = new LinkedHashMap<>();

		@Override
		public synchronized void save(CommitRecord record) {
			history.computeIfAbsent(record.getCommitId(), id -> new ArrayList<>()).add(
				CommitRecord.restore(record.getCommitId(), record.getChangeId(),
					record.getTaskId(), record.getWorkspaceId(), record.getBranch(),
					record.getMessage(), record.getStatus(), record.getGitHash(),
					record.getCreatedAt(), record.getUpdatedAt()));
		}

		@Override
		public synchronized CommitRecord get(String commitId) {
			List<CommitRecord> saved = history.get(commitId);
			return saved == null || saved.isEmpty() ? null : saved.get(saved.size() - 1);
		}

		@Override
		public synchronized List<CommitRecord> getByTaskId(String taskId) {
			List<CommitRecord> result = new ArrayList<>();
			for (List<CommitRecord> saved : history.values()) {
				CommitRecord latest = saved.get(saved.size() - 1);
				if (taskId != null && taskId.equals(latest.getTaskId())) {
					result.add(latest);
				}
			}
			return result;
		}

		@Override
		public synchronized List<CommitRecord> list() {
			List<CommitRecord> result = new ArrayList<>();
			for (List<CommitRecord> saved : history.values()) {
				result.add(saved.get(saved.size() - 1));
			}
			return result;
		}

		synchronized List<CommitStatus> statuses(String commitId) {
			return history.getOrDefault(commitId, List.of()).stream()
				.map(CommitRecord::getStatus).toList();
		}
	}

	/** RemoteRepository that snapshots every save like the PostgreSQL repo. */
	private static final class SnapshotRemoteRepository implements RemoteRepository {

		private final Map<String, List<RemoteBranchRecord>> history = new LinkedHashMap<>();

		@Override
		public synchronized void save(RemoteBranchRecord record) {
			history.computeIfAbsent(record.getRemoteId(), id -> new ArrayList<>()).add(
				RemoteBranchRecord.restore(record.getRemoteId(), record.getTaskId(),
					record.getWorkspaceId(), record.getCommitId(), record.getBranch(),
					record.getRemote(), record.getUrl(), record.getCreatedAt(),
					record.getStatus(), record.getUpdatedAt()));
		}

		@Override
		public synchronized RemoteBranchRecord get(String remoteId) {
			List<RemoteBranchRecord> saved = history.get(remoteId);
			return saved == null || saved.isEmpty() ? null : saved.get(saved.size() - 1);
		}

		@Override
		public synchronized List<RemoteBranchRecord> getByTaskId(String taskId) {
			List<RemoteBranchRecord> result = new ArrayList<>();
			for (List<RemoteBranchRecord> saved : history.values()) {
				RemoteBranchRecord latest = saved.get(saved.size() - 1);
				if (taskId != null && taskId.equals(latest.getTaskId())) {
					result.add(latest);
				}
			}
			return result;
		}

		@Override
		public synchronized List<RemoteBranchRecord> list() {
			List<RemoteBranchRecord> result = new ArrayList<>();
			for (List<RemoteBranchRecord> saved : history.values()) {
				result.add(saved.get(saved.size() - 1));
			}
			return result;
		}

		synchronized List<RemoteStatus> statuses(String remoteId) {
			return history.getOrDefault(remoteId, List.of()).stream()
				.map(RemoteBranchRecord::getStatus).toList();
		}
	}
}
