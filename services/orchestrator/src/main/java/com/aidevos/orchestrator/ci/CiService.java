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
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import org.springframework.stereotype.Service;

/**
 * CI/CD status awareness for pull requests: a check associates the PR commit
 * with its provider pipeline, records a CiRunRecord, polls the status and
 * emits CI_* audit events on the task timeline. On CI_FAILED a FailureContext
 * is registered for a later repair loop; this phase never starts a repair.
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
	private final AuditService auditService;

	public CiService(CiRepository repository, CiProvider provider,
			CiProviderProperties properties, PullRequestService pullRequestService,
			CommitService commitService, RepairCoordinator repairCoordinator,
			AuditService auditService) {
		this.repository = repository;
		this.provider = provider;
		this.properties = properties;
		this.pullRequestService = pullRequestService;
		this.commitService = commitService;
		this.repairCoordinator = repairCoordinator;
		this.auditService = auditService;
	}

	/**
	 * Checks the CI status of a pull request: creates a CiRunRecord and
	 * triggers the provider on first check, then polls the provider status
	 * and updates the record with CI_* audit events. A CI_FAILED registers a
	 * FailureContext for a later repair loop without starting one.
	 */
	public CiRunRecord check(String pullRequestId) {
		PullRequestRecord pr = pullRequestService.get(pullRequestId)
			.orElseThrow(() -> new ResourceNotFoundException("PullRequest", pullRequestId));
		String commitHash = commitHash(pr);
		CiRunRecord run = latestByPullRequest(pullRequestId).orElse(null);
		boolean created = run == null;
		if (created) {
			run = new CiRunRecord("ci-" + UUID.randomUUID(), pr.getTaskId(), pullRequestId,
				providerName(), pr.getBranch(), commitHash, Instant.now());
			repository.save(run);
			CiTriggerResult trigger = provider.trigger(
				new CiTriggerRequest(pullRequestId, pr.getBranch(), commitHash));
			run.updatePipelineId(trigger.pipelineId());
			run.updateReportUrl(trigger.reportUrl());
			run.markRunning();
			auditService.ciEvent(EventType.CI_STARTED, run.getTaskId(), run.getCiRunId(),
				pullRequestId, CiStatus.PENDING.name(), CiStatus.RUNNING.name(),
				"CI run started: " + value(trigger.pipelineId()), metadata(run));
		}
		CiRunResult result = provider.getStatus(run.getPipelineId());
		applyStatus(run, result, created);
		return run;
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
			case SUCCESS -> transition(run, CiStatus.SUCCESS, EventType.CI_SUCCESS,
				"CI run succeeded");
			case FAILED -> {
				if (transition(run, CiStatus.FAILED, EventType.CI_FAILED, "CI run failed")) {
					registerFailure(run);
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

	private void registerFailure(CiRunRecord run) {
		String workspaceId = pullRequestService.get(run.getPullRequestId())
			.flatMap(pr -> commitService.getCommit(pr.getCommitId()))
			.map(CommitRecord::getWorkspaceId)
			.orElse("");
		FailureContext context = new FailureContext(run.getTaskId(), workspaceId, null,
			"CI run failed: " + value(run.getPipelineId()), null, value(run.getReportUrl()),
			null, Instant.now());
		repairCoordinator.registerFailure(context);
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
