package com.aidevos.orchestrator.feedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemoteStatus;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Pull request feedback loop: a failed CI run is linked to its repair task;
 * a successful repair snapshots a ChangeSet that waits for human review; the
 * approved ChangeSet is committed, pushed and re-checked by CI. The loop
 * never auto-approves a ChangeSet, never merges a PR and never bypasses
 * review. CiService is wired lazily via setter to break the cycle
 * CiService -> RepairCoordinator -> PrFeedbackService -> CiService.
 */
@Service
public class PrFeedbackService {

	private final FeedbackRepository repository;
	private final PullRequestService pullRequestService;
	private final CommitService commitService;
	private final RemoteGitService remoteGitService;
	private final ChangeService changeService;
	private final AuditService auditService;

	private volatile CiService ciService;

	public PrFeedbackService(FeedbackRepository repository,
			PullRequestService pullRequestService, CommitService commitService,
			RemoteGitService remoteGitService, ChangeService changeService,
			AuditService auditService) {
		this.repository = repository;
		this.pullRequestService = pullRequestService;
		this.commitService = commitService;
		this.remoteGitService = remoteGitService;
		this.changeService = changeService;
		this.auditService = auditService;
	}

	@Autowired
	@Lazy
	public void setCiService(CiService ciService) {
		this.ciService = ciService;
	}

	/**
	 * A repair for a CI failure started: creates (or reuses) the feedback
	 * record for the task's pull request and moves it to REPAIRING. Reused
	 * records count one more retry.
	 */
	public PrFeedbackRecord onRepairStarted(FailureContext context, RepairTask repairTask) {
		if (repairTask == null || repairTask.getTaskId() == null
			|| repairTask.getTaskId().isBlank()) {
			return null;
		}
		String taskId = repairTask.getTaskId();
		PrFeedbackRecord feedback = latestByTask(taskId).filter(
			record -> record.getStatus() != FeedbackStatus.SUCCESS).orElse(null);
		boolean created = feedback == null;
		if (created) {
			String pullRequestId = latestPullRequestId(taskId);
			feedback = new PrFeedbackRecord("feedback-" + UUID.randomUUID(), taskId,
				pullRequestId, repairTask.getRepairId(), null, null,
				context == null ? "" : value(context.sourceId()), FeedbackStatus.CREATED,
				0, Instant.now());
			repository.save(feedback);
			auditService.feedbackEvent(EventType.FEEDBACK_CREATED, taskId,
				feedback.getFeedbackId(), null, FeedbackStatus.CREATED.name(),
				"Feedback loop created", Map.of("pullRequestId", feedback.getPullRequestId()));
		}
		else {
			feedback.incrementRetry();
		}
		feedback.linkRepair(repairTask.getRepairId());
		feedback.linkCiRun(context == null ? "" : value(context.sourceId()));
		String from = feedback.getStatus().name();
		feedback.markRepairing();
		auditService.feedbackEvent(EventType.FEEDBACK_REPAIRING, taskId,
			feedback.getFeedbackId(), from, FeedbackStatus.REPAIRING.name(),
			"Repairing CI failure",
			Map.of("repairTaskId", repairTask.getRepairId(), "ciRunId",
				value(context == null ? "" : context.sourceId())));
		repository.save(feedback);
		return feedback;
	}

	/**
	 * The repair succeeded: the repaired ChangeSet (newest for the task) is
	 * linked and the feedback waits for human review.
	 */
	public PrFeedbackRecord onRepairSucceeded(FailureContext context, RepairTask repairTask) {
		PrFeedbackRecord feedback = latestByRepair(repairTask.getRepairId()).orElse(null);
		if (feedback == null) {
			return null;
		}
		String changeId = latestChangeId(repairTask.getTaskId());
		feedback.linkChange(changeId);
		String from = feedback.getStatus().name();
		feedback.markWaitingReview();
		auditService.feedbackEvent(EventType.FEEDBACK_WAITING_REVIEW,
			repairTask.getTaskId(), feedback.getFeedbackId(), from,
			FeedbackStatus.WAITING_REVIEW.name(), "Repair done, waiting for review",
			Map.of("changeId", value(changeId)));
		repository.save(feedback);
		return feedback;
	}

	/** The repair failed: the feedback loop ends in FAILED (retry available). */
	public PrFeedbackRecord onRepairFailed(FailureContext context, RepairTask repairTask) {
		PrFeedbackRecord feedback = latestByRepair(repairTask.getRepairId()).orElse(null);
		if (feedback == null) {
			return null;
		}
		String from = feedback.getStatus().name();
		feedback.markFailed();
		auditService.feedbackEvent(EventType.FEEDBACK_FAILED, repairTask.getTaskId(),
			feedback.getFeedbackId(), from, FeedbackStatus.FAILED.name(),
			"Repair failed: " + value(context == null ? "" : context.errorMessage()),
			Map.of());
		repository.save(feedback);
		return feedback;
	}

	/**
	 * The repaired ChangeSet was approved: commit, push and re-check CI for
	 * the new commit. Feedback PUSHED after the commit, RECHECKING after the
	 * push; a failure at any step marks the feedback FAILED.
	 */
	public PrFeedbackRecord onChangeApproved(String changeId, String taskId) {
		PrFeedbackRecord feedback = latestByTask(taskId).filter(
			record -> record.getStatus() == FeedbackStatus.WAITING_REVIEW).orElse(null);
		if (feedback == null) {
			return null;
		}
		try {
			CommitRecord commit = commitService.commit(changeId);
			feedback.linkCommit(commit.getCommitId());
			String from = feedback.getStatus().name();
			feedback.markPushed();
			auditService.feedbackEvent(EventType.FEEDBACK_PUSHED, taskId,
				feedback.getFeedbackId(), from, FeedbackStatus.PUSHED.name(),
				"Change committed: " + value(commit.getGitHash()),
				Map.of("commitId", commit.getCommitId()));
			repository.save(feedback);

			RemoteBranchRecord push = remoteGitService.push(commit.getCommitId(), null);
			from = feedback.getStatus().name();
			feedback.markRechecking();
			auditService.feedbackEvent(EventType.FEEDBACK_RECHECKING, taskId,
				feedback.getFeedbackId(), from, FeedbackStatus.RECHECKING.name(),
				"Change pushed, re-checking CI",
				Map.of("remoteId", push.getRemoteId(), "commitHash",
					value(commit.getGitHash())));
			repository.save(feedback);
			if (ciService != null) {
				CiRunRecord recheckRun = ciService.check(feedback.getPullRequestId(),
					value(commit.getGitHash()));
				if (recheckRun != null) {
					feedback.linkCiRun(recheckRun.getCiRunId());
					repository.save(feedback);
				}
			}
			return feedback;
		}
		catch (RuntimeException exception) {
			String from = feedback.getStatus().name();
			feedback.markFailed();
			auditService.feedbackEvent(EventType.FEEDBACK_FAILED, taskId,
				feedback.getFeedbackId(), from, FeedbackStatus.FAILED.name(),
				"Commit or push failed: " + errorMessage(exception), Map.of());
			repository.save(feedback);
			return feedback;
		}
	}

	/**
	 * The CI run linked to the feedback succeeded: the loop is complete.
	 */
	public PrFeedbackRecord onCiSucceeded(CiRunRecord run) {
		if (run == null || run.getCiRunId() == null || run.getCiRunId().isBlank()) {
			return null;
		}
		PrFeedbackRecord feedback = repository.getByCiRunId(run.getCiRunId()).stream()
			.findFirst().orElse(null);
		if (feedback == null || feedback.getStatus() != FeedbackStatus.RECHECKING) {
			return null;
		}
		String from = feedback.getStatus().name();
		feedback.markSuccess();
		auditService.feedbackEvent(EventType.FEEDBACK_SUCCESS, feedback.getTaskId(),
			feedback.getFeedbackId(), from, FeedbackStatus.SUCCESS.name(),
			"CI re-check succeeded", Map.of("ciRunId", run.getCiRunId()));
		repository.save(feedback);
		return feedback;
	}

	public Optional<PrFeedbackRecord> get(String feedbackId) {
		if (feedbackId == null || feedbackId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(feedbackId));
	}

	public List<PrFeedbackRecord> getByTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		List<PrFeedbackRecord> result = new ArrayList<>(repository.getByTaskId(taskId));
		result.sort(Comparator.comparing(PrFeedbackRecord::getCreatedAt).reversed());
		return result;
	}

	/**
	 * Retries a FAILED feedback loop: re-runs the bounded repair for the
	 * stored CI run. The repair reuses this feedback record, moves it back to
	 * REPAIRING and increments the retry counter; a successful repair again
	 * waits for review.
	 */
	public PrFeedbackRecord retry(String feedbackId) {
		PrFeedbackRecord feedback = get(feedbackId)
			.orElseThrow(() -> new ResourceNotFoundException("Feedback", feedbackId));
		if (feedback.getStatus() != FeedbackStatus.FAILED
			|| feedback.getCiRunId().isBlank() || ciService == null) {
			return feedback;
		}
		ciService.retryRepairFromCiRun(feedback.getCiRunId());
		return get(feedbackId).orElse(feedback);
	}

	private Optional<PrFeedbackRecord> latestByTask(String taskId) {
		List<PrFeedbackRecord> records = repository.getByTaskId(taskId);
		records.sort(Comparator.comparing(PrFeedbackRecord::getCreatedAt).reversed());
		return records.stream().findFirst();
	}

	private Optional<PrFeedbackRecord> latestByRepair(String repairTaskId) {
		if (repairTaskId == null || repairTaskId.isBlank()) {
			return Optional.empty();
		}
		return repository.list().stream()
			.filter(record -> repairTaskId.equals(record.getRepairTaskId()))
			.findFirst();
	}

	private String latestPullRequestId(String taskId) {
		List<PullRequestRecord> pullRequests = pullRequestService.getByTask(taskId);
		return pullRequests.isEmpty() ? "" : pullRequests.get(0).getPullRequestId();
	}

	private String latestChangeId(String taskId) {
		List<ChangeSet> changes = changeService.getChangesByTask(taskId);
		return changes.isEmpty() ? "" : changes.get(0).getChangeId();
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
