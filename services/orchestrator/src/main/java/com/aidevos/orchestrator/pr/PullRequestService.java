package com.aidevos.orchestrator.pr;

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
import com.aidevos.orchestrator.pr.provider.GitProvider;
import com.aidevos.orchestrator.pr.provider.GitProviderProperties;
import com.aidevos.orchestrator.pr.provider.GitPullRequestRequest;
import com.aidevos.orchestrator.pr.provider.GitPullRequestResult;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemoteStatus;
import org.springframework.stereotype.Service;

/**
 * Pull request management for pushed commits: CommitRecord SUCCESS + remote
 * push SUCCESS -> PR record OPEN -> MERGED | CLOSED via the configured git
 * provider (mock/github/gitlab). This phase only manages state; it never
 * merges, pulls or modifies code.
 */
@Service
public class PullRequestService {

	private static final String DEFAULT_TARGET_BRANCH = "main";

	private final PullRequestRepository repository;
	private final CommitService commitService;
	private final RemoteGitService remoteGitService;
	private final GitProvider provider;
	private final GitProviderProperties properties;
	private final AuditService auditService;

	public PullRequestService(PullRequestRepository repository, CommitService commitService,
			RemoteGitService remoteGitService, GitProvider provider,
			GitProviderProperties properties, AuditService auditService) {
		this.repository = repository;
		this.commitService = commitService;
		this.remoteGitService = remoteGitService;
		this.provider = provider;
		this.properties = properties;
		this.auditService = auditService;
	}

	/**
	 * Opens a pull request for a commit that was both committed and pushed
	 * successfully. The provider URL and external id are stored on the
	 * record; on failure the record moves to FAILED and a PR_FAILED audit
	 * event is emitted.
	 */
	public PullRequestRecord createPullRequest(String commitId,
			PullRequestCreateRequest request) {
		CommitRecord commit = commitService.getCommit(commitId)
			.orElseThrow(() -> new ResourceNotFoundException("Commit", commitId));
		if (commit.getStatus() != CommitStatus.SUCCESS) {
			throw new IllegalStateException("Only a SUCCESS commit can open a pull request "
				+ "(current: " + commit.getStatus() + ")");
		}
		RemoteBranchRecord push = findSuccessfulPush(commit);
		if (push == null) {
			throw new IllegalStateException("No SUCCESS remote push found for commit: "
				+ commitId);
		}
		String targetBranch = request == null || request.targetBranch() == null
			|| request.targetBranch().isBlank() ? DEFAULT_TARGET_BRANCH
			: request.targetBranch();
		String title = request == null || request.title() == null || request.title().isBlank()
			? "AI change for task " + commit.getTaskId() : request.title();
		String description = request == null || request.description() == null
			|| request.description().isBlank()
			? "Auto-generated pull request by AI Dev OS" : request.description();
		String remoteUrl = value(push.getUrl());
		PullRequestRecord record = new PullRequestRecord("pr-" + UUID.randomUUID(),
			commit.getTaskId(), commitId, push.getRemoteId(), push.getBranch(), targetBranch,
			title, description, null, Instant.now());
		repository.save(record);
		auditService.prEvent(EventType.PR_CREATED, record.getTaskId(),
			record.getPullRequestId(), commitId, PullRequestStatus.CREATED.name(),
			PullRequestStatus.CREATED.name(), "Pull request record created",
			Map.of("provider", providerName(), "remoteUrl", remoteUrl,
				"branch", record.getBranch(), "targetBranch", targetBranch));
		try {
			GitPullRequestResult result = provider.createPullRequest(
				new GitPullRequestRequest(record.getPullRequestId(), record.getBranch(),
					targetBranch, title, description));
			record.updateUrl(result.url());
			record.updateExternalId(result.externalId());
			record.markOpened();
			auditService.prEvent(EventType.PR_OPENED, record.getTaskId(),
				record.getPullRequestId(), commitId, PullRequestStatus.CREATED.name(),
				PullRequestStatus.OPEN.name(), "Pull request opened: " + result.url(),
				Map.of("provider", providerName(), "remoteUrl", remoteUrl,
					"externalId", value(result.externalId()), "url", value(result.url()),
					"branch", record.getBranch(), "targetBranch", targetBranch));
			return record;
		}
		catch (RuntimeException exception) {
			record.markFailed();
			auditService.prEvent(EventType.PR_FAILED, record.getTaskId(),
				record.getPullRequestId(), commitId, PullRequestStatus.CREATED.name(),
				PullRequestStatus.FAILED.name(),
				"Pull request creation failed: " + message(exception),
				Map.of("provider", providerName(), "remoteUrl", remoteUrl,
					"externalId", value(record.getExternalId())));
			throw exception;
		}
	}

	/**
	 * Closes an OPEN pull request through the provider and moves the record to
	 * CLOSED. No code is changed and no branch is deleted.
	 */
	public PullRequestRecord close(String pullRequestId) {
		PullRequestRecord record = requireRecord(pullRequestId);
		String from = record.getStatus().name();
		try {
			provider.closePullRequest(value(record.getExternalId()));
			record.markClosed();
			auditService.prEvent(EventType.PR_CLOSED, record.getTaskId(),
				record.getPullRequestId(), record.getCommitId(), from,
				PullRequestStatus.CLOSED.name(), "Pull request closed",
				Map.of("provider", providerName(), "remoteUrl", remoteUrl(record),
					"externalId", value(record.getExternalId()), "url", value(record.getUrl())));
			return record;
		}
		catch (RuntimeException exception) {
			record.markFailed();
			auditService.prEvent(EventType.PR_FAILED, record.getTaskId(),
				record.getPullRequestId(), record.getCommitId(), from,
				PullRequestStatus.FAILED.name(),
				"Pull request close failed: " + message(exception),
				Map.of("provider", providerName(), "remoteUrl", remoteUrl(record),
					"externalId", value(record.getExternalId())));
			throw exception;
		}
	}

	/**
	 * Marks an OPEN pull request as MERGED. This phase only changes state; no
	 * real git merge, pull or code modification is performed.
	 */
	public PullRequestRecord merge(String pullRequestId) {
		PullRequestRecord record = requireRecord(pullRequestId);
		String from = record.getStatus().name();
		try {
			provider.mergePullRequest(value(record.getExternalId()));
			record.markMerged();
			auditService.prEvent(EventType.PR_MERGED, record.getTaskId(),
				record.getPullRequestId(), record.getCommitId(), from,
				PullRequestStatus.MERGED.name(), "Pull request merged",
				Map.of("provider", providerName(), "remoteUrl", remoteUrl(record),
					"externalId", value(record.getExternalId()), "url", value(record.getUrl())));
			return record;
		}
		catch (RuntimeException exception) {
			record.markFailed();
			auditService.prEvent(EventType.PR_FAILED, record.getTaskId(),
				record.getPullRequestId(), record.getCommitId(), from,
				PullRequestStatus.FAILED.name(),
				"Pull request merge failed: " + message(exception),
				Map.of("provider", providerName(), "remoteUrl", remoteUrl(record),
					"externalId", value(record.getExternalId())));
			throw exception;
		}
	}

	public Optional<PullRequestRecord> get(String pullRequestId) {
		if (pullRequestId == null || pullRequestId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(pullRequestId));
	}

	public List<PullRequestRecord> getByTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		List<PullRequestRecord> result = new ArrayList<>(repository.getByTaskId(taskId));
		result.sort(Comparator.comparing(PullRequestRecord::getCreatedAt).reversed());
		return result;
	}

	private RemoteBranchRecord findSuccessfulPush(CommitRecord commit) {
		return remoteGitService.getByTask(commit.getTaskId()).stream()
			.filter(push -> push.getCommitId().equals(commit.getCommitId()))
			.filter(push -> push.getStatus() == RemoteStatus.SUCCESS)
			.findFirst()
			.orElse(null);
	}

	private PullRequestRecord requireRecord(String pullRequestId) {
		return get(pullRequestId).orElseThrow(
			() -> new ResourceNotFoundException("PullRequest", pullRequestId));
	}

	private String providerName() {
		String name = properties == null ? null : properties.getProvider();
		return name == null || name.isBlank() ? "mock" : name;
	}

	private String remoteUrl(PullRequestRecord record) {
		Optional<RemoteBranchRecord> push = remoteGitService.get(record.getRemoteId());
		if (push == null || push.isEmpty()) {
			return "";
		}
		return value(push.get().getUrl());
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private String message(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
