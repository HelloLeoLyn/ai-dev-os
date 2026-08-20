package com.aidevos.orchestrator.commit;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditRepository;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApproval;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Recovers CommitRecords that were left in PENDING by the historical
 * persistence gap even though the git commit really succeeded. Recovery never
 * re-runs git commit, never touches files or remotes and never bypasses the
 * Remote Push Approval flow: it only re-applies the persisted SUCCESS state
 * backed by trusted evidence (ChangeSet COMMITTED + Execution Workspace HEAD +
 * COMMIT_SUCCESS audit hash) and then re-enters the normal approval request
 * path. It is idempotent and fails closed on any evidence mismatch.
 */
@Service
public class CommitRecoveryService {

	private final CommitRepository commitRepository;
	private final ChangeService changeService;
	private final ExecutionWorkspaceService executionWorkspaceService;
	private final GitCommandExecutor gitCommandExecutor;
	private final AuditRepository auditRepository;
	private final AuditService auditService;
	private volatile RemoteGitService remoteGitService;

	public CommitRecoveryService(CommitRepository commitRepository, ChangeService changeService,
			ExecutionWorkspaceService executionWorkspaceService, GitCommandExecutor gitCommandExecutor,
			AuditRepository auditRepository, AuditService auditService) {
		this.commitRepository = commitRepository;
		this.changeService = changeService;
		this.executionWorkspaceService = executionWorkspaceService;
		this.gitCommandExecutor = gitCommandExecutor;
		this.auditRepository = auditRepository;
		this.auditService = auditService;
	}

	@Autowired(required = false) @Lazy
	public void setRemoteGitService(RemoteGitService service) {
		this.remoteGitService = service;
	}

	/**
	 * Recovers a PENDING commit from trusted persisted evidence. Idempotent:
	 * an already SUCCESS commit is returned as-is (with the approval flow
	 * re-entered only when the approval is still missing). Fails closed unless
	 * every evidence check passes.
	 */
	public CommitRecord recover(String taskId, String commitId) {
		CommitRecord commit = commitRepository.get(commitId);
		if (commit == null) {
			throw new ResourceNotFoundException("Commit", commitId);
		}
		if (!taskId.equals(commit.getTaskId())) {
			throw new IllegalStateException("Commit " + commitId + " does not belong to task "
				+ taskId);
		}
		if (commit.getStatus() == CommitStatus.SUCCESS) {
			ensureApproval(commit);
			return commit;
		}
		if (commit.getStatus() != CommitStatus.PENDING) {
			throw new IllegalStateException("Commit is not recoverable (status: "
				+ commit.getStatus() + ")");
		}

		ChangeSet change = changeService.getChange(commit.getChangeId())
			.orElseThrow(() -> new IllegalStateException("ChangeSet "
				+ commit.getChangeId() + " not found"));
		if (!taskId.equals(change.getTaskId())
				|| !commit.getChangeId().equals(change.getChangeId())) {
			throw new IllegalStateException("Commit does not belong to the ChangeSet of task "
				+ taskId);
		}
		if (change.getStatus() != ChangeStatus.COMMITTED) {
			throw new IllegalStateException("ChangeSet is not COMMITTED (status: "
				+ change.getStatus() + ")");
		}

		ExecutionWorkspace execution = executionWorkspaceService.findByTaskId(taskId);
		if (execution == null) {
			throw new IllegalStateException("Execution workspace not found for task " + taskId);
		}
		if (!commit.getWorkspaceId().equals(execution.getId())) {
			throw new IllegalStateException("Commit workspace does not match the execution workspace");
		}
		String path = execution.getExecutionWorkspace();
		if (path == null || path.isBlank()) {
			throw new IllegalStateException("Execution workspace has no workspace path");
		}
		String head = gitCommandExecutor.currentCommitHash(path);
		if (head == null || head.isBlank()) {
			throw new IllegalStateException("Execution workspace has no readable git HEAD");
		}
		GitStatus status = gitCommandExecutor.status(path);
		String branch = status == null ? null : status.getBranch();
		if (branch == null || branch.isBlank() || !branch.equals(commit.getBranch())) {
			throw new IllegalStateException("Execution workspace branch does not match the commit record");
		}
		String evidenceHash = committedHashEvidence(commit, taskId);
		if (!head.equals(evidenceHash)) {
			throw new IllegalStateException("Git HEAD does not match the recorded commit evidence");
		}

		commit.markCommitting();
		commitRepository.save(commit);
		commit.markSuccess(head);
		commitRepository.save(commit);
		auditService.commitEvent(EventType.COMMIT_RECOVERED, taskId, commit.getCommitId(),
			commit.getChangeId(), CommitStatus.PENDING.name(), CommitStatus.SUCCESS.name(),
			"Commit state recovered from persisted evidence: " + head,
			Map.of("gitHash", head, "recoveredFrom", "EXECUTION_WORKSPACE_HEAD"));
		ensureApproval(commit);
		return commit;
	}

	/**
	 * Re-enters the normal Remote Push Approval flow when the workspace has a
	 * trusted origin. requestApproval is idempotent: it re-reads the persisted
	 * SUCCESS commit from the repository and reuses an existing PENDING or
	 * APPROVED approval instead of duplicating it.
	 */
	private void ensureApproval(CommitRecord commit) {
		if (remoteGitService == null) {
			throw new IllegalStateException("Remote push approval service unavailable");
		}
		String path = workspacePath(commit);
		if (path == null
				|| !RemoteGitService.isRegisteredRemote("origin",
					gitCommandExecutor.listRemotes(path))) {
			return;
		}
		RemotePushApproval approval = remoteGitService.requestApproval(commit.getCommitId(),
			"origin");
		if (approval == null) {
			throw new IllegalStateException("Remote push approval was not created for commit "
				+ commit.getCommitId());
		}
	}

	private String workspacePath(CommitRecord commit) {
		ExecutionWorkspace execution = executionWorkspaceService.findByTaskId(commit.getTaskId());
		if (execution != null && commit.getWorkspaceId().equals(execution.getId())) {
			return execution.getExecutionWorkspace();
		}
		return null;
	}

	/**
	 * Returns the git hash recorded by the COMMIT_SUCCESS audit event for this
	 * commit, or fails closed when no trustworthy evidence exists.
	 */
	private String committedHashEvidence(CommitRecord commit, String taskId) {
		EventQuery query = new EventQuery("commit", commit.getCommitId(), null, null, null,
			null, null, null, null, null, Set.of(EventType.COMMIT_SUCCESS), null, null, 0,
			EventQuery.MAX_LIMIT);
		List<EventRecord> evidence = auditRepository.query(query).stream()
			.filter(event -> taskId.equals(event.taskId()))
			.toList();
		if (evidence.isEmpty()) {
			throw new IllegalStateException("No COMMIT_SUCCESS audit evidence for commit "
				+ commit.getCommitId());
		}
		EventRecord latest = evidence.stream().max(Comparator
			.comparing(EventRecord::occurredAt)
			.thenComparingLong(EventRecord::sequence)
			.thenComparing(EventRecord::id)).orElseThrow();
		Object hash = latest.metadata().get("gitHash");
		if (!(hash instanceof String value) || value.isBlank()) {
			throw new IllegalStateException("COMMIT_SUCCESS audit evidence is missing gitHash");
		}
		return value;
	}
}
