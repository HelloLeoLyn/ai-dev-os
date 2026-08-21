package com.aidevos.orchestrator.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.pr.PullRequestCreateRequest;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.pr.PullRequestStatus;
import com.aidevos.orchestrator.qualitygate.QualityGateDecision;
import com.aidevos.orchestrator.qualitygate.QualityGateResult;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApproval;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.remote.RemotePushApprovalStatus;
import com.aidevos.orchestrator.remote.RemoteStatus;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Unified delivery pipeline orchestration. One aggregate per task tracks
 * ChangeSet -> Validation -> Quality Gate -> Commit -> Remote Push Approval ->
 * Push -> PR -> CI -> Delivery Complete. Deterministic stages advance
 * automatically from persisted state; the only human gates are quality gate
 * approval and remote push approval. advance() is idempotent and crash-safe:
 * every step re-reads the persisted pipeline and the underlying entities, so
 * a restart resumes from the next unfinished stage and never re-executes
 * completed work.
 *
 * <p>The pipeline only orchestrates; the existing services (ChangeService,
 * ValidationService, QualityGateService, CommitService, RemoteGitService,
 * RemotePushApprovalService, PullRequestService, CiService) remain the
 * authorities for their own implementation.
 */
@Service
public class DeliveryPipelineService {

	private static final Logger logger = LoggerFactory.getLogger(DeliveryPipelineService.class);
	private static final String DEFAULT_REMOTE = "origin";
	private static final String DEFAULT_TARGET_BRANCH = "main";
	private static final int MAX_ADVANCE_ITERATIONS = 16;

	private final DeliveryPipelineRepository repository;
	private final ChangeService changeService;
	private final ValidationService validationService;
	private final QualityGateService qualityGateService;
	private final CommitService commitService;
	private final RemoteGitService remoteGitService;
	private final RemotePushApprovalService approvalService;
	private final PullRequestService pullRequestService;
	private final CiService ciService;
	private final AuditService auditService;

	public DeliveryPipelineService(DeliveryPipelineRepository repository,
			ChangeService changeService, ValidationService validationService,
			QualityGateService qualityGateService, CommitService commitService,
			RemoteGitService remoteGitService, RemotePushApprovalService approvalService,
			PullRequestService pullRequestService, CiService ciService,
			AuditService auditService) {
		this.repository = repository;
		this.changeService = changeService;
		this.validationService = validationService;
		this.qualityGateService = qualityGateService;
		this.commitService = commitService;
		this.remoteGitService = remoteGitService;
		this.approvalService = approvalService;
		this.pullRequestService = pullRequestService;
		this.ciService = ciService;
		this.auditService = auditService;
	}

	public DeliveryPipeline get(String taskId) {
		return taskId == null ? null : repository.get(taskId);
	}

	public List<DeliveryPipeline> list() {
		return repository.list();
	}

	/**
	 * Advances the task's pipeline as far as it can go without a human gate.
	 * Creates the aggregate on first call. Returns the persisted pipeline
	 * after the run; callers may re-invoke after a human decision or a fix.
	 */
	public synchronized DeliveryPipeline advance(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("taskId is required");
		}
		DeliveryPipeline pipeline = repository.get(taskId);
		if (pipeline != null && pipeline.getStatus() == DeliveryStatus.FAILED) {
			if (reconcile(pipeline) == DeliveryStage.FAILED) {
				return pipeline;
			}
			pipeline.resumeFromFailure();
			repository.save(pipeline);
		}
		if (pipeline != null && pipeline.getStatus() == DeliveryStatus.WAITING_APPROVAL) {
			pipeline.resumeFromWaitingApproval();
			repository.save(pipeline);
		}
		if (pipeline == null) {
			pipeline = new DeliveryPipeline(taskId, Instant.now());
			repository.save(pipeline);
			auditService.deliveryEvent(EventType.DELIVERY_PIPELINE_STARTED, taskId,
				DeliveryStage.CHANGE_READY.name(), null, DeliveryStatus.RUNNING.name(),
				"Delivery pipeline started", Map.of("taskId", taskId));
		}
		for (int i = 0; i < MAX_ADVANCE_ITERATIONS; i++) {
			DeliveryStage stage = reconcile(pipeline);
			if (stage == DeliveryStage.DELIVERY_COMPLETE) {
				complete(pipeline);
				break;
			}
			if (stage == DeliveryStage.FAILED || pipeline.getStatus() == DeliveryStatus.FAILED) {
				break;
			}
			if (stage == DeliveryStage.WAITING_APPROVAL
					|| pipeline.getStatus() == DeliveryStatus.WAITING_APPROVAL) {
				waiting(pipeline);
				break;
			}
			boolean progressed = executeStage(pipeline, stage);
			repository.save(pipeline);
			if (!progressed) {
				break;
			}
		}
		return repository.get(taskId);
	}

	/**
	 * Advances only when a pipeline already exists. Used by human-gate
	 * services after an approval so legacy flows are not force-created.
	 */
	public synchronized DeliveryPipeline advanceIfExists(String taskId) {
		return repository.get(taskId) == null ? null : advance(taskId);
	}

	/**
	 * Reconciles the persisted pipeline against the underlying entities.
	 * This is the crash/retry safety core: already produced entities are
	 * reused and the stage is derived from them instead of trusting memory.
	 */
	private DeliveryStage reconcile(DeliveryPipeline pipeline) {
		if (pipeline.getStatus() == DeliveryStatus.COMPLETE
				|| pipeline.getStatus() == DeliveryStatus.FAILED) {
			return pipeline.getCurrentStage();
		}
		if (notBlank(pipeline.getCiRunId())) {
			CiRunRecord run = ciService.get(pipeline.getCiRunId()).orElse(null);
			if (run == null) {
				return DeliveryStage.CI_CHECKING;
			}
			if (run.getStatus() == CiStatus.SUCCESS) {
				return DeliveryStage.DELIVERY_COMPLETE;
			}
			if (run.getStatus() == CiStatus.FAILED || run.getStatus() == CiStatus.CANCELLED) {
				return DeliveryStage.FAILED;
			}
			return DeliveryStage.CI_CHECKING;
		}
		if (notBlank(pipeline.getPullRequestId())) {
			PullRequestRecord pr = pullRequestService.get(pipeline.getPullRequestId()).orElse(null);
			if (pr != null && pr.getStatus() == PullRequestStatus.FAILED) {
				return DeliveryStage.FAILED;
			}
			return DeliveryStage.CI_CHECKING;
		}
		if (notBlank(pipeline.getRemoteBranchId())) {
			RemoteBranchRecord push = remoteGitService.get(pipeline.getRemoteBranchId()).orElse(null);
			if (push == null) {
				return DeliveryStage.CREATING_PR;
			}
			if (push.getStatus() == RemoteStatus.FAILED) {
				return DeliveryStage.FAILED;
			}
			if (push.getStatus() == RemoteStatus.SUCCESS) {
				return DeliveryStage.CREATING_PR;
			}
			return DeliveryStage.PUSHING;
		}
		if (notBlank(pipeline.getRemotePushApprovalId())) {
			RemotePushApproval approval = approvalService.get(pipeline.getRemotePushApprovalId());
			if (approval == null) {
				return DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL;
			}
			switch (approval.getStatus()) {
				case APPROVED -> {
					return DeliveryStage.PUSHING;
				}
				case CONSUMED -> {
					if (hasSuccessfulPush(pipeline.getTaskId(), pipeline.getCommitId())) {
						return DeliveryStage.CREATING_PR;
					}
					return DeliveryStage.FAILED;
				}
				case REJECTED -> {
					return DeliveryStage.FAILED;
				}
				default -> {
					return DeliveryStage.WAITING_APPROVAL;
				}
			}
		}
		if (notBlank(pipeline.getCommitId())) {
			CommitRecord commit = commitService.getCommit(pipeline.getCommitId()).orElse(null);
			if (commit == null || commit.getStatus() == CommitStatus.PENDING
					|| commit.getStatus() == CommitStatus.COMMITTING) {
				return DeliveryStage.COMMITTING;
			}
			if (commit.getStatus() == CommitStatus.FAILED) {
				return DeliveryStage.FAILED;
			}
			return DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL;
		}
		if (notBlank(pipeline.getQualityGateId())) {
			QualityGateResult gate;
			try {
				gate = qualityGateService.get(pipeline.getQualityGateId());
			}
			catch (RuntimeException missing) {
				return DeliveryStage.QUALITY_GATE;
			}
			if (gate == null) {
				return DeliveryStage.QUALITY_GATE;
			}
			if (gate.getDecision() == QualityGateDecision.REQUIRE_APPROVAL
					&& gate.getStatus() == com.aidevos.orchestrator.qualitygate.QualityGateStatus.EVALUATED) {
				return DeliveryStage.WAITING_APPROVAL;
			}
			if (gate.getDecision() == QualityGateDecision.BLOCK) {
				return DeliveryStage.FAILED;
			}
			return DeliveryStage.COMMITTING;
		}
		if (notBlank(pipeline.getValidationRunId())) {
			ValidationRun run = validationService.get(pipeline.getValidationRunId());
			if (run == null) {
				return DeliveryStage.VALIDATING;
			}
			if (run.getStatus() == ValidationStatus.FAILED
					|| run.getStatus() == ValidationStatus.ERROR
					|| run.getStatus() == ValidationStatus.BLOCKED) {
				return DeliveryStage.FAILED;
			}
			if (run.getStatus() == ValidationStatus.SUCCESS
					|| run.getStatus() == ValidationStatus.SKIPPED) {
				return DeliveryStage.QUALITY_GATE;
			}
			return DeliveryStage.VALIDATING;
		}
		if (notBlank(pipeline.getChangeSetId())) {
			return DeliveryStage.VALIDATING;
		}
		return DeliveryStage.CHANGE_READY;
	}

	/**
	 * Executes one deterministic stage. Returns true when the stage made
	 * progress (the next loop iteration reconciles a different stage) and
	 * false when the stage is still in progress, is blocked on a human gate
	 * or ended in failure.
	 */
	private boolean executeStage(DeliveryPipeline pipeline, DeliveryStage stage) {
		pipeline.advanceTo(stage);
		switch (stage) {
			case CHANGE_READY -> {
				ChangeSet change = latestChange(pipeline.getTaskId());
				if (change == null) {
					return false;
				}
				if (change.getStatus() == ChangeStatus.REJECTED) {
					fail(pipeline, DeliveryFailureClass.HUMAN_REQUIRED,
						"Change set was rejected");
					return false;
				}
				if (change.getStatus() != ChangeStatus.APPROVED
						&& change.getStatus() != ChangeStatus.COMMITTED) {
					return false;
				}
				pipeline.bindChangeSet(change.getChangeId());
				pipeline.bindExecutionWorkspace(change.getWorkspaceId());
				stageStarted(pipeline, DeliveryStage.VALIDATING);
				pipeline.advanceTo(DeliveryStage.VALIDATING);
				return true;
			}
			case VALIDATING -> {
				if (notBlank(pipeline.getValidationRunId())) {
					// V1-C：绑定过 FAILED/ERROR run 的 pipeline 不得继续推进（幂等拦截）
					ValidationRun bound = validationService.get(pipeline.getValidationRunId());
					if (bound != null && (bound.getStatus() == ValidationStatus.FAILED
							|| bound.getStatus() == ValidationStatus.ERROR)) {
						fail(pipeline, DeliveryFailureClass.RECOVERABLE,
							validationService.failureReason(bound));
						return false;
					}
					return progressed(pipeline, DeliveryStage.VALIDATING);
				}
				// V1-FLOW-CONFORMANCE：重建/历史任务复用已有 SUCCESS delivery run（不重复测试）。
				ValidationRun existing = validationService.findReusableDeliveryRun(
					pipeline.getTaskId(), pipeline.getChangeSetId());
				if (existing != null) {
					pipeline.bindValidation(existing.getValidationRunId());
					stageSucceeded(pipeline, DeliveryStage.VALIDATING, existing.getValidationRunId());
					return progressed(pipeline, DeliveryStage.VALIDATING);
				}
				try {
					ValidationRun run = validationService.startDelivery(pipeline.getChangeSetId());
					pipeline.bindValidation(run.getValidationRunId());
					if (run.getStatus() == ValidationStatus.FAILED
							|| run.getStatus() == ValidationStatus.ERROR) {
						// V1-C：Validation FAILED → 不进 Quality Gate → Pipeline FAILED（结构化失败）
						fail(pipeline, DeliveryFailureClass.RECOVERABLE,
							validationService.failureReason(run));
						return false;
					}
					stageSucceeded(pipeline, DeliveryStage.VALIDATING, run.getValidationRunId());
					return progressed(pipeline, DeliveryStage.VALIDATING);
				}
				catch (RuntimeException exception) {
					fail(pipeline, classify(exception), "Validation failed: " + message(exception));
					return false;
				}
			}
			case QUALITY_GATE -> {
				if (notBlank(pipeline.getQualityGateId())) {
					return progressed(pipeline, DeliveryStage.QUALITY_GATE);
				}
				try {
					QualityGateResult gate = qualityGateService.evaluate(pipeline.getValidationRunId());
					pipeline.bindQualityGate(gate.getGateResultId());
					stageSucceeded(pipeline, DeliveryStage.QUALITY_GATE, gate.getGateResultId());
					if (gate.getDecision() == QualityGateDecision.REQUIRE_APPROVAL) {
						stageWaiting(pipeline, DeliveryStage.QUALITY_GATE,
							"Quality gate requires approval");
						return false;
					}
					if (gate.getDecision() == QualityGateDecision.BLOCK) {
						fail(pipeline, DeliveryFailureClass.HUMAN_REQUIRED,
							"Quality gate blocked: " + gateReason(gate));
						return false;
					}
					return progressed(pipeline, DeliveryStage.QUALITY_GATE);
				}
				catch (RuntimeException exception) {
					fail(pipeline, classify(exception), "Quality gate failed: " + message(exception));
					return false;
				}
			}
			case COMMITTING -> {
				if (notBlank(pipeline.getCommitId())) {
					return progressed(pipeline, DeliveryStage.COMMITTING);
				}
				// DELIVERY-SINGLE-AUTHORITY-V1：重建/历史任务幂等——change 已有 SUCCESS commit 时复用，
				// 不重复 git commit（与 reconcile 的 "already produced entities are reused" 设计一致）。
				CommitRecord existing = existingSuccessCommit(pipeline.getTaskId(),
					pipeline.getChangeSetId());
				if (existing != null) {
					pipeline.bindCommit(existing.getCommitId());
					stageSucceeded(pipeline, DeliveryStage.COMMITTING, existing.getCommitId());
					RemotePushApproval approval = ensureApproval(existing);
					if (approval != null) {
						pipeline.bindApproval(approval.getApprovalId());
					}
					pipeline.advanceTo(DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL);
					return true;
				}
				try {
					CommitRecord commit = commitService.commit(pipeline.getChangeSetId());
					pipeline.bindCommit(commit.getCommitId());
					stageSucceeded(pipeline, DeliveryStage.COMMITTING, commit.getCommitId());
					RemotePushApproval approval = ensureApproval(commit);
					if (approval != null) {
						pipeline.bindApproval(approval.getApprovalId());
					}
					pipeline.advanceTo(DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL);
					return true;
				}
				catch (RuntimeException exception) {
					fail(pipeline, classify(exception), "Commit failed: " + message(exception));
					return false;
				}
			}
			case WAITING_REMOTE_PUSH_APPROVAL -> {
				RemotePushApproval approval = approvalService.get(pipeline.getRemotePushApprovalId());
				if (approval == null) {
					fail(pipeline, DeliveryFailureClass.FATAL,
						"Remote push approval binding is missing");
					return false;
				}
				switch (approval.getStatus()) {
					case APPROVED, CONSUMED -> {
						pipeline.advanceTo(DeliveryStage.PUSHING);
						return true;
					}
					case REJECTED -> {
						fail(pipeline, DeliveryFailureClass.HUMAN_REQUIRED,
							"Remote push approval was rejected");
						return false;
					}
					default -> {
						stageWaiting(pipeline, DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL,
							"Remote push approval required");
						return false;
					}
				}
			}
			case PUSHING -> {
				if (notBlank(pipeline.getRemoteBranchId())) {
					return progressed(pipeline, DeliveryStage.PUSHING);
				}
				RemotePushApproval bound = approvalService.get(pipeline.getRemotePushApprovalId());
				if (bound != null && (bound.getStatus() == RemotePushApprovalStatus.CONSUMED
						|| bound.getStatus() == RemotePushApprovalStatus.REJECTED)) {
					CommitRecord commit = commitService.getCommit(pipeline.getCommitId())
						.orElseThrow(() -> new IllegalStateException("Commit binding is missing"));
					RemotePushApproval fresh = remoteGitService.requestApproval(commit.getCommitId(),
						DEFAULT_REMOTE);
					if (!fresh.getApprovalId().equals(pipeline.getRemotePushApprovalId())) {
						pipeline.bindApproval(fresh.getApprovalId());
					}
					if (fresh.getStatus() == RemotePushApprovalStatus.PENDING) {
						stageWaiting(pipeline, DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL,
							"Remote push approval required");
						return false;
					}
				}
				try {
					RemoteBranchRecord push = remoteGitService.push(pipeline.getCommitId(),
						DEFAULT_REMOTE, pipeline.getRemotePushApprovalId());
					pipeline.bindPush(push.getRemoteId());
					stageSucceeded(pipeline, DeliveryStage.PUSHING, push.getRemoteId());
					return progressed(pipeline, DeliveryStage.PUSHING);
				}
				catch (RuntimeException exception) {
					fail(pipeline, classify(exception), "Remote push failed: " + message(exception));
					return false;
				}
			}
			case CREATING_PR -> {
				if (notBlank(pipeline.getPullRequestId())) {
					return progressed(pipeline, DeliveryStage.CREATING_PR);
				}
				try {
					PullRequestRecord existing = pullRequestService
						.getByCommit(pipeline.getCommitId()).orElse(null);
					PullRequestRecord pr = existing != null && existing.getStatus() != PullRequestStatus.FAILED
						? existing : pullRequestService.createPullRequest(pipeline.getCommitId(),
							new PullRequestCreateRequest(DEFAULT_TARGET_BRANCH, null, null));
					pipeline.bindPullRequest(pr.getPullRequestId());
					stageSucceeded(pipeline, DeliveryStage.CREATING_PR, pr.getPullRequestId());
					return progressed(pipeline, DeliveryStage.CREATING_PR);
				}
				catch (RuntimeException exception) {
					fail(pipeline, classify(exception), "Pull request failed: " + message(exception));
					return false;
				}
			}
			case CI_CHECKING -> {
				if (notBlank(pipeline.getCiRunId())) {
					CiRunRecord bound = ciService.get(pipeline.getCiRunId()).orElse(null);
					if (bound != null && bound.getStatus() == CiStatus.SUCCESS) {
						return progressed(pipeline, DeliveryStage.CREATING_PR);
					}
					if (bound != null && (bound.getStatus() == CiStatus.FAILED
							|| bound.getStatus() == CiStatus.CANCELLED)) {
						fail(pipeline, DeliveryFailureClass.HUMAN_REQUIRED,
							"CI " + bound.getStatus() + ": delivery cannot complete without CI success");
						return false;
					}
					if (checkCi(pipeline)) {
						return progressed(pipeline, DeliveryStage.CREATING_PR);
					}
					return false;
				}
				try {
					CommitRecord commit = commitService.getCommit(pipeline.getCommitId())
						.orElseThrow(() -> new IllegalStateException("Commit binding is missing"));
					CiRunRecord run = ciService.check(pipeline.getPullRequestId(),
						commit.getGitHash());
					pipeline.bindCiRun(run.getCiRunId());
					stageSucceeded(pipeline, DeliveryStage.CI_CHECKING, run.getCiRunId());
					if (run.getStatus() == CiStatus.SUCCESS) {
						return progressed(pipeline, DeliveryStage.CI_CHECKING);
					}
					if (run.getStatus() == CiStatus.FAILED || run.getStatus() == CiStatus.CANCELLED) {
						fail(pipeline, DeliveryFailureClass.HUMAN_REQUIRED,
							"CI " + run.getStatus() + ": delivery cannot complete without CI success");
						return false;
					}
					return false;
				}
				catch (RuntimeException exception) {
					fail(pipeline, classify(exception), "CI check failed: " + message(exception));
					return false;
				}
			}
			default -> {
				return false;
			}
		}
	}

	private RemotePushApproval ensureApproval(CommitRecord commit) {
		List<RemotePushApproval> existing = approvalService.getByTask(commit.getTaskId());
		for (RemotePushApproval approval : existing) {
			if (commit.getCommitId().equals(approval.getCommitId())
					&& (approval.getStatus() == RemotePushApprovalStatus.PENDING
						|| approval.getStatus() == RemotePushApprovalStatus.APPROVED
						|| approval.getStatus() == RemotePushApprovalStatus.CONSUMED)) {
				return approval;
			}
		}
		return remoteGitService.requestApproval(commit.getCommitId(), DEFAULT_REMOTE);
	}

	/**
	 * Reuses an already-produced SUCCESS commit for the change (idempotent
	 * rebuild for legacy tasks whose push already succeeded). Never creates
	 * a second git commit.
	 */
	private CommitRecord existingSuccessCommit(String taskId, String changeId) {
		for (CommitRecord commit : commitService.getCommitsByTask(taskId)) {
			if (changeId.equals(commit.getChangeId())
					&& commit.getStatus() == CommitStatus.SUCCESS) {
				return commit;
			}
		}
		return null;
	}

	/**
	 * True when the reconciled stage differs from the stage just executed, i.e.
	 * the pipeline actually moved forward. Keeps the advance loop from
	 * spinning on an in-flight stage (for example a CI run still RUNNING).
	 */
	private boolean progressed(DeliveryPipeline pipeline, DeliveryStage executed) {
		return reconcile(pipeline) != executed;
	}

	/**
	 * Re-polls the CI run through the existing check path and reports whether
	 * it reached SUCCESS. A failed/cancelled run fails the pipeline. Used by
	 * CI_CHECKING once a run is bound: repeated advances poll the same run
	 * (ciService.check reuses the existing run, so no second run or trigger).
	 */
	private boolean checkCi(DeliveryPipeline pipeline) {
		try {
			CommitRecord commit = commitService.getCommit(pipeline.getCommitId())
				.orElseThrow(() -> new IllegalStateException("Commit binding is missing"));
			CiRunRecord run = ciService.check(pipeline.getPullRequestId(),
				commit.getGitHash());
			if (run.getStatus() == CiStatus.SUCCESS) {
				return true;
			}
			if (run.getStatus() == CiStatus.FAILED || run.getStatus() == CiStatus.CANCELLED) {
				fail(pipeline, DeliveryFailureClass.HUMAN_REQUIRED,
					"CI " + run.getStatus() + ": delivery cannot complete without CI success");
				return false;
			}
			return false;
		}
		catch (RuntimeException exception) {
			fail(pipeline, classify(exception), "CI check failed: " + message(exception));
			return false;
		}
	}

	private void complete(DeliveryPipeline pipeline) {
		if (pipeline.getStatus() == DeliveryStatus.COMPLETE) {
			return;
		}
		pipeline.markComplete();
		repository.save(pipeline);
		auditService.deliveryEvent(EventType.DELIVERY_COMPLETED, pipeline.getTaskId(),
			DeliveryStage.DELIVERY_COMPLETE.name(), DeliveryStatus.RUNNING.name(),
			DeliveryStatus.COMPLETE.name(), "Delivery pipeline completed", Map.of(
				"commitId", pipeline.getCommitId(), "pullRequestId", pipeline.getPullRequestId(),
				"ciRunId", pipeline.getCiRunId()));
	}

	private void waiting(DeliveryPipeline pipeline) {
		if (pipeline.getStatus() == DeliveryStatus.WAITING_APPROVAL) {
			return;
		}
		pipeline.markWaitingApproval();
		repository.save(pipeline);
		auditService.deliveryEvent(EventType.DELIVERY_WAITING_APPROVAL, pipeline.getTaskId(),
			pipeline.getCurrentStage().name(), DeliveryStatus.RUNNING.name(),
			DeliveryStatus.WAITING_APPROVAL.name(), "Delivery waiting for approval", Map.of(
				"stage", pipeline.getCurrentStage().name()));
	}

	private void fail(DeliveryPipeline pipeline, DeliveryFailureClass failureClass,
			String reason) {
		if (pipeline.getStatus() == DeliveryStatus.FAILED) {
			return;
		}
		pipeline.markFailed(failureClass, reason);
		auditService.deliveryEvent(EventType.DELIVERY_STAGE_FAILED, pipeline.getTaskId(),
			pipeline.getCurrentStage().name(), DeliveryStatus.RUNNING.name(),
			DeliveryStatus.FAILED.name(), reason, Map.of(
				"failureClass", failureClass == null ? null : failureClass.name(),
				"reason", reason));
	}

	private void stageStarted(DeliveryPipeline pipeline, DeliveryStage stage) {
		auditService.deliveryEvent(EventType.DELIVERY_STAGE_STARTED, pipeline.getTaskId(),
			stage.name(), pipeline.getCurrentStage().name(), stage.name(),
			"Delivery stage started: " + stage, Map.of("stage", stage.name()));
	}

	private void stageSucceeded(DeliveryPipeline pipeline, DeliveryStage stage, String entityId) {
		auditService.deliveryEvent(EventType.DELIVERY_STAGE_SUCCEEDED, pipeline.getTaskId(),
			stage.name(), pipeline.getCurrentStage().name(), stage.name(),
			"Delivery stage succeeded: " + stage, Map.of("stage", stage.name(),
				"entityId", entityId));
	}

	private void stageWaiting(DeliveryPipeline pipeline, DeliveryStage stage, String reason) {
		pipeline.markWaitingApproval();
		auditService.deliveryEvent(EventType.DELIVERY_WAITING_APPROVAL, pipeline.getTaskId(),
			stage.name(), DeliveryStatus.RUNNING.name(), DeliveryStatus.WAITING_APPROVAL.name(),
			reason, Map.of("stage", stage.name(), "reason", reason));
	}

	private ChangeSet latestChange(String taskId) {
		List<ChangeSet> changes = changeService.getChangesByTask(taskId);
		return changes.isEmpty() ? null : changes.get(0);
	}

	private boolean hasSuccessfulPush(String taskId, String commitId) {
		return remoteGitService.getByTask(taskId).stream().anyMatch(push ->
			push.getStatus() == RemoteStatus.SUCCESS && commitId.equals(push.getCommitId()));
	}

	private DeliveryFailureClass classify(RuntimeException exception) {
		String message = message(exception).toLowerCase();
		if (message.contains("timeout") || message.contains("timed out")
				|| message.contains("connection refused") || message.contains("network")
				|| message.contains("unable to access")) {
			return DeliveryFailureClass.TRANSIENT;
		}
		if (message.contains("approval") || message.contains("rejected")
				|| message.contains("read_only") || message.contains("workspace")) {
			return DeliveryFailureClass.HUMAN_REQUIRED;
		}
		return DeliveryFailureClass.RECOVERABLE;
	}

	private String gateReason(QualityGateResult gate) {
		if (gate.getReasons() == null || gate.getReasons().isEmpty()) {
			return gate.getDecision().name();
		}
		return gate.getReasons().get(0).message();
	}

	private String message(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}
}
