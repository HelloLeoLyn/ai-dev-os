package com.aidevos.orchestrator.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.InMemoryCommitRepository;
import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import com.aidevos.orchestrator.delivery.InMemoryDeliveryPipelineRepository;
import com.aidevos.orchestrator.diagnosis.FailureCategory;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosis;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosisService;
import com.aidevos.orchestrator.diagnosis.RecommendedAction;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.InMemoryExecutionAttemptRepository;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.qualitygate.QualityGateDecision;
import com.aidevos.orchestrator.qualitygate.QualityGateResult;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.remote.InMemoryRemotePushApprovalRepository;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RECOVERY-AUTO-TRIGGER-CLOSEOUT：
 * 真实 Failure 出现后自动 evaluate 一次（不依赖用户点 API），
 * 且不形成递归循环、hook 异常不覆盖原始 failure。
 */
class RecoveryAutoTriggerTest {

	private static final String TASK_ID = "task-1";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private FailureDiagnosisService diagnosisService;
	private TaskCenterService taskCenterService;
	private DeliveryPipelineService deliveryPipelineService;
	private ExecutionEngine recoveryExecutionEngine;
	private RecoveryCoordinator coordinator;
	private InMemoryRecoveryAttemptRepository recoveryRepository;
	private AuditService auditService;

	@BeforeEach
	void setUp() {
		auditService = new AuditService(new InMemoryAuditRepository());
		diagnosisService = mock(FailureDiagnosisService.class);
		taskCenterService = mock(TaskCenterService.class);
		TaskRecord task = mock(TaskRecord.class);
		when(task.getStatus()).thenReturn(TaskStatus.RUNNING);
		when(taskCenterService.getTask(TASK_ID)).thenReturn(Optional.of(task));
		recoveryExecutionEngine = mock(ExecutionEngine.class);
		recoveryRepository = new InMemoryRecoveryAttemptRepository();
		coordinator = new RecoveryCoordinator(new RecoveryPolicy(), recoveryRepository,
			diagnosisService, auditService);
		coordinator.setTaskCenterService(taskCenterService);
		coordinator.setExecutionEngine(recoveryExecutionEngine);
		TaskDefinition definition = mock(TaskDefinition.class);
		coordinator.setTaskDefinitionResolverForTests(taskId -> definition);
	}

	private FailureDiagnosis diagnosis(String errorCode, FailureCategory category,
			String source, String fingerprint) {
		return new FailureDiagnosis(TASK_ID, source, "stage", null, errorCode, errorCode,
			category, "summary", "rootCause", List.of("hint"), RecommendedAction.RETRY,
			true, fingerprint, NOW, false, 1, NOW, NOW);
	}

	private ExecutionResult failedResult() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage("boom");
		return result;
	}

	/** 1. Execution transient failure → 自动 evaluate → retry 1 次 */
	@Test
	void executionTransientFailureAutoTriggersRetryOnce() throws Exception {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("NETWORK_TRANSIENT",
			FailureCategory.INFRASTRUCTURE, "EXECUTION", "fp-exec-net"));
		ExecutionResult retryResult = new ExecutionResult();
		retryResult.setSuccess(true);
		retryResult.setMessage("ok");
		when(recoveryExecutionEngine.execute(any(TaskDefinition.class))).thenReturn(retryResult);

		// 真实 ExecutionEngine（失败落点）+ spy coordinator
		AgentResolver agentResolver = mock(AgentResolver.class);
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		when(executor.execute(any())).thenReturn(failedResult());
		AgentDefinition agentDefinition = mock(AgentDefinition.class);
		when(agentDefinition.getName()).thenReturn("coder");
		when(agentResolver.resolve(any(TaskDefinition.class)))
			.thenReturn(new ResolvedAgent(agentDefinition, executor));
		TaskDefinition taskDefinition = mock(TaskDefinition.class);
		when(taskDefinition.getAgentName()).thenReturn("coder");
		when(taskDefinition.getMetadata())
			.thenReturn(Map.of("originalTaskId", TASK_ID, "executionMode", "READ_ONLY"));
		ExecutionEngine engine = new ExecutionEngine(agentResolver,
			mock(ExecutionRecordManager.class), auditService,
			new InMemoryExecutionAttemptRepository());
		engine.setRecoveryCoordinator(coordinator);

		ExecutionResult result = engine.execute(taskDefinition);

		assertNotNull(result);
		assertTrue(!result.isSuccess());
		RecoveryAttempt attempt = recoveryRepository.list().stream()
			.filter(candidate -> candidate.fingerprint().equals("fp-exec-net"))
			.findFirst().orElse(null);
		assertNotNull(attempt, "Execution 失败后必须自动产生 recovery attempt");
		assertEquals(AttemptStatus.SUCCEEDED, attempt.status());
		assertEquals(1, attempt.attemptNumber());
		assertEquals(1, attempt.maxAttempts());
		verify(recoveryExecutionEngine, times(1)).execute(any(TaskDefinition.class));
	}

	/** 2. Validation TEST_FAILED → hook 调用但 policy 不执行 retry */
	@Test
	void validationTestFailedHookRunsButNoAutoRetry() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("TEST_FAILED",
			FailureCategory.CODE, "EXECUTION", "fp-test"));
		DeliveryPipelineService pipelineService = deliveryServiceWithFailingValidation(false);
		pipelineService.setRecoveryCoordinator(coordinator);
		coordinator.setDeliveryPipelineService(pipelineService);

		pipelineService.advance(TASK_ID);

		assertTrue(recoveryRepository.list().isEmpty(),
			"TEST_FAILED 不允许自动 retry：不得产生 attempt");
		verify(recoveryExecutionEngine, never()).execute(any(TaskDefinition.class));
	}

	/** 3. Delivery CI_TRANSIENT → 自动 RETRY_DELIVERY */
	@Test
	void deliveryCiTransientAutoRetriesDelivery() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-ci"));
		DeliveryPipelineService pipelineService = deliveryServiceWithFailingValidation(true);
		pipelineService.setRecoveryCoordinator(coordinator);
		coordinator.setDeliveryPipelineService(pipelineService);

		pipelineService.advance(TASK_ID);

		RecoveryAttempt attempt = recoveryRepository.list().stream()
			.filter(candidate -> candidate.fingerprint().equals("fp-ci"))
			.findFirst().orElse(null);
		assertNotNull(attempt, "CI_TRANSIENT 必须自动产生 recovery attempt");
		assertEquals(AttemptStatus.SUCCEEDED, attempt.status());
		assertTrue(attempt.automatic());
		// 自动 retry 调用了 Delivery authority（第二次 advance：第一次失败，第二次成功推进）
		verify(validationServiceOf(pipelineService), times(2)).startDelivery(anyString());
	}

	/** 4. 同 fingerprint retry 后再次失败 → hook 再触发但 EXHAUSTED，不执行第二次 */
	@Test
	void reentrantFailureExhaustsBudgetAndStops() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-loop"));
		DeliveryPipelineService pipelineService = deliveryServiceWithFailingValidation(false);
		pipelineService.setRecoveryCoordinator(coordinator);
		coordinator.setDeliveryPipelineService(pipelineService);

		pipelineService.advance(TASK_ID);
		// 第一次自动 retry 后再次失败：attempt#1 FAILED，且无第二次执行（递归在 budget 处停止）
		assertEquals(1, recoveryRepository.list().size());
		assertEquals(AttemptStatus.FAILED, recoveryRepository.list().get(0).status());
		verify(validationServiceOf(pipelineService), times(2)).startDelivery(anyString());

		// 再次触发（同 fingerprint 顺序出现）：budget 耗尽 → EXHAUSTED，不执行第二次
		RecoveryAttempt exhausted = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.EXHAUSTED, exhausted.status(),
			"同 fingerprint 再次触发必须 EXHAUSTED");
		verify(validationServiceOf(pipelineService), times(2)).startDelivery(anyString());
	}

	// ==================== 真实 DeliveryPipelineService（validation 恒定失败） ====================

	private ValidationService validationServiceOf(DeliveryPipelineService pipelineService) {
		return lastValidationService;
	}

	private ValidationService lastValidationService;

	private DeliveryPipelineService deliveryServiceWithFailingValidation(boolean retrySucceeds) {
		InMemoryDeliveryPipelineRepository pipelineRepository =
			new InMemoryDeliveryPipelineRepository();
		WorkspaceService workspaceService = mock(WorkspaceService.class);
		Workspace workspace = new Workspace("workspace-1", "project-a", "/tmp/repo",
			"ai-dev-os/task/task-1", WorkspaceStatus.READY, NOW, NOW);
		when(workspaceService.getWorkspace("workspace-1")).thenReturn(Optional.of(workspace));
		when(workspaceService.checkGitStatus("workspace-1"))
			.thenReturn(new GitStatus("ai-dev-os/task/task-1", 1, 0, 0));
		when(workspaceService.getGitDiff("workspace-1"))
			.thenReturn(new GitDiff(1, 1, 0, "1 file changed"));
		when(workspaceService.getGitDiffContent("workspace-1"))
			.thenReturn("diff --git a/a.txt b/a.txt\n");
		InMemoryChangeRepository changeRepository = new InMemoryChangeRepository();
		ChangeService changeService = new ChangeService(changeRepository, workspaceService,
			auditService);
		com.aidevos.orchestrator.change.ChangeSet change = changeService.createChange(
			TASK_ID, "workspace-1", "project-a", "exec-1", "ai-dev-os/task/task-1");
		changeService.startReview(change.getChangeId());
		change.markApproved("user-1");
		changeRepository.save(change);
		lastValidationService = mock(ValidationService.class);
		when(lastValidationService.findReusableDeliveryRun(anyString(), anyString()))
			.thenReturn(null);
		if (retrySucceeds) {
			ValidationRun okRun = new ValidationRun("validation-ok", TASK_ID, "project-a",
				"workspace-1", null, "exec-1");
			okRun.setChangeSetId("change-1");
			okRun.setDelivery(true);
			okRun.setStatus(ValidationStatus.SUCCESS);
			java.util.concurrent.atomic.AtomicInteger calls =
				new java.util.concurrent.atomic.AtomicInteger();
			org.mockito.Mockito.doAnswer(invocation -> {
				int n = calls.incrementAndGet();
				System.out.println("DEBUG startDelivery call #" + n
					+ " changeSetId=" + invocation.getArgument(0));
				if (n == 1) {
					throw new IllegalStateException(
						"Validation failed: CI stage unreachable");
				}
				return okRun;
			}).when(lastValidationService).startDelivery(anyString());
		}
		else {
			org.mockito.Mockito.doThrow(
				new IllegalStateException("Validation failed: CI stage unreachable"))
				.when(lastValidationService).startDelivery(anyString());
		}
		when(lastValidationService.failureReason(any(ValidationRun.class)))
			.thenReturn("Validation failed: TEST_FAILED");
		QualityGateService qualityGateService = mock(QualityGateService.class);
		QualityGateResult gate = new QualityGateResult();
		gate.setGateResultId("gate-1");
		gate.setDecision(QualityGateDecision.REQUIRE_APPROVAL);
		gate.setApprovalId("approval-1");
		gate.setStatus(com.aidevos.orchestrator.qualitygate.QualityGateStatus.EVALUATED);
		when(qualityGateService.evaluate(anyString())).thenReturn(gate);
		when(qualityGateService.get(anyString())).thenReturn(gate);
		GitCommandExecutor gitCommandExecutor = mock(GitCommandExecutor.class);
		when(gitCommandExecutor.commit(anyString(), anyString())).thenReturn("abc123def");
		when(gitCommandExecutor.listRemotes(anyString()))
			.thenReturn("origin\tfile:///tmp/bare.git (fetch)\n");
		CommitService commitService = new CommitService(new InMemoryCommitRepository(),
			changeService, workspaceService, gitCommandExecutor, auditService);
		RemotePushApprovalService approvalService = new RemotePushApprovalService(
			new InMemoryRemotePushApprovalRepository(), auditService);
		RemoteGitService remoteGitService = new RemoteGitService(
			new com.aidevos.orchestrator.remote.InMemoryRemoteRepository(), commitService,
			workspaceService, gitCommandExecutor, auditService, approvalService);
		commitService.setRemoteGitService(remoteGitService);
		DeliveryPipelineService pipelineService = new DeliveryPipelineService(
			pipelineRepository, changeService, lastValidationService, qualityGateService,
			commitService, remoteGitService, approvalService, mock(PullRequestService.class),
			mock(CiService.class), auditService);
		approvalService.setDeliveryPipelineService(pipelineService);
		changeService.setDeliveryPipelineService(pipelineService);
		return pipelineService;
	}
}
