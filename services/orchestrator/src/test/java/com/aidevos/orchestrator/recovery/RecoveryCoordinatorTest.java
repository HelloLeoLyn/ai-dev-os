package com.aidevos.orchestrator.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import com.aidevos.orchestrator.delivery.DeliveryStatus;
import com.aidevos.orchestrator.diagnosis.FailureCategory;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosis;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosisService;
import com.aidevos.orchestrator.diagnosis.RecommendedAction;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.persistence.postgresql.FakeDocumentDataSource;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RECOVERY-AND-RETRY-V1 核心测试：
 * Diagnosis → Decision → Safety Policy → Attempt Budget → 最小范围 Recovery → Audit。
 */
class RecoveryCoordinatorTest {

	private static final String TASK_ID = "task-1";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private FailureDiagnosisService diagnosisService;
	private TaskCenterService taskCenterService;
	private DeliveryPipelineService deliveryPipelineService;
	private ExecutionEngine executionEngine;
	private RecoveryCoordinator coordinator;
	private InMemoryRecoveryAttemptRepository repository;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		diagnosisService = mock(FailureDiagnosisService.class);
		taskCenterService = mock(TaskCenterService.class);
		TaskRecord task = mock(TaskRecord.class);
		when(task.getStatus()).thenReturn(TaskStatus.RUNNING);
		when(taskCenterService.getTask(TASK_ID)).thenReturn(Optional.of(task));
		deliveryPipelineService = mock(DeliveryPipelineService.class);
		executionEngine = mock(ExecutionEngine.class);
		repository = new InMemoryRecoveryAttemptRepository();
		coordinator = new RecoveryCoordinator(new RecoveryPolicy(), repository,
			diagnosisService, auditService);
		coordinator.setTaskCenterService(taskCenterService);
		coordinator.setDeliveryPipelineService(deliveryPipelineService);
		coordinator.setExecutionEngine(executionEngine);
		TaskDefinition definition = mock(TaskDefinition.class);
		coordinator.setTaskDefinitionResolverForTests(taskId -> definition);
	}

	private FailureDiagnosis diagnosis(String errorCode, FailureCategory category,
			String source, String fingerprint, List<String> evidence) {
		return new FailureDiagnosis(TASK_ID, source, "stage", null, errorCode, errorCode,
			category, "summary", "rootCause", evidence, RecommendedAction.RETRY, true,
			fingerprint, NOW, false, 1, NOW, NOW);
	}

	private DeliveryPipeline delivery(boolean failed) {
		DeliveryPipeline pipeline = new DeliveryPipeline(TASK_ID, NOW);
		pipeline.advanceTo(com.aidevos.orchestrator.delivery.DeliveryStage.COMMITTING);
		if (failed) {
			pipeline.markFailed(com.aidevos.orchestrator.delivery.DeliveryFailureClass.RECOVERABLE,
				"boom");
		}
		return pipeline;
	}

	private ExecutionResult successResult() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("ok");
		return result;
	}

	/** 1. NETWORK_TRANSIENT → RETRY_EXECUTION → automaticAllowed → attempt 1/1 */
	@Test
	void networkTransientRetriesExecutionOnce() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("NETWORK_TRANSIENT",
			FailureCategory.INFRASTRUCTURE, "EXECUTION", "fp-net", List.of("connection refused")));
		when(executionEngine.execute(any(TaskDefinition.class))).thenReturn(successResult());

		RecoveryDecision decision = coordinator.decide(TASK_ID);
		RecoveryAttempt attempt = coordinator.evaluate(TASK_ID);

		assertEquals(RecoveryAction.RETRY_EXECUTION, decision.action());
		assertTrue(decision.automaticAllowed());
		assertEquals(1, decision.maxAttempts());
		assertEquals(0, decision.attempt(), "首次 decision：已用 0 次 / 上限 1");
		assertNotNull(attempt);
		assertEquals(AttemptStatus.SUCCEEDED, attempt.status());
		assertEquals(1, attempt.attemptNumber());
		assertTrue(attempt.automatic());
		verify(executionEngine, times(1)).execute(any(TaskDefinition.class));
	}

	/** 2. PROVIDER_AUTHENTICATION_FAILED → HUMAN_INTERVENTION → automatic=false → executor 未调用 */
	@Test
	void providerAuthFailureNeverAutomatic() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis(
			"PROVIDER_AUTHENTICATION_FAILED", FailureCategory.PERMISSION, "EXECUTION",
			"fp-auth", List.of("401 Unauthorized")));

		RecoveryDecision decision = coordinator.decide(TASK_ID);
		RecoveryAttempt attempt = coordinator.evaluate(TASK_ID);

		assertEquals(RecoveryAction.HUMAN_INTERVENTION, decision.action());
		assertFalse(decision.automaticAllowed());
		assertNull(attempt, "非自动 recovery 不执行");
		verify(executionEngine, never()).execute(any(TaskDefinition.class));
		verify(deliveryPipelineService, never()).advance(anyString());
	}

	/** 3. TEST_FAILED → HUMAN_INTERVENTION → 不进行无意义自动重跑 */
	@Test
	void testFailedNeverAutoRerun() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("TEST_FAILED",
			FailureCategory.CODE, "EXECUTION", "fp-test", List.of("AssertionError")));

		RecoveryDecision decision = coordinator.decide(TASK_ID);
		coordinator.evaluate(TASK_ID);

		assertEquals(RecoveryAction.HUMAN_INTERVENTION, decision.action());
		assertFalse(decision.automaticAllowed());
		verify(executionEngine, never()).execute(any(TaskDefinition.class));
		verify(deliveryPipelineService, never()).advance(anyString());
	}

	/** 4. CI_TRANSIENT → RETRY_DELIVERY → 调现有 Delivery authority（不重建 commit/push/PR） */
	@Test
	void ciTransientRetriesDeliveryThroughExistingAuthority() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-ci", List.of("temporarily unavailable")));
		when(deliveryPipelineService.advance(TASK_ID)).thenReturn(delivery(false));

		RecoveryDecision decision = coordinator.decide(TASK_ID);
		RecoveryAttempt attempt = coordinator.evaluate(TASK_ID);

		assertEquals(RecoveryAction.RETRY_DELIVERY, decision.action());
		assertTrue(decision.automaticAllowed());
		assertEquals(AttemptStatus.SUCCEEDED, attempt.status());
		verify(deliveryPipelineService, times(1)).advance(TASK_ID);
		verify(executionEngine, never()).execute(any(TaskDefinition.class));
	}

	/** 5. 同 fingerprint 自动 retry 一次后再次失败 → EXHAUSTED → 不执行第二次 */
	@Test
	void sameFingerprintExhaustedAfterOneRetry() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-x", List.of("temporarily unavailable")));
		when(deliveryPipelineService.advance(TASK_ID)).thenReturn(delivery(true));

		RecoveryAttempt first = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.FAILED, first.status());
		RecoveryAttempt second = coordinator.evaluate(TASK_ID);

		assertEquals(AttemptStatus.EXHAUSTED, second.status(),
			"同 fingerprint 第二次自动 retry 必须 EXHAUSTED");
		verify(deliveryPipelineService, times(1)).advance(TASK_ID);
	}

	/** 6. fingerprint=A retry 后 fingerprint=B → B 获得独立 decision/budget */
	@Test
	void changedFingerprintGetsIndependentBudget() {
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-a", List.of("temporarily unavailable")));
		when(deliveryPipelineService.advance(TASK_ID)).thenReturn(delivery(true));
		RecoveryAttempt first = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.FAILED, first.status());

		// 第二次失败是新的 fingerprint（如换了一个错误）
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-b", List.of("temporarily unavailable")));
		when(deliveryPipelineService.advance(TASK_ID)).thenReturn(delivery(false));
		RecoveryDecision decisionB = coordinator.decide(TASK_ID);
		RecoveryAttempt second = coordinator.evaluate(TASK_ID);

		assertEquals("fp-b", decisionB.diagnosisFingerprint());
		assertEquals(0, decisionB.attempt(), "新 fingerprint 获得独立 budget");
		assertEquals(AttemptStatus.SUCCEEDED, second.status());
		verify(deliveryPipelineService, times(2)).advance(TASK_ID);
	}

	/** 7. restart 后读取已有 attempt=1/1 → EXHAUSTED → 不再次 retry */
	@Test
	void restartPreservesExhaustedBudget() {
		FakeDocumentDataSource dataSource = new FakeDocumentDataSource();
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource, new ObjectMapper());
		PostgresRecoveryAttemptRepository pgRepository =
			new PostgresRecoveryAttemptRepository(store);
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("CI_TRANSIENT",
			FailureCategory.DELIVERY, "DELIVERY", "fp-r", List.of("temporarily unavailable")));
		when(deliveryPipelineService.advance(TASK_ID)).thenReturn(delivery(true));

		RecoveryCoordinator first = new RecoveryCoordinator(new RecoveryPolicy(),
			pgRepository, diagnosisService, auditService);
		first.setTaskCenterService(taskCenterService);
		first.setDeliveryPipelineService(deliveryPipelineService);
		RecoveryAttempt before = first.evaluate(TASK_ID);
		assertEquals(AttemptStatus.FAILED, before.status());

		// 重启：同一数据源 + 全新 repository/coordinator 实例
		PostgresRecoveryAttemptRepository restartedRepository =
			new PostgresRecoveryAttemptRepository(
				new PostgresDocumentStore(dataSource, new ObjectMapper()));
		RecoveryCoordinator restarted = new RecoveryCoordinator(new RecoveryPolicy(),
			restartedRepository, diagnosisService, auditService);
		restarted.setTaskCenterService(taskCenterService);
		restarted.setDeliveryPipelineService(deliveryPipelineService);
		RecoveryAttempt after = restarted.evaluate(TASK_ID);

		assertEquals(AttemptStatus.EXHAUSTED, after.status(),
			"重启后 budget 不重置：必须 EXHAUSTED");
		verify(deliveryPipelineService, times(1)).advance(TASK_ID);
	}

	/** 8. WAITING_APPROVAL → no recovery → 不误判 Failure */
	@Test
	void waitingApprovalIsNotRecovery() {
		DeliveryPipeline waiting = new DeliveryPipeline(TASK_ID, NOW);
		waiting.advanceTo(com.aidevos.orchestrator.delivery.DeliveryStage.WAITING_REMOTE_PUSH_APPROVAL);
		waiting.markWaitingApproval();
		when(deliveryPipelineService.get(TASK_ID)).thenReturn(waiting);

		RecoveryAttempt attempt = coordinator.evaluate(TASK_ID);

		assertNull(attempt, "WAITING_APPROVAL 不是 Failure，不生成 recovery");
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.noneMatch(event -> event.type()
				== com.aidevos.orchestrator.audit.EventType.RECOVERY_STARTED),
			"不得产生任何 recovery 执行事件");
	}
}
