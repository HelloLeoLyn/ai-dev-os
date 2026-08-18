package com.aidevos.orchestrator.commit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.remote.RemoteGitService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * Git commit flow for approved change sets: APPROVED ChangeSet -> git commit
 * in its workspace -> CommitRecord + ChangeSet COMMITTED. Only commits; never
 * pushes, merges or touches remotes.
 */
@Service
public class CommitService {

	private final CommitRepository repository;
	private final ChangeService changeService;
	private final WorkspaceService workspaceService;
	private final GitCommandExecutor gitCommandExecutor;
	private final AuditService auditService;
	private volatile QualityGateService qualityGateService;
	private volatile ExecutionWorkspaceService executionWorkspaceService;
	private volatile RemoteGitService remoteGitService;

	public CommitService(CommitRepository repository, ChangeService changeService,
			WorkspaceService workspaceService, GitCommandExecutor gitCommandExecutor,
			AuditService auditService) {
		this.repository = repository;
		this.changeService = changeService;
		this.workspaceService = workspaceService;
		this.gitCommandExecutor = gitCommandExecutor;
		this.auditService = auditService;
	}
	@Autowired(required=false) @Lazy public void setQualityGateService(QualityGateService service){this.qualityGateService=service;}
	@Autowired(required=false) public void setExecutionWorkspaceService(ExecutionWorkspaceService service){this.executionWorkspaceService=service;}
	@Autowired(required=false) @Lazy public void setRemoteGitService(RemoteGitService service){this.remoteGitService=service;}

	/**
	 * Commits the workspace changes behind an APPROVED change set and records
	 * the resulting git hash. On success the change set becomes COMMITTED.
	 */
	public CommitRecord commit(String changeId) {
		ChangeSet change = changeService.getChange(changeId)
			.orElseThrow(() -> new ResourceNotFoundException("Change", changeId));
		if (qualityGateService != null) qualityGateService.assertAllowed(change.getTaskId());
		if (change.getStatus() != ChangeStatus.APPROVED) {
			throw new IllegalStateException("Only an APPROVED change can be committed "
				+ "(current: " + change.getStatus() + ")");
		}
		Workspace workspace = workspaceService.getWorkspace(change.getWorkspaceId()).orElse(null);
		if (workspace == null && executionWorkspaceService != null) {
			ExecutionWorkspace execution = executionWorkspaceService.findByTaskId(change.getTaskId());
			if (execution != null && execution.getId().equals(change.getWorkspaceId())) {
				workspace = new Workspace(execution.getId(), execution.getProjectId(),
					execution.getExecutionWorkspace(), execution.getExecutionBranch(),
					com.aidevos.orchestrator.workspace.WorkspaceStatus.READY,
					execution.getCreatedAt(), execution.getUpdatedAt());
			}
		}
		if (workspace == null) throw new ResourceNotFoundException("Workspace", change.getWorkspaceId());
		String message = "AI change " + changeId + " for task " + change.getTaskId();
		CommitRecord record = new CommitRecord("commit-" + UUID.randomUUID(), changeId,
			change.getTaskId(), change.getWorkspaceId(), change.getBranch(), message,
			Instant.now());
		repository.save(record);
		String from = record.getStatus().name();
		record.markCommitting();
		auditService.commitEvent(EventType.COMMIT_REQUESTED, record.getTaskId(),
			record.getCommitId(), record.getChangeId(), from, CommitStatus.COMMITTING.name(),
			"Commit requested", Map.of("workspaceId", change.getWorkspaceId()));
		auditService.commitEvent(EventType.COMMIT_STARTED, record.getTaskId(),
			record.getCommitId(), record.getChangeId(), from, CommitStatus.COMMITTING.name(),
			"Commit started", Map.of("workspaceId", change.getWorkspaceId()));
		try {
			String hash = gitCommandExecutor.commit(workspace.getPath(), message);
			if (hash == null || hash.isBlank()) {
				throw new IllegalStateException("Git commit failed in workspace: "
					+ workspace.getPath());
			}
			record.markSuccess(hash);
			changeService.markCommitted(changeId);
			if (remoteGitService != null && gitCommandExecutor.listRemotes(workspace.getPath())
					.lines().anyMatch(line -> line.trim().startsWith("origin "))) {
				remoteGitService.requestApproval(record.getCommitId(), "origin");
			}
			auditService.commitEvent(EventType.COMMIT_SUCCESS, record.getTaskId(),
				record.getCommitId(), record.getChangeId(), CommitStatus.COMMITTING.name(),
				CommitStatus.SUCCESS.name(), "Commit succeeded: " + hash,
				Map.of("gitHash", hash));
			return record;
		}
		catch (RuntimeException exception) {
			record.markFailed();
			auditService.commitEvent(EventType.COMMIT_FAILED, record.getTaskId(),
				record.getCommitId(), record.getChangeId(), CommitStatus.COMMITTING.name(),
				CommitStatus.FAILED.name(), "Commit failed: " + message(exception),
				Map.of());
			throw exception;
		}
	}

	public Optional<CommitRecord> getCommit(String commitId) {
		if (commitId == null || commitId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(commitId));
	}

	public List<CommitRecord> getCommitsByTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		List<CommitRecord> result = new ArrayList<>(repository.getByTaskId(taskId));
		result.sort(Comparator.comparing(CommitRecord::getCreatedAt).reversed());
		return result;
	}

	private String message(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
