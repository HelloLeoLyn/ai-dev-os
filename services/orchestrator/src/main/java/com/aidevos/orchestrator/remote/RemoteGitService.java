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
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.aidevos.orchestrator.feedback.PrFeedbackService;

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
	private final RemotePushApprovalService pushApprovals;
	private volatile ExecutionWorkspaceService executionWorkspaces;
	private volatile PrFeedbackService feedbackService;
	private volatile QualityGateService qualityGateService;

	public RemoteGitService(RemoteRepository repository, CommitService commitService,
			WorkspaceService workspaceService, GitCommandExecutor gitCommandExecutor,
			AuditService auditService) {
		this(repository, commitService, workspaceService, gitCommandExecutor, auditService, null);
	}
	@Autowired
	public RemoteGitService(RemoteRepository repository, CommitService commitService,
			WorkspaceService workspaceService, GitCommandExecutor gitCommandExecutor,
			AuditService auditService, RemotePushApprovalService pushApprovals) {
		this.repository = repository;
		this.commitService = commitService;
		this.workspaceService = workspaceService;
		this.gitCommandExecutor = gitCommandExecutor;
		this.auditService = auditService;
		this.pushApprovals = pushApprovals;
	}
	@Autowired(required=false) public void setExecutionWorkspaceService(ExecutionWorkspaceService service){this.executionWorkspaces=service;}
	@Autowired(required=false) @Lazy public void setFeedbackService(PrFeedbackService service){this.feedbackService=service;}
	public boolean requiresRemotePushApproval(){return pushApprovals != null;}
	@Autowired(required=false) @Lazy public void setQualityGateService(QualityGateService service){this.qualityGateService=service;}

	/**
	 * Pushes the branch of a SUCCESS commit to the given remote (default
	 * origin) and records the outcome.
	 */
	public RemoteBranchRecord push(String commitId, String remote) {
		return push(commitId, remote, null);
	}

	public RemoteBranchRecord push(String commitId, String remote, String approvalId) {
		CommitRecord commit = commitService.getCommit(commitId)
			.orElseThrow(() -> new ResourceNotFoundException("Commit", commitId));
		if (qualityGateService != null) qualityGateService.assertAllowed(commit.getTaskId());
		if (commit.getStatus() != CommitStatus.SUCCESS) {
			throw new IllegalStateException("Only a SUCCESS commit can be pushed "
				+ "(current: " + commit.getStatus() + ")");
		}
		Workspace workspace = resolveWorkspace(commit);
		String remoteName = remote == null || remote.isBlank() ? DEFAULT_REMOTE : remote;
		String expectedBranch = taskBranch(commit.getTaskId());
		RemoteBranchRecord alreadyPushed = successfulPush(commit.getTaskId(), commitId,
			remoteName, commit.getBranch());
		if (alreadyPushed != null) {
			return alreadyPushed;
		}
		if (pushApprovals != null && !expectedBranch.equals(commit.getBranch())) throw new IllegalStateException("Commit branch is not a task branch");
		if (pushApprovals != null) {
			if (approvalId == null || approvalId.isBlank()) throw new IllegalStateException("Remote push approval is required");
			RemotePushApproval approval=pushApprovals.get(approvalId);
			if (approval == null || approval.getStatus() != RemotePushApprovalStatus.APPROVED) throw new IllegalStateException("Remote push approval is not approved");
			if (!commit.getTaskId().equals(approval.getTaskId()) || !commit.getWorkspaceId().equals(approval.getExecutionWorkspaceId()) || !commitId.equals(approval.getCommitId()) || !remoteName.equals(approval.getRemote()) || !expectedBranch.equals(approval.getExecutionBranch()) || !approval.getTargetRef().equals("refs/heads/"+expectedBranch) || !commit.getGitHash().equals(approval.getCommitHash())) throw new IllegalStateException("Remote push approval binding mismatch");
			// consumed only after all workspace and remote preconditions pass
		}
		if (pushApprovals != null && executionWorkspaces != null) {
			ExecutionWorkspace execution=executionWorkspaces.findByTaskId(commit.getTaskId());
			if (execution == null || !commit.getWorkspaceId().equals(execution.getId()) || !expectedBranch.equals(execution.getExecutionBranch()) || !expectedBranch.equals(gitCommandExecutor.status(execution.getExecutionWorkspace()).getBranch())) throw new IllegalStateException("Execution workspace lineage is invalid");
		}
		String url = resolveRemoteUrl(remoteName,
			gitCommandExecutor.listRemotes(workspace.getPath()));
		if (!isRegisteredRemote(remoteName, gitCommandExecutor.listRemotes(workspace.getPath()))) throw new IllegalStateException("Remote is not registered: " + remoteName);
		if (pushApprovals != null && !pushApprovals.consume(pushApprovals.get(approvalId))) throw new IllegalStateException("Remote push approval cannot be consumed");
		RemoteBranchRecord record = new RemoteBranchRecord("remote-" + UUID.randomUUID(),
			commit.getTaskId(), commit.getWorkspaceId(), commitId, commit.getBranch(),
			remoteName, url, Instant.now());
		repository.save(record);
		String from = record.getStatus().name();
		record.markPushing();
		repository.save(record);
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
			repository.save(record);
			 auditService.remoteEvent(EventType.REMOTE_PUSH_SUCCESS, record.getTaskId(),
				record.getRemoteId(), record.getCommitId(), RemoteStatus.PUSHING.name(),
				RemoteStatus.SUCCESS.name(), "Remote push succeeded: " + remoteName
					+ " -> " + commit.getBranch(),
				Map.of("remote", remoteName, "branch", commit.getBranch(), "url", url));
			if (feedbackService != null) feedbackService.onRemotePushSucceeded(record);
			return record;
		}
		catch (RuntimeException exception) {
			record.markFailed();
			repository.save(record);
			auditService.remoteEvent(EventType.REMOTE_PUSH_FAILED, record.getTaskId(),
				record.getRemoteId(), record.getCommitId(), RemoteStatus.PUSHING.name(),
				RemoteStatus.FAILED.name(), "Remote push failed: " + message(exception),
				Map.of("remote", remoteName, "branch", commit.getBranch()));
			throw exception;
		}
	}

	public RemotePushApproval requestApproval(String commitId, String remote) {
		CommitRecord commit=commitService.getCommit(commitId).orElseThrow(() -> new ResourceNotFoundException("Commit",commitId));
		if (commit.getStatus()!=CommitStatus.SUCCESS) throw new IllegalStateException("Only a SUCCESS commit can request remote push");
		String branch=taskBranch(commit.getTaskId()); String remoteName=remote==null||remote.isBlank()?DEFAULT_REMOTE:remote;
		Workspace workspace=resolveWorkspace(commit);
		if (pushApprovals==null) throw new IllegalStateException("Remote push approval service unavailable");
		if (!branch.equals(commit.getBranch()) || !isRegisteredRemote(remoteName,gitCommandExecutor.listRemotes(workspace.getPath()))) throw new IllegalStateException("Remote push binding is invalid");
		return pushApprovals.request(commit.getTaskId(),commit.getWorkspaceId(),branch,commit.getCommitId(),commit.getGitHash(),remoteName);
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
	private RemoteBranchRecord successfulPush(String taskId, String commitId, String remote,
			String branch) {
		for (RemoteBranchRecord record : repository.getByTaskId(taskId)) {
			if (record.getStatus() == RemoteStatus.SUCCESS && commitId.equals(record.getCommitId())
					&& remote.equals(record.getRemote()) && branch.equals(record.getBranch())) {
				return record;
			}
		}
		return null;
	}
	private Workspace resolveWorkspace(CommitRecord commit) {
		Workspace workspace = workspaceService.getWorkspace(commit.getWorkspaceId()).orElse(null);
		if (workspace != null) {
			return workspace;
		}
		if (executionWorkspaces != null) {
			ExecutionWorkspace execution = executionWorkspaces.findByTaskId(commit.getTaskId());
			if (execution != null && commit.getWorkspaceId().equals(execution.getId())) {
				return new Workspace(execution.getId(), execution.getProjectId(),
					execution.getExecutionWorkspace(), execution.getExecutionBranch(),
					WorkspaceStatus.READY, execution.getCreatedAt(), execution.getUpdatedAt());
			}
		}
		throw new ResourceNotFoundException("Workspace", commit.getWorkspaceId());
	}

	public static boolean isRegisteredRemote(String remote,String output){if(output==null)return false;for(String line:output.split("\\R")){String[] p=line.trim().split("\\s+");if(p.length>=2&&remote.equals(p[0]))return true;}return false;}
	private String taskBranch(String taskId){String safe=taskId==null?"":taskId.replaceAll("[^A-Za-z0-9._-]","_");if(safe.isBlank()||safe.equals(".")||safe.equals(".."))throw new IllegalStateException("Invalid task id");return "ai-dev-os/task/"+safe;}
}
