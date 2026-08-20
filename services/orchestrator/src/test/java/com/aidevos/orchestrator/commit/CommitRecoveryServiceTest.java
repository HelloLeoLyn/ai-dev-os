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
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import com.aidevos.orchestrator.remote.InMemoryRemotePushApprovalRepository;
import com.aidevos.orchestrator.remote.InMemoryRemoteRepository;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApproval;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.remote.RemotePushApprovalStatus;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recovery of historical PENDING commits: only trusted persisted evidence may
 * flip a commit to SUCCESS, the real Execution Workspace HEAD is used as the
 * git hash, the Remote Push Approval flow is re-entered and every mismatch
 * fails closed. Recovery never re-runs git commit and never pushes.
 */
class CommitRecoveryServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final String TASK = "task-1";
	private static final String BRANCH = "ai-dev-os/task/task-1";
	private static final String WORKSPACE_ID = "exec-ws-1";
	private static final String WORKSPACE_PATH = "/tmp/exec";
	private static final String HEAD = "320131b1fce88d224333aa72a08273250fde0dfd";

	private SnapshotCommitRepository commitRepository;
	private InMemoryAuditRepository auditRepository;
	private InMemoryRemotePushApprovalRepository approvalRepository;
	private ExecutionWorkspaceService executionWorkspaceService;
	private WorkspaceService workspaceService;
	private GitCommandExecutor gitCommandExecutor;
	private ChangeService changeService;
	private CommitRecoveryService recoveryService;
	private CommitService commitService;

	@BeforeEach
	void setUp() {
		commitRepository = new SnapshotCommitRepository();
		auditRepository = new InMemoryAuditRepository();
		approvalRepository = new InMemoryRemotePushApprovalRepository();
		AuditService auditService = new AuditService(auditRepository);
		workspaceService = mock(WorkspaceService.class);
		executionWorkspaceService = mock(ExecutionWorkspaceService.class);
		gitCommandExecutor = mock(GitCommandExecutor.class);
		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		commitService = new CommitService(commitRepository, changeService, workspaceService,
			gitCommandExecutor, auditService);
		RemotePushApprovalService approvalService = new RemotePushApprovalService(
			approvalRepository, auditService);
		RemoteGitService remoteGitService = new RemoteGitService(new InMemoryRemoteRepository(),
			commitService, workspaceService, gitCommandExecutor, auditService, approvalService);
		remoteGitService.setExecutionWorkspaceService(executionWorkspaceService);
		recoveryService = new CommitRecoveryService(commitRepository, changeService,
			executionWorkspaceService, gitCommandExecutor, auditRepository, auditService);
		recoveryService.setRemoteGitService(remoteGitService);

		ExecutionWorkspace execution = new ExecutionWorkspace(WORKSPACE_ID, TASK, "project-a",
			"source-ws-1", "/source/repo", WORKSPACE_PATH, "GIT_WORKTREE", BRANCH,
			ExecutionWorkspaceStatus.COMPLETED, "base-revision", NOW, NOW);
		when(executionWorkspaceService.findByTaskId(TASK)).thenReturn(execution);
		// Execution workspaces are not registered in WorkspaceService; the
		// remote flow must resolve them through the execution workspace store.
		when(workspaceService.getWorkspace(WORKSPACE_ID)).thenReturn(Optional.empty());
		when(gitCommandExecutor.currentCommitHash(WORKSPACE_PATH)).thenReturn(HEAD);
		when(gitCommandExecutor.status(WORKSPACE_PATH)).thenReturn(new GitStatus(BRANCH, 0, 0, 0));
		when(gitCommandExecutor.listRemotes(WORKSPACE_PATH)).thenReturn(
			"origin\tfile:///tmp/bare.git (fetch)\norigin\tfile:///tmp/bare.git (push)\n");
	}

	@Test
	void recoversHistoricalPendingCommitToSuccessWithRealHead() {
		CommitRecord commit = historicalPendingCommit();

		CommitRecord recovered = recoveryService.recover(TASK, commit.getCommitId());

		assertEquals(CommitStatus.SUCCESS, recovered.getStatus());
		assertEquals(HEAD, recovered.getGitHash());
		CommitRecord reloaded = commitRepository.get(commit.getCommitId());
		assertEquals(CommitStatus.SUCCESS, reloaded.getStatus());
		assertEquals(HEAD, reloaded.getGitHash());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.COMMIT_RECOVERED
			&& TASK.equals(event.taskId())
			&& HEAD.equals(event.metadata().get("gitHash"))));
	}

	@Test
	void recoversAndCreatesPendingRemotePushApproval() {
		CommitRecord commit = historicalPendingCommit();

		recoveryService.recover(TASK, commit.getCommitId());

		assertEquals(1, approvalRepository.getAll().size());
		RemotePushApproval approval = approvalRepository.getAll().get(0);
		assertEquals(RemotePushApprovalStatus.PENDING, approval.getStatus());
		assertEquals(commit.getCommitId(), approval.getCommitId());
		assertEquals(HEAD, approval.getCommitHash());
		assertEquals("origin", approval.getRemote());
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.REMOTE_PUSH_APPROVAL_REQUESTED && TASK.equals(event.taskId())));
	}

	@Test
	void recoveryIsIdempotentAndNeverRepeatsCommitOrApproval() {
		CommitRecord commit = historicalPendingCommit();

		recoveryService.recover(TASK, commit.getCommitId());
		CommitRecord second = recoveryService.recover(TASK, commit.getCommitId());

		assertEquals(CommitStatus.SUCCESS, second.getStatus());
		assertEquals(1, approvalRepository.getAll().size());
		verify(gitCommandExecutor, never()).commit(any(), any());
		verify(gitCommandExecutor, never()).push(any(), any(), any());
	}

	@Test
	void recoveryWithoutOriginPersistsSuccessButSkipsApproval() {
		CommitRecord commit = historicalPendingCommit();
		when(gitCommandExecutor.listRemotes(WORKSPACE_PATH)).thenReturn("");

		CommitRecord recovered = recoveryService.recover(TASK, commit.getCommitId());

		assertEquals(CommitStatus.SUCCESS, recovered.getStatus());
		assertEquals(HEAD, recovered.getGitHash());
		assertTrue(approvalRepository.getAll().isEmpty());
	}

	@Test
	void failsClosedWhenExecutionWorkspaceMissing() {
		CommitRecord commit = historicalPendingCommit();
		when(executionWorkspaceService.findByTaskId(TASK)).thenReturn(null);

		assertThrows(IllegalStateException.class,
			() -> recoveryService.recover(TASK, commit.getCommitId()));
		assertEquals(CommitStatus.PENDING, commitRepository.get(commit.getCommitId()).getStatus());
	}

	@Test
	void failsClosedWhenChangeSetNotCommitted() {
		CommitRecord commit = historicalPendingCommit();
		InMemoryChangeRepository changes = new InMemoryChangeRepository();
		ChangeService approvedOnly = new ChangeService(changes, workspaceService,
			new AuditService(auditRepository));
		ChangeSet change = new ChangeSet(commit.getChangeId(), TASK, WORKSPACE_ID, "project-a",
			"exec-1", BRANCH, "diff", "1 file changed", 1, 1, 0, 1, 0, 0, NOW);
		approvedOnly.save(change);
		approvedOnly.startReview(commit.getChangeId());
		approvedOnly.approve(commit.getChangeId(), "user-1");
		CommitRecoveryService service = new CommitRecoveryService(commitRepository,
			approvedOnly, executionWorkspaceService, gitCommandExecutor, auditRepository,
			new AuditService(auditRepository));

		assertThrows(IllegalStateException.class,
			() -> service.recover(TASK, commit.getCommitId()));
		assertEquals(CommitStatus.PENDING, commitRepository.get(commit.getCommitId()).getStatus());
	}

	@Test
	void failsClosedWhenGitEvidenceMismatches() {
		CommitRecord commit = historicalPendingCommit();
		when(gitCommandExecutor.currentCommitHash(WORKSPACE_PATH)).thenReturn("different-hash");

		assertThrows(IllegalStateException.class,
			() -> recoveryService.recover(TASK, commit.getCommitId()));
		assertEquals(CommitStatus.PENDING, commitRepository.get(commit.getCommitId()).getStatus());
	}

	@Test
	void failsClosedWhenCommitDoesNotBelongToTask() {
		CommitRecord commit = historicalPendingCommit();

		assertThrows(IllegalStateException.class,
			() -> recoveryService.recover("other-task", commit.getCommitId()));
		assertEquals(CommitStatus.PENDING, commitRepository.get(commit.getCommitId()).getStatus());
	}

	@Test
	void failsClosedWhenCommitSuccessAuditEvidenceMissing() {
		ChangeSet change = committedChange("change-no-evidence");
		CommitRecord orphan = new CommitRecord("commit-no-evidence", change.getChangeId(), TASK,
			WORKSPACE_ID, BRANCH, "AI change " + change.getChangeId() + " for task " + TASK, NOW);
		commitRepository.save(orphan);
		// No COMMIT_SUCCESS audit event was ever recorded for this commit.

		assertThrows(IllegalStateException.class,
			() -> recoveryService.recover(TASK, orphan.getCommitId()));
		assertEquals(CommitStatus.PENDING,
			commitRepository.get(orphan.getCommitId()).getStatus());
	}

	@Test
	void failsClosedWhenCommitAlreadyFailed() {
		CommitRecord commit = historicalPendingCommit();
		CommitRecord failed = commitRepository.get(commit.getCommitId());
		failed.markCommitting();
		failed.markFailed();
		commitRepository.save(failed);

		assertThrows(IllegalStateException.class,
			() -> recoveryService.recover(TASK, commit.getCommitId()));
	}

	@Test
	void failsClosedWhenBranchDoesNotMatchCommitRecord() {
		CommitRecord commit = historicalPendingCommit();
		when(gitCommandExecutor.status(WORKSPACE_PATH)).thenReturn(new GitStatus("main", 0, 0, 0));

		assertThrows(IllegalStateException.class,
			() -> recoveryService.recover(TASK, commit.getCommitId()));
		assertEquals(CommitStatus.PENDING, commitRepository.get(commit.getCommitId()).getStatus());
	}

	private CommitRecord historicalPendingCommit() {
		ChangeSet change = committedChange("change-historical");
		CommitRecord commit = new CommitRecord("commit-historical", change.getChangeId(), TASK,
			WORKSPACE_ID, BRANCH, "AI change " + change.getChangeId() + " for task " + TASK, NOW);
		commitRepository.save(commit);
		auditServiceOf().commitEvent(EventType.COMMIT_SUCCESS, TASK, commit.getCommitId(),
			change.getChangeId(), CommitStatus.COMMITTING.name(), CommitStatus.SUCCESS.name(),
			"Commit succeeded: " + HEAD, Map.of("gitHash", HEAD));
		return commit;
	}

	private ChangeSet committedChange(String changeId) {
		ChangeSet change = new ChangeSet(changeId, TASK, WORKSPACE_ID, "project-a", "exec-1",
			BRANCH, "diff", "1 file changed", 1, 1, 0, 1, 0, 0, NOW);
		changeService.save(change);
		changeService.startReview(changeId);
		changeService.approve(changeId, "user-1");
		changeService.markCommitted(changeId);
		return changeService.getChange(changeId).orElseThrow();
	}

	private AuditService auditServiceOf() {
		return new AuditService(auditRepository);
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
	}
}
