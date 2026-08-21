package com.aidevos.orchestrator.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.InMemoryCommitRepository;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.human.InMemoryHumanApprovalRepository;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.qualitygate.QualityGateDecision;
import com.aidevos.orchestrator.qualitygate.QualityGateResult;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.qualitygate.InMemoryQualityGateRepository;
import com.aidevos.orchestrator.qualitygate.QualityGatePolicy;
import com.aidevos.orchestrator.remote.InMemoryRemotePushApprovalRepository;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.validation.security.InMemorySecurityReportRepository;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.validation.InMemoryValidationRepository;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validationplan.ChangeAnalyzer;
import com.aidevos.orchestrator.validationplan.InMemoryValidationRunResultRepository;
import com.aidevos.orchestrator.validationplan.LocalValidationSelector;
import com.aidevos.orchestrator.validationplan.TestCatalogService;
import com.aidevos.orchestrator.validationplan.ValidationPlanComparator;
import com.aidevos.orchestrator.validationplan.ValidationPlanExecutionService;
import com.aidevos.orchestrator.validationplan.ValidationPlanService;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.RiskLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V1-C DELIVERY-INTEGRATION：DeliveryPipeline 真正使用
 * Multi-Mode Validation Planning (AUTO) + Deterministic Validation Execution。
 */
class DeliveryValidationBridgeTest {

	private static final String TASK_ID = "task-1";
	private static final String TASK_BRANCH = "ai-dev-os/task/task-1";
	private static final String ORIGIN = "origin\tfile:///tmp/bare.git (fetch)\n";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	@TempDir
	java.nio.file.Path executionWorkspaceDir;

	private InMemoryDeliveryPipelineRepository pipelineRepository;
	private InMemoryChangeRepositoryHolder changeHolder;
	private ChangeService changeService;
	private InMemoryValidationRepository validationRepository;
	private ValidationService validationService;
	private ValidationPlanService validationPlanService;
	private ValidationPlanExecutionService executionService;
	private CommandExecutor commandExecutor;
	private ExecutionWorkspacePromotionService promotion;
	private QualityGateService qualityGateService;
	private DeliveryPipelineService pipelineService;
	private AuditService auditService;
	private GitCommandExecutor gitCommandExecutor;
	private WorkspaceService workspaceService;
	private TaskCenterService taskCenterService;
	private CiService ciService;
	private PullRequestService pullRequestService;

	/** holder：pipeline 与 changeService 需要同一个 repository。 */
	private static final class InMemoryChangeRepositoryHolder {
		final com.aidevos.orchestrator.change.InMemoryChangeRepository value =
			new com.aidevos.orchestrator.change.InMemoryChangeRepository();
	}

	@BeforeEach
	void setUp() throws Exception {
		pipelineRepository = new InMemoryDeliveryPipelineRepository();
		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);

		workspaceService = mock(WorkspaceService.class);
		gitCommandExecutor = mock(GitCommandExecutor.class);
		taskCenterService = mock(TaskCenterService.class);
		when(taskCenterService.getTask(TASK_ID)).thenReturn(Optional.of(
			new TaskRecord(TASK_ID, "t", "d", "project-a", "workspace-1",
				ExecutionMode.READ_WRITE)));

		changeHolder = new InMemoryChangeRepositoryHolder();
		changeService = new ChangeService(changeHolder.value, workspaceService, auditService);

		validationRepository = new InMemoryValidationRepository();
		commandExecutor = mock(CommandExecutor.class);
		when(commandExecutor.execute(ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(success());

		promotion = mock(ExecutionWorkspacePromotionService.class);
		ExecutionWorkspace workspace = mock(ExecutionWorkspace.class);
		when(workspace.getId()).thenReturn("workspace-1");
		when(workspace.getStatus()).thenReturn(ExecutionWorkspaceStatus.COMPLETED);
		when(workspace.getExecutionWorkspace()).thenReturn(executionWorkspaceDir.toString());
		when(workspace.getExecutionBranch()).thenReturn(TASK_BRANCH);
		when(workspace.getBaseRevision()).thenReturn("base-1");
		when(promotion.findWorkspace(TASK_ID)).thenReturn(workspace);
		when(promotion.changeFingerprint(TASK_ID)).thenReturn("change-fp-1");

		executionService = new ValidationPlanExecutionService(commandExecutor, promotion,
			new InMemoryValidationRunResultRepository());

		validationPlanService = mock(ValidationPlanService.class);
		when(validationPlanService.generate(anyString(), anyString(), anyList(),
			eq(ValidationMode.AUTO), isNull())).thenReturn(plan("change-any"));

		validationService = new ValidationService(validationRepository, taskCenterService,
			workspaceService, mock(com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector.class),
			List.of(), mock(com.aidevos.orchestrator.validation.ValidationEvidenceService.class),
			auditService);
		validationService.setChangeService(changeService);
		validationService.setExecutionWorkspaces(promotion);
		validationService.setValidationPlanServices(validationPlanService, executionService);

		qualityGateService = spy(new QualityGateService(
			new InMemoryQualityGateRepository(), validationRepository,
			new InMemorySecurityReportRepository(), new QualityGatePolicy(),
			new InMemoryHumanApprovalRepository(), taskCenterService, auditService,
			new ObjectMapper()));

		CommitService commitService = new CommitService(new InMemoryCommitRepository(),
			changeService, workspaceService, gitCommandExecutor, auditService);
		RemotePushApprovalService approvalService = new RemotePushApprovalService(
			new InMemoryRemotePushApprovalRepository(), auditService);
		RemoteGitService remoteGitService = new RemoteGitService(
			new com.aidevos.orchestrator.remote.InMemoryRemoteRepository(), commitService,
			workspaceService, gitCommandExecutor, auditService, approvalService);
		commitService.setRemoteGitService(remoteGitService);
		pullRequestService = mock(PullRequestService.class);
		ciService = mock(CiService.class);
		pipelineService = new DeliveryPipelineService(pipelineRepository, changeService,
			validationService, qualityGateService, commitService, remoteGitService,
			approvalService, pullRequestService, ciService, auditService);
		approvalService.setDeliveryPipelineService(pipelineService);
		changeService.setDeliveryPipelineService(pipelineService);

		Workspace ws = new Workspace("workspace-1", "project-a", "/tmp/repo", TASK_BRANCH,
			WorkspaceStatus.READY, NOW, NOW);
		when(workspaceService.getWorkspace("workspace-1")).thenReturn(Optional.of(ws));
		when(workspaceService.checkGitStatus("workspace-1"))
			.thenReturn(new GitStatus(TASK_BRANCH, 1, 0, 0));
		when(workspaceService.getGitDiff("workspace-1"))
			.thenReturn(new GitDiff(1, 1, 0, "1 file changed"));
		when(workspaceService.getGitDiffContent("workspace-1"))
			.thenReturn("diff --git a/a.txt b/a.txt\n");

		java.nio.file.Files.createDirectories(
			executionWorkspaceDir.resolve("services/orchestrator"));
	}

	private CommandResult success() {
		CommandResult result = new CommandResult();
		result.setSuccess(true);
		result.setExitCode(0);
		result.setOutput("ok");
		return result;
	}

	private CommandResult failed(int exitCode) {
		CommandResult result = new CommandResult();
		result.setSuccess(false);
		result.setExitCode(exitCode);
		result.setOutput("failure output");
		result.setError("boom");
		return result;
	}

	private ValidationPlan plan(String changeSetId) {
		ValidationCheck compile = new ValidationCheck(CheckType.BACKEND_COMPILE, "maven",
			"services/orchestrator", List.of("compile"), true, "mandatory",
			CheckSource.MANDATORY, 300);
		ValidationCheck test = new ValidationCheck(CheckType.MAVEN_TARGETED_TEST, "maven",
			"services/orchestrator", List.of("test", "-Dtest=FooServiceTest"), true,
			"targeted", CheckSource.MANDATORY, 300);
		return new ValidationPlan(TASK_ID, changeSetId, ValidationMode.AUTO, "TARGETED",
			RiskLevel.MEDIUM, ConfidenceLevel.HIGH, List.of(compile, test), null, null,
			List.of(), false, NOW);
	}

	private ChangeSet approvedChange() {
		ChangeSet change = changeService.createChange(TASK_ID, "workspace-1", "project-a",
			"exec-1", TASK_BRANCH);
		changeService.startReview(change.getChangeId());
		change.markApproved("user-1");
		changeHolder.value.save(change);
		return changeService.getChange(change.getChangeId()).orElseThrow();
	}

	private void wireCommitToSuccess(String changeId) {
		when(gitCommandExecutor.commit("/tmp/repo",
			"AI change " + changeId + " for task task-1")).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes("/tmp/repo")).thenReturn(ORIGIN);
	}

	/** 1. Change APPROVED → AUTO ValidationPlan → deterministic execution → Validation SUCCESS */
	@Test
	void startDeliveryBuildsAutoPlanAndExecutesDeterministically() {
		ChangeSet change = approvedChange();

		ValidationRun run = validationService.startDelivery(change.getChangeId());

		assertEquals(ValidationStatus.SUCCESS, run.getStatus());
		verify(validationPlanService).generate(eq(TASK_ID), eq(change.getChangeId()), anyList(),
			eq(ValidationMode.AUTO), isNull());
		verify(commandExecutor, times(2)).execute(ArgumentMatchers.any(CommandOptions.class));
		assertEquals("AUTO", run.getMetadata().get("planMode"));
		assertEquals("TARGETED", run.getMetadata().get("planProfile"));
		assertEquals("HIGH", run.getMetadata().get("planConfidence"));
		assertNotNull(run.getMetadata().get("planFingerprint"));
		assertEquals(2, run.getChecks().size());
		assertTrue(run.getChecks().stream()
			.allMatch(check -> check.getStatus() == ValidationStatus.SUCCESS));
	}

	/** 2. ValidationPlan compile + targeted test SUCCESS → Quality Gate 被调用 */
	@Test
	void validationSuccessTriggersQualityGate() {
		ChangeSet change = approvedChange();
		wireCommitToSuccess(change.getChangeId());

		DeliveryPipeline pipeline = pipelineService.advance(TASK_ID);

		assertTrue(pipeline.getCurrentStage() == DeliveryStage.COMMITTING
			|| pipeline.getCurrentStage() == DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL,
			"Quality gate PASS 后应继续推进，实际 stage=" + pipeline.getCurrentStage() + " reason=" + pipeline.getFailureReason());
		verify(qualityGateService).evaluate(anyString());
		QualityGateResult gate = qualityGateService.byValidation(
			pipeline.getValidationRunId()).stream()
			.max(java.util.Comparator.comparing(QualityGateResult::getCreatedAt))
			.orElseThrow();
		assertEquals(QualityGateDecision.PASS, gate.getDecision());
	}

	/** 3. Validation FAILED → Quality Gate 不调用 → Pipeline FAILED + 结构化 failureReason */
	@Test
	void validationFailedSkipsGateAndFailsPipelineWithStructuredReason() {
		ChangeSet change = approvedChange();
		when(commandExecutor.execute(ArgumentMatchers.any(CommandOptions.class)))
			.thenReturn(failed(1));

		DeliveryPipeline pipeline = pipelineService.advance(TASK_ID);

		assertEquals(DeliveryStatus.FAILED, pipeline.getStatus());
		assertEquals(DeliveryStage.FAILED, pipeline.getCurrentStage());
		assertTrue(pipeline.getFailureReason().contains("BACKEND_COMPILE"),
			"failureReason 必须含 checkType: " + pipeline.getFailureReason());
		assertTrue(pipeline.getFailureReason().contains("BUILD_FAILED"),
			"failureReason 必须含 errorCode: " + pipeline.getFailureReason());
		assertTrue(pipeline.getFailureReason().contains("exitCode=1"),
			"failureReason 必须含 exitCode: " + pipeline.getFailureReason());
		verify(qualityGateService, never()).evaluate(anyString());
	}

	/** 4. Validation REUSED → tool calls = 0 → Quality Gate 继续 */
	@Test
	void validationReusedWithZeroToolCallsAndGateContinues() {
		ChangeSet change = approvedChange();
		wireCommitToSuccess(change.getChangeId());

		ValidationRun first = validationService.startDelivery(change.getChangeId());
		assertEquals(ValidationStatus.SUCCESS, first.getStatus());
		ValidationRun second = validationService.startDelivery(change.getChangeId());

		assertEquals(ValidationStatus.SUCCESS, second.getStatus(),
			"同 change+plan 必须 REUSE（保持 SUCCESS）");
		verify(commandExecutor, times(2)).execute(ArgumentMatchers.any(CommandOptions.class));

		DeliveryPipeline pipeline = pipelineService.advance(TASK_ID);
		assertTrue(pipeline.getCurrentStage() == DeliveryStage.COMMITTING
			|| pipeline.getCurrentStage() == DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL,
			"REUSED 后 Quality Gate 应继续，实际 stage=" + pipeline.getCurrentStage() + " reason=" + pipeline.getFailureReason());
	}

	/** 5. Delivery reconcile/advance 重复调用 → 不重复执行 SUCCESS validation */
	@Test
	void repeatedAdvanceNeverReExecutesValidation() {
		ChangeSet change = approvedChange();
		wireCommitToSuccess(change.getChangeId());

		DeliveryPipeline first = pipelineService.advance(TASK_ID);
		assertEquals(DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL, first.getCurrentStage(), "reason=" + first.getFailureReason());

		DeliveryPipeline again = pipelineService.advance(TASK_ID);

		assertEquals(DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL, again.getCurrentStage(), "reason=" + again.getFailureReason());
		verify(validationPlanService, times(1)).generate(anyString(), anyString(),
			anyList(), eq(ValidationMode.AUTO), isNull());
		verify(commandExecutor, times(2)).execute(ArgumentMatchers.any(CommandOptions.class));
	}

	/** 6. AI selector unavailable → AUTO broader-local plan → deterministic execution → Delivery 继续 */
	@Test
	void aiUnavailableFallsBackToBroaderLocalAndDeliveryContinues() {
		ChangeSet change = approvedChange();
		wireCommitToSuccess(change.getChangeId());
		com.aidevos.orchestrator.validationplan.AiValidationSelector aiSelector =
			mock(com.aidevos.orchestrator.validationplan.AiValidationSelector.class);
		when(aiSelector.isAvailable()).thenReturn(true);
		when(aiSelector.suggest(any()))
			.thenThrow(new com.aidevos.orchestrator.validationplan.DisabledAiValidationSelector.AiUnavailableException(
				"provider down"));
		ValidationPlanService realPlanService = new ValidationPlanService(
			new ChangeAnalyzer(), new LocalValidationSelector(), aiSelector,
			new ValidationPlanComparator(), new TestCatalogService("/nonexistent"));
		validationService.setValidationPlanServices(realPlanService, executionService);
		// pom.xml 修改 → MEDIUM confidence → AUTO 尝试 AI → AI 不可用 → broader-local fallback
		when(workspaceService.getGitDiffContent("workspace-1")).thenReturn(
			"diff --git a/services/orchestrator/pom.xml b/services/orchestrator/pom.xml\n");
		changeHolder.value.list().stream()
			.filter(c -> change.getChangeId().equals(c.getChangeId()))
			.forEach(c -> {
				com.aidevos.orchestrator.change.ChangeSet updated =
					new com.aidevos.orchestrator.change.ChangeSet(c.getChangeId(),
						c.getTaskId(), c.getWorkspaceId(), c.getProjectId(),
						c.getExecutionId(), c.getBranch(),
						"diff --git a/services/orchestrator/pom.xml b/services/orchestrator/pom.xml\n",
						c.getDiffStat(), c.getFilesChanged(), c.getInsertions(),
						c.getDeletions(), c.getModified(), c.getAdded(),
						c.getDeleted(), c.getCreatedAt());
				changeHolder.value.save(updated);
				changeService.startReview(updated.getChangeId());
				updated.markApproved("user-1");
				changeHolder.value.save(updated);
			});

		DeliveryPipeline pipeline = pipelineService.advance(TASK_ID);

		verify(aiSelector).suggest(any());
		assertTrue(pipeline.getCurrentStage() == DeliveryStage.COMMITTING
			|| pipeline.getCurrentStage() == DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL,
			"AI fallback 后 Delivery 应继续，实际 stage=" + pipeline.getCurrentStage() + " reason=" + pipeline.getFailureReason());
		ValidationRun run = validationService.get(pipeline.getValidationRunId());
		assertEquals(ValidationStatus.SUCCESS, run.getStatus());
	}

	/** 7. 完整 service-level：Change APPROVED → Validation → Gate → COMMITTING → WAITING_REMOTE_PUSH_APPROVAL */
	@Test
	void fullDeliveryReachesCommittingAndWaitsForRemotePushApproval() {
		ChangeSet change = approvedChange();
		wireCommitToSuccess(change.getChangeId());

		DeliveryPipeline pipeline = pipelineService.advance(TASK_ID);

		assertEquals(DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL, pipeline.getCurrentStage(), "reason=" + pipeline.getFailureReason());
		assertEquals(DeliveryStatus.WAITING_APPROVAL, pipeline.getStatus());
		assertNotNull(pipeline.getCommitId());
		assertNotNull(pipeline.getRemotePushApprovalId());
		verify(gitCommandExecutor).commit("/tmp/repo",
			"AI change " + change.getChangeId() + " for task task-1");
		assertFalse(pipeline.getFailureReason().contains("Validation failed"));
	}
}
