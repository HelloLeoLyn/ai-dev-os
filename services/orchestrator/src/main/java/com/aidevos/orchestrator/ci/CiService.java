package com.aidevos.orchestrator.ci;

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
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.feedback.PrFeedbackService;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.repair.CiFailureAnalyzer;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.repair.RepairTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * CI/CD status awareness for pull requests: a check associates the PR commit
 * with its provider pipeline, records a CiRunRecord, polls the status and
 * emits CI_* audit events on the task timeline. On CI_FAILED a FailureContext
 * is created (via CiFailureAnalyzer) and the RepairCoordinator starts a
 * bounded repair loop; a successful repair snapshots a ChangeSet for manual
 * review.
 * Scheduler, Worker and ExecutionEngine are not touched.
 */
@Service
public class CiService {

	private final CiRepository repository;
	private final CiProvider provider;
	private final CiProviderProperties properties;
	private final PullRequestService pullRequestService;
	private final CommitService commitService;
	private final RepairCoordinator repairCoordinator;
	private final CiFailureAnalyzer ciFailureAnalyzer;
	private final AuditService auditService;
	private volatile PrFeedbackService feedbackService;

	public CiService(CiRepository repository, CiProvider provider,
			CiProviderProperties properties, PullRequestService pullRequestService,
			CommitService commitService, RepairCoordinator repairCoordinator,
			CiFailureAnalyzer ciFailureAnalyzer, AuditService auditService) {
		this.repository = repository;
		this.provider = provider;
		this.properties = properties;
		this.pullRequestService = pullRequestService;
		this.commitService = commitService;
		this.repairCoordinator = repairCoordinator;
		this.ciFailureAnalyzer = ciFailureAnalyzer;
		this.auditService = auditService;
	}

	@Autowired(required = false)
	@Lazy
	public void setFeedbackService(PrFeedbackService feedbackService) {
		this.feedbackService = feedbackService;
	}

	/**
	 * Checks the CI status of a pull request: creates a CiRunRecord and
	 * triggers the provider on first check, then polls the provider status
	 * and updates the record with CI_* audit events. A CI_FAILED builds a
	 * FailureContext and starts the repair loop.
	 */
	public CiRunRecord check(String pullRequestId) {
		PullRequestRecord pr = requirePullRequest(pullRequestId);
		return checkInternal(pullRequestId, commitHash(pr), pr.getTaskId(), pr.getBranch());
	}

	/**
	 * Checks the CI status of a pull request for a specific commit (used by
	 * the feedback loop after a repaired commit is pushed). A new commit
	 * starts a fresh CI run.
	 */
	public CiRunRecord check(String pullRequestId, String commitHash) {
		PullRequestRecord pr = requirePullRequest(pullRequestId);
		return checkInternal(pullRequestId, value(commitHash), pr.getTaskId(),
			pr.getBranch());
	}

	/**
	 * Re-runs the repair loop for a previously failed CI run (feedback retry):
	 * rebuilds the failure context from the stored run and restarts the
	 * bounded repair.
	 */
	public RepairTask retryRepairFromCiRun(String ciRunId) {
		CiRunRecord run = repository.get(ciRunId);
		if (run == null) {
			throw new ResourceNotFoundException("CiRun", ciRunId);
		}
		FailureContext context = ciFailureAnalyzer.analyze(run, workspaceIdFor(run),
			provider.getReport(run.getPipelineId()));
		return repairCoordinator.startRepairFromCiFailure(context);
	}

	private CiRunRecord checkInternal(String pullRequestId, String commitHash, String taskId,
			String branch) {
		CiRunRecord run = latestByPullRequest(pullRequestId).orElse(null);
		boolean created = run == null
			|| !value(commitHash).equals(value(run.getCommitHash()));
		if (created) {
			run = new CiRunRecord("ci-" + UUID.randomUUID(), taskId, pullRequestId,
				providerName(), branch, commitHash, Instant.now());
			repository.save(run);
			CiTriggerResult trigger = provider.trigger(
				new CiTriggerRequest(pullRequestId, branch, commitHash));
			run.updatePipelineId(trigger.pipelineId());
			run.updateReportUrl(trigger.reportUrl());
			run.markRunning();
			auditService.ciEvent(EventType.CI_STARTED, taskId, run.getCiRunId(),
				pullRequestId, CiStatus.PENDING.name(), CiStatus.RUNNING.name(),
				"CI run started: " + value(trigger.pipelineId()), metadata(run));
		}
		CiRunResult result = provider.getStatus(run.getPipelineId());
		applyStatus(run, result, created);
		return run;
	}

	private PullRequestRecord requirePullRequest(String pullRequestId) {
		return pullRequestService.get(pullRequestId)
			.orElseThrow(() -> new ResourceNotFoundException("PullRequest", pullRequestId));
	}

	public Optional<CiRunRecord> get(String ciRunId) {
		if (ciRunId == null || ciRunId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(ciRunId));
	}

	public List<CiRunRecord> getByTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		List<CiRunRecord> result = new ArrayList<>(repository.getByTaskId(taskId));
		result.sort(Comparator.comparing(CiRunRecord::getStartedAt).reversed());
		return result;
	}

	private void applyStatus(CiRunRecord run, CiRunResult result, boolean freshlyCreated) {
		CiStatus next = result.status() == null ? CiStatus.RUNNING : result.status();
		switch (next) {
			case SUCCESS -> {
				if (transition(run, CiStatus.SUCCESS, EventType.CI_SUCCESS,
					"CI run succeeded") && feedbackService != null) {
					feedbackService.onCiSucceeded(run);
				}
			}
			case FAILED -> {
				if (transition(run, CiStatus.FAILED, EventType.CI_FAILED, "CI run failed")) {
					startRepair(run);
				}
			}
			case CANCELLED -> transition(run, CiStatus.CANCELLED, EventType.CI_CANCELLED,
				"CI run cancelled");
			default -> {
				if (!freshlyCreated && run.getStatus() == CiStatus.RUNNING) {
					auditService.ciEvent(EventType.CI_RUNNING, run.getTaskId(),
						run.getCiRunId(), run.getPullRequestId(), CiStatus.RUNNING.name(),
						CiStatus.RUNNING.name(), "CI run still in progress", metadata(run));
				}
			}
		}
	}

	private boolean transition(CiRunRecord run, CiStatus target, EventType type,
			String summary) {
		CiStatus from = run.getStatus();
		if (from == target || from != CiStatus.RUNNING) {
			return false;
		}
		switch (target) {
			case SUCCESS -> run.markSuccess();
			case FAILED -> run.markFailed();
			case CANCELLED -> run.markCancelled();
			default -> throw new IllegalStateException("Unexpected target: " + target);
		}
		auditService.ciEvent(type, run.getTaskId(), run.getCiRunId(), run.getPullRequestId(),
			from.name(), target.name(), summary, metadata(run));
		return true;
	}

	private void startRepair(CiRunRecord run) {
		FailureContext context = ciFailureAnalyzer.analyze(run, workspaceIdFor(run),
			provider.getReport(run.getPipelineId()));
		repairCoordinator.startRepairFromCiFailure(context);
	}

	private String workspaceIdFor(CiRunRecord run) {
		return pullRequestService.get(run.getPullRequestId())
			.flatMap(pr -> commitService.getCommit(pr.getCommitId()))
			.map(CommitRecord::getWorkspaceId)
			.orElse("");
	}

	private String commitHash(PullRequestRecord pr) {
		return commitService.getCommit(pr.getCommitId())
			.map(CommitRecord::getGitHash)
			.map(this::value)
			.orElse("");
	}

	private Optional<CiRunRecord> latestByPullRequest(String pullRequestId) {
		List<CiRunRecord> runs = repository.getByPullRequestId(pullRequestId);
		runs.sort(Comparator.comparing(CiRunRecord::getStartedAt).reversed());
		return runs.stream().findFirst();
	}

	private String providerName() {
		String name = properties == null ? null : properties.getProvider();
		return name == null || name.isBlank() ? "mock" : name;
	}

	private Map<String, Object> metadata(CiRunRecord run) {
		return Map.of("provider", run.getProvider(), "pullRequestId", run.getPullRequestId(),
			"pipelineId", value(run.getPipelineId()), "commitHash", value(run.getCommitHash()),
			"reportUrl", value(run.getReportUrl()));
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
