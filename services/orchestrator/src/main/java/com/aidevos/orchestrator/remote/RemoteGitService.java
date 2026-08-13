package com.aidevos.orchestrator.remote;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * Remote git integration for committed change sets: CommitRecord SUCCESS ->
 * git push of the committed branch -> RemoteBranchRecord + audit. Only pushes;
 * never merges, rebases, deletes branches or creates pull requests.
 */
@Service
public class RemoteGitService {

	private static final String DEFAULT_REMOTE = "origin";

	private final RemoteRepository repository;
	private final CommitService commitService;
	private final WorkspaceService workspaceService;
	private final GitCommandExecutor gitCommandExecutor;
	private final AuditService auditService;
	private volatile QualityGateService qualityGateService;

	public RemoteGitService(RemoteRepository repository, CommitService commitService,
			WorkspaceService workspaceService, GitCommandExecutor gitCommandExecutor,
			AuditService auditService) {
		this.repository = repository;
		this.commitService = commitService;
		this.workspaceService = workspaceService;
		this.gitCommandExecutor = gitCommandExecutor;
		this.auditService = auditService;
	}
	@Autowired(required=false) @Lazy public void setQualityGateService(QualityGateService service){this.qualityGateService=service;}

	/**
	 * Pushes the branch of a SUCCESS commit to the given remote (default
	 * origin) and records the outcome.
	 */
	public RemoteBranchRecord push(String commitId, String remote) {
		CommitRecord commit = commitService.getCommit(commitId)
			.orElseThrow(() -> new ResourceNotFoundException("Commit", commitId));
		if (qualityGateService != null) qualityGateService.assertAllowed(commit.getTaskId());
		if (commit.getStatus() != CommitStatus.SUCCESS) {
			throw new IllegalStateException("Only a SUCCESS commit can be pushed "
				+ "(current: " + commit.getStatus() + ")");
		}
		Workspace workspace = workspaceService.getWorkspace(commit.getWorkspaceId())
			.orElseThrow(() -> new ResourceNotFoundException("Workspace",
				commit.getWorkspaceId()));
		String remoteName = remote == null || remote.isBlank() ? DEFAULT_REMOTE : remote;
		String url = resolveRemoteUrl(remoteName,
			gitCommandExecutor.listRemotes(workspace.getPath()));
		RemoteBranchRecord record = new RemoteBranchRecord("remote-" + UUID.randomUUID(),
			commit.getTaskId(), commit.getWorkspaceId(), commitId, commit.getBranch(),
			remoteName, url, Instant.now());
		repository.save(record);
		String from = record.getStatus().name();
		record.markPushing();
		auditService.remoteEvent(EventType.REMOTE_PUSH_STARTED, record.getTaskId(),
			record.getRemoteId(), record.getCommitId(), from, RemoteStatus.PUSHING.name(),
			"Remote push started", Map.of("remote", remoteName, "branch", commit.getBranch(),
				"url", url));
		try {
			boolean pushed = gitCommandExecutor.push(workspace.getPath(), remoteName,
				commit.getBranch());
			if (!pushed) {
				throw new IllegalStateException("Git push failed to remote: " + remoteName);
			}
			record.markSuccess();
			auditService.remoteEvent(EventType.REMOTE_PUSH_SUCCESS, record.getTaskId(),
				record.getRemoteId(), record.getCommitId(), RemoteStatus.PUSHING.name(),
				RemoteStatus.SUCCESS.name(), "Remote push succeeded: " + remoteName
					+ " -> " + commit.getBranch(),
				Map.of("remote", remoteName, "branch", commit.getBranch(), "url", url));
			return record;
		}
		catch (RuntimeException exception) {
			record.markFailed();
			auditService.remoteEvent(EventType.REMOTE_PUSH_FAILED, record.getTaskId(),
				record.getRemoteId(), record.getCommitId(), RemoteStatus.PUSHING.name(),
				RemoteStatus.FAILED.name(), "Remote push failed: " + message(exception),
				Map.of("remote", remoteName, "branch", commit.getBranch()));
			throw exception;
		}
	}

	public Optional<RemoteBranchRecord> get(String remoteId) {
		if (remoteId == null || remoteId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(remoteId));
	}

	public List<RemoteBranchRecord> getByTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		List<RemoteBranchRecord> result = new ArrayList<>(repository.getByTaskId(taskId));
		result.sort(Comparator.comparing(RemoteBranchRecord::getCreatedAt).reversed());
		return result;
	}

	private String resolveRemoteUrl(String remoteName, String remotesOutput) {
		if (remotesOutput == null || remotesOutput.isBlank()) {
			return "";
		}
		for (String line : remotesOutput.split("\\R")) {
			String[] parts = line.trim().split("\\s+");
			if (parts.length >= 2 && remoteName.equals(parts[0])) {
				return parts[1];
			}
		}
		return "";
	}

	private String message(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
