package com.aidevos.orchestrator.delivery;

import java.time.Instant;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.InMemoryCommitRepository;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.pr.PullRequestStatus;
import com.aidevos.orchestrator.qualitygate.QualityGateDecision;
import com.aidevos.orchestrator.qualitygate.QualityGateResult;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.remote.InMemoryRemotePushApprovalRepository;
import com.aidevos.orchestrator.remote.InMemoryRemoteRepository;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApproval;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.remote.RemotePushApprovalStatus;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Delivery pipeline orchestration tests: automatic advance through
 * ChangeSet -> Validation -> Quality Gate -> Commit -> Remote Push Approval ->
 * Push -> PR -> CI -> Complete, plus idempotent resume from persisted state.
 */
class DeliveryPipelineServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
	private static final String TASK_BRANCH = "ai-dev-os/task/task-1";
	private static final String ORIGIN = "origin\tfile:///tmp/bare.git (fetch)\n"
		+ "origin\tfile:///tmp/bare.git (push)\n";

	private InMemoryDeliveryPipelineRepository pipelineRepository;
	private InMemoryAuditRepository auditRepository;
	private InMemoryCommitRepository commitRepository;
	private WorkspaceService workspaceService;
	private GitCommandExecutor gitCommandExecutor;
	private ChangeService changeService;
	private CommitService commitService;
	private RemoteGitService remoteGitService;
	private RemotePushApprovalService approvalService;
	private ValidationService validationService;
	private QualityGateService qualityGateService;
	private PullRequestService pullRequestService;
	private CiService ciService;
	private DeliveryPipelineService pipelineService;

	@BeforeEach
	void setUp() {
		pipelineRepository = new InMemoryDeliveryPipelineRepository();
		auditRepository = new InMemoryAuditRepository();
		workspaceService = mock(WorkspaceService.class);
		gitCommandExecutor = mock(GitCommandExecutor.class);
		validationService = mock(ValidationService.class);
		qualityGateService = mock(QualityGateService.class);
		pullRequestService = mock(PullRequestService.class);
		ciService = mock(CiService.class);
		AuditService auditService = new AuditService(auditRepository);
		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		commitRepository = new InMemoryCommitRepository();
		commitService = new CommitService(commitRepository, changeService,
			workspaceService, gitCommandExecutor, auditService);
		approvalService = new RemotePushApprovalService(
			new InMemoryRemotePushApprovalRepository(), auditService);
		remoteGitService = new RemoteGitService(new InMemoryRemoteRepository(), commitService,
			workspaceService, gitCommandExecutor, auditService, approvalService);
		commitService.setRemoteGitService(remoteGitService);
		pipelineService = new DeliveryPipelineService(pipelineRepository, changeService,
			validationService, qualityGateService, commitService, remoteGitService,
			approvalService, pullRequestService, ciService, auditService);
		approvalService.setDeliveryPipelineService(pipelineService);

		Workspace workspace = new Workspace("workspace-1", "project-a", "/tmp/repo", TASK_BRANCH,
			WorkspaceStatus.READY, NOW, NOW);
		when(workspaceService.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		when(workspaceService.checkGitStatus("workspace-1")).thenReturn(
			new GitStatus(TASK_BRANCH, 1, 0, 0));
		when(workspaceService.getGitDiff("workspace-1")).thenReturn(
			new GitDiff(1, 1, 0, "1 file changed"));
		when(workspaceService.getGitDiffContent("workspace-1")).thenReturn(
			"diff --git a/a.txt b/a.txt\n");
	}

	@Test
	void changeReadyAdvancesValidationGateCommitAndStopsAtRemotePushApproval() {
		ChangeSet change = approvedChange();
		ValidationRun run = successRun(change.getChangeId());
		QualityGateResult gate = passGate(change.getChangeId(), run.getValidationRunId());
		when(validationService.startDelivery(change.getChangeId())).thenReturn(run);
		when(validationService.get(run.getValidationRunId())).thenReturn(run);
		when(qualityGateService.evaluate(run.getValidationRunId())).thenReturn(gate);
		when(qualityGateService.get(gate.getGateResultId())).thenReturn(gate);
		when(gitCommandExecutor.commit("/tmp/repo", commitMessage(change.getChangeId())))
			.thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(ORIGIN);

		DeliveryPipeline pipeline = pipelineService.advance("task-1");

		assertEquals(DeliveryStatus.WAITING_APPROVAL, pipeline.getStatus());
		assertEquals(DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL, pipeline.getCurrentStage());
		assertEquals(change.getChangeId(), pipeline.getChangeSetId());
		assertEquals(run.getValidationRunId(), pipeline.getValidationRunId());
		assertEquals(gate.getGateResultId(), pipeline.getQualityGateId());
		assertNotNull(pipeline.getCommitId());
		assertNotNull(pipeline.getRemotePushApprovalId());
		RemotePushApproval approval = approvalService.get(pipeline.getRemotePushApprovalId());
		assertEquals(RemotePushApprovalStatus.PENDING, approval.getStatus());
		verify(gitCommandExecutor, never()).push(anyString(), anyString(), anyString());
		verify(pullRequestService, never()).createPullRequest(anyString(), any());
		verify(ciService, never()).check(anyString(), anyString());
		assertEquals(com.aidevos.orchestrator.change.ChangeStatus.COMMITTED,
			changeService.getChange(change.getChangeId()).orElseThrow().getStatus());
	}

	@Test
	void qualityGateRequireApprovalStopsAtWaitingApprovalWithoutCommit() {
		ChangeSet change = approvedChange();
		ValidationRun run = successRun(change.getChangeId());
		QualityGateResult gate = new QualityGateResult();
		gate.setGateResultId("gate-approval");
		gate.setValidationRunId(run.getValidationRunId());
		gate.setTaskId("task-1");
		gate.setChangeSetId(change.getChangeId());
		gate.setDecision(QualityGateDecision.REQUIRE_APPROVAL);
		gate.setStatus(com.aidevos.orchestrator.qualitygate.QualityGateStatus.EVALUATED);
		when(validationService.startDelivery(change.getChangeId())).thenReturn(run);
		when(validationService.get(run.getValidationRunId())).thenReturn(run);
		when(qualityGateService.evaluate(run.getValidationRunId())).thenReturn(gate);
		when(qualityGateService.get(gate.getGateResultId())).thenReturn(gate);

		DeliveryPipeline pipeline = pipelineService.advance("task-1");

		assertEquals(DeliveryStatus.WAITING_APPROVAL, pipeline.getStatus());
		assertEquals(DeliveryStage.QUALITY_GATE, pipeline.getCurrentStage());
		verify(gitCommandExecutor, never()).commit(anyString(), anyString());
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.DELIVERY_WAITING_APPROVAL));
	}

	@Test
	void approvedRemotePushAdvancesPushPrAndCiToCompleteWithoutMerge() {
		ChangeSet change = approvedChange();
		ValidationRun run = successRun(change.getChangeId());
		QualityGateResult gate = passGate(change.getChangeId(), run.getValidationRunId());
		when(validationService.startDelivery(change.getChangeId())).thenReturn(run);
		when(validationService.get(run.getValidationRunId())).thenReturn(run);
		when(qualityGateService.evaluate(run.getValidationRunId())).thenReturn(gate);
		when(qualityGateService.get(gate.getGateResultId())).thenReturn(gate);
		when(gitCommandExecutor.commit("/tmp/repo", commitMessage(change.getChangeId())))
			.thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(ORIGIN);
		when(gitCommandExecutor.push("/tmp/repo", "origin", TASK_BRANCH)).thenReturn(true);
		PullRequestRecord pr = new PullRequestRecord("pr-1", "task-1", "commit-1",
			"remote-1", TASK_BRANCH, "main", "title", "desc", "https://pr", NOW);
		pr.markOpened();
		when(pullRequestService.getByCommit(anyString())).thenReturn(Optional.empty());
		when(pullRequestService.createPullRequest(anyString(), any()))
			.thenAnswer(invocation -> {
				PullRequestRecord created = new PullRequestRecord("pr-1", "task-1",
					invocation.getArgument(0), "remote-1", TASK_BRANCH, "main", "title",
					"desc", "https://pr", NOW);
				created.markOpened();
				return created;
			});
		when(pullRequestService.get("pr-1")).thenReturn(Optional.of(pr));
		CiRunRecord ci = ciRun("ci-1", "pr-1", CiStatus.SUCCESS);
		when(ciService.check("pr-1", "abc123def")).thenReturn(ci);
		when(ciService.get("ci-1")).thenReturn(Optional.of(ci));

		DeliveryPipeline first = pipelineService.advance("task-1");
		RemotePushApproval approval = approvalService.get(first.getRemotePushApprovalId());
		approvalService.approve(approval.getApprovalId());

		DeliveryPipeline pipeline = pipelineService.get("task-1");
		assertEquals(DeliveryStatus.COMPLETE, pipeline.getStatus());
		assertEquals(DeliveryStage.DELIVERY_COMPLETE, pipeline.getCurrentStage());
		assertEquals("pr-1", pipeline.getPullRequestId());
		assertEquals("ci-1", pipeline.getCiRunId());
		verify(pullRequestService, times(1)).createPullRequest(anyString(), any());
		verify(pullRequestService, never()).merge(anyString());
		assertTrue(events().stream().anyMatch(event -> event.type()
			== EventType.DELIVERY_COMPLETED && "task-1".equals(event.taskId())));
	}

	@Test
	void existingOpenPrIsReusedAndNeverDuplicated() {
		ChangeSet change = approvedChange();
		ValidationRun run = successRun(change.getChangeId());
		QualityGateResult gate = passGate(change.getChangeId(), run.getValidationRunId());
		when(validationService.startDelivery(change.getChangeId())).thenReturn(run);
		when(validationService.get(run.getValidationRunId())).thenReturn(run);
		when(qualityGateService.evaluate(run.getValidationRunId())).thenReturn(gate);
		when(qualityGateService.get(gate.getGateResultId())).thenReturn(gate);
		when(gitCommandExecutor.commit("/tmp/repo", commitMessage(change.getChangeId())))
			.thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(ORIGIN);
		when(gitCommandExecutor.push("/tmp/repo", "origin", TASK_BRANCH)).thenReturn(true);
		PullRequestRecord existing = new PullRequestRecord("pr-existing", "task-1",
			"commit-1", "remote-1", TASK_BRANCH, "main", "title", "desc", null, NOW);
		existing.markOpened();
		when(pullRequestService.getByCommit(anyString())).thenReturn(Optional.of(existing));
		when(pullRequestService.get("pr-existing")).thenReturn(Optional.of(existing));
		CiRunRecord ci = ciRun("ci-1", "pr-existing", CiStatus.SUCCESS);
		when(ciService.check("pr-existing", "abc123def")).thenReturn(ci);
		when(ciService.get("ci-1")).thenReturn(Optional.of(ci));

		pipelineService.advance("task-1");
		DeliveryPipeline pipeline = pipelineService.get("task-1");
		approvalService.approve(approvalService.get(pipeline.getRemotePushApprovalId())
			.getApprovalId());

		assertEquals("pr-existing", pipelineService.get("task-1").getPullRequestId());
		verify(pullRequestService, never()).createPullRequest(anyString(), any());
	}

	@Test
	void ciSuccessCompletesDelivery() {
		DeliveryPipeline seeded = seededPipeline();
		PullRequestRecord pr = new PullRequestRecord("pr-1", "task-1", "commit-1",
			"remote-1", TASK_BRANCH, "main", "title", "desc", null, NOW);
		pr.markOpened();
		when(pullRequestService.get("pr-1")).thenReturn(Optional.of(pr));
		CiRunRecord ci = ciRun("ci-1", "pr-1", CiStatus.SUCCESS);
		when(ciService.check("pr-1", "abc123def")).thenReturn(ci);
		when(ciService.get("ci-1")).thenReturn(Optional.of(ci));

		DeliveryPipeline pipeline = pipelineService.advance("task-1");

		assertEquals(DeliveryStatus.COMPLETE, pipeline.getStatus());
		assertEquals(DeliveryStage.DELIVERY_COMPLETE, pipeline.getCurrentStage());
		assertEquals("ci-1", pipeline.getCiRunId());
	}

	@Test
	void ciFailedMarksPipelineFailedWithoutMergeAndStopsRechecking() {
		DeliveryPipeline seeded = seededPipeline();
		PullRequestRecord pr = new PullRequestRecord("pr-1", "task-1", "commit-1",
			"remote-1", TASK_BRANCH, "main", "title", "desc", null, NOW);
		pr.markOpened();
		when(pullRequestService.get("pr-1")).thenReturn(Optional.of(pr));
		CiRunRecord ci = ciRun("ci-1", "pr-1", CiStatus.FAILED);
		when(ciService.check("pr-1", "abc123def")).thenReturn(ci);
		when(ciService.get("ci-1")).thenReturn(Optional.of(ci));

		pipelineService.advance("task-1");
		DeliveryPipeline pipeline = pipelineService.get("task-1");

		assertEquals(DeliveryStatus.FAILED, pipeline.getStatus());
		assertEquals(DeliveryFailureClass.HUMAN_REQUIRED, pipeline.getFailureClass());
		assertTrue(pipeline.getFailureReason().contains("CI FAILED"));
		verify(pullRequestService, never()).merge(anyString());

		pipelineService.advance("task-1");
		verify(ciService, times(1)).check(anyString(), anyString());
		assertEquals(DeliveryStatus.FAILED, pipelineService.get("task-1").getStatus());
	}

	@Test
	void restartResumesFromPersistedStateWithoutRepeatingCommitOrPush() {
		ChangeSet change = approvedChange();
		ValidationRun run = successRun(change.getChangeId());
		QualityGateResult gate = passGate(change.getChangeId(), run.getValidationRunId());
		when(validationService.startDelivery(change.getChangeId())).thenReturn(run);
		when(validationService.get(run.getValidationRunId())).thenReturn(run);
		when(qualityGateService.evaluate(run.getValidationRunId())).thenReturn(gate);
		when(qualityGateService.get(gate.getGateResultId())).thenReturn(gate);
		when(gitCommandExecutor.commit("/tmp/repo", commitMessage(change.getChangeId())))
			.thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(ORIGIN);
		when(gitCommandExecutor.push("/tmp/repo", "origin", TASK_BRANCH)).thenReturn(true);
		PullRequestRecord pr = new PullRequestRecord("pr-1", "task-1", "commit-1",
			"remote-1", TASK_BRANCH, "main", "title", "desc", "https://pr", NOW);
		pr.markOpened();
		when(pullRequestService.getByCommit(anyString())).thenReturn(Optional.empty());
		when(pullRequestService.createPullRequest(anyString(), any()))
			.thenAnswer(invocation -> {
				PullRequestRecord created = new PullRequestRecord("pr-1", "task-1",
					invocation.getArgument(0), "remote-1", TASK_BRANCH, "main", "title",
					"desc", "https://pr", NOW);
				created.markOpened();
				return created;
			});
		when(pullRequestService.get("pr-1")).thenReturn(Optional.of(pr));
		CiRunRecord ci = ciRun("ci-1", "pr-1", CiStatus.SUCCESS);
		when(ciService.check("pr-1", "abc123def")).thenReturn(ci);
		when(ciService.get("ci-1")).thenReturn(Optional.of(ci));

		DeliveryPipeline first = pipelineService.advance("task-1");
		verify(gitCommandExecutor, times(1)).commit(anyString(), anyString());
		RemotePushApproval approval = approvalService.get(first.getRemotePushApprovalId());
		approvalService.approve(approval.getApprovalId());

		// Simulated restart: a fresh service over the same persisted stores.
		DeliveryPipelineService restarted = new DeliveryPipelineService(pipelineRepository,
			changeService, validationService, qualityGateService, commitService, remoteGitService,
			approvalService, pullRequestService, ciService,
			new AuditService(auditRepository));
		DeliveryPipeline pipeline = restarted.advance("task-1");

		assertEquals(DeliveryStatus.COMPLETE, pipeline.getStatus());
		verify(gitCommandExecutor, times(1)).commit(anyString(), anyString());
		verify(gitCommandExecutor, times(1)).push("/tmp/repo", "origin", TASK_BRANCH);
		verify(pullRequestService, times(1)).createPullRequest(anyString(), any());
	}

	private DeliveryPipeline seededPipeline() {
		com.aidevos.orchestrator.commit.CommitRecord commit = new com.aidevos.orchestrator.commit.CommitRecord(
			"commit-1", "change-1", "task-1", "workspace-1", TASK_BRANCH,
			"AI change change-1 for task task-1", NOW);
		commit.markCommitting();
		commit.markSuccess("abc123def");
		commitRepository.save(commit);
		RemotePushApproval approval = new RemotePushApproval("approval-1", "task-1",
			"workspace-1", TASK_BRANCH, "commit-1", "abc123def", "origin",
			"refs/heads/" + TASK_BRANCH, NOW);
		approval.approve();
		approval.consume();
		DeliveryPipeline seeded = DeliveryPipeline.restore("task-1", "change-1",
			"workspace-1", DeliveryStage.CI_CHECKING, DeliveryStatus.RUNNING, "validation-1",
			"gate-1", "commit-1", "approval-1", "remote-1", "pr-1", null, null, "",
			NOW, NOW, null);
		pipelineRepository.save(seeded);
		return seeded;
	}

	private ChangeSet approvedChange() {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1", TASK_BRANCH);
		changeService.startReview(change.getChangeId());
		changeService.approve(change.getChangeId(), "user-1");
		return changeService.getChange(change.getChangeId()).orElseThrow();
	}

	private ValidationRun successRun(String changeSetId) {
		ValidationRun run = new ValidationRun("validation-1", "task-1", "project-a",
			"workspace-1", null, "exec-1");
		run.setChangeSetId(changeSetId);
		run.setDelivery(true);
		run.setStatus(ValidationStatus.SUCCESS);
		return run;
	}

	private QualityGateResult passGate(String changeSetId, String validationRunId) {
		QualityGateResult gate = new QualityGateResult();
		gate.setGateResultId("gate-1");
		gate.setValidationRunId(validationRunId);
		gate.setTaskId("task-1");
		gate.setChangeSetId(changeSetId);
		gate.setDecision(QualityGateDecision.PASS);
		gate.setStatus(com.aidevos.orchestrator.qualitygate.QualityGateStatus.EVALUATED);
		return gate;
	}

	private CiRunRecord ciRun(String ciRunId, String pullRequestId, CiStatus status) {
		CiRunRecord run = new CiRunRecord(ciRunId, "task-1", pullRequestId, "mock",
			TASK_BRANCH, "abc123def", NOW);
		run.markRunning();
		if (status == CiStatus.SUCCESS) {
			run.markSuccess();
		}
		else if (status == CiStatus.FAILED) {
			run.markFailed();
		}
		return run;
	}

	private String commitMessage(String changeId) {
		return "AI change " + changeId + " for task task-1";
	}

	private java.util.List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

}
