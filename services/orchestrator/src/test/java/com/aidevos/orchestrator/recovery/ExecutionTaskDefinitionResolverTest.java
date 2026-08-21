package com.aidevos.orchestrator.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.diagnosis.FailureCategory;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosis;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosisService;
import com.aidevos.orchestrator.diagnosis.RecommendedAction;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RECOVERY-EXECUTION-RESOLVER-CLOSEOUT：
 * 生产 RETRY_EXECUTION 从持久化 job snapshot 安全重建 TaskDefinition。
 */
class ExecutionTaskDefinitionResolverTest {

	private static final String TASK_ID = "task-1";
	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private JobRepository jobRepository;
	private TaskCenterService taskCenterService;
	private FailureDiagnosisService diagnosisService;
	private ExecutionEngine executionEngine;
	private RecoveryCoordinator coordinator;

	@BeforeEach
	void setUp() {
		jobRepository = mock(JobRepository.class);
		taskCenterService = mock(TaskCenterService.class);
		TaskRecord task = mock(TaskRecord.class);
		when(task.getStatus()).thenReturn(TaskStatus.RUNNING);
		when(taskCenterService.getTask(TASK_ID)).thenReturn(Optional.of(task));
		diagnosisService = mock(FailureDiagnosisService.class);
		executionEngine = mock(ExecutionEngine.class);
		coordinator = new RecoveryCoordinator(new RecoveryPolicy(),
			new InMemoryRecoveryAttemptRepository(), diagnosisService,
			new AuditService(new InMemoryAuditRepository()));
		coordinator.setTaskCenterService(taskCenterService);
		coordinator.setExecutionEngine(executionEngine);
	}

	private TaskDefinition snapshot(String id, String agentName) {
		TaskDefinition definition = new TaskDefinition();
		definition.setId(id);
		definition.setName("t");
		definition.setDescription("d");
		definition.setAgentName(agentName);
		definition.setMetadata(new java.util.LinkedHashMap<>(Map.of(
			"originalTaskId", TASK_ID,
			"executionMode", "READ_WRITE",
			"requestedModelId", "deepseek-v4-flash")));
		definition.setParameters(new java.util.LinkedHashMap<>(Map.of(
			"goal", "fix the bug")));
		return definition;
	}

	private ExecutionJob job(TaskDefinition snapshot) {
		return new ExecutionJob("job-1", snapshot);
	}

	private FailureDiagnosis diagnosis(String fingerprint) {
		return new FailureDiagnosis(TASK_ID, "EXECUTION", "stage", null,
			"NETWORK_TRANSIENT", "NETWORK_TRANSIENT", FailureCategory.INFRASTRUCTURE,
			"summary", "rootCause", List.of("connection refused"), RecommendedAction.RETRY,
			true, fingerprint, NOW, false, 1, NOW, NOW);
	}

	/** 1. persisted task/execution context → resolve TaskDefinition → RETRY_EXECUTION 成功 */
	@Test
	void persistedJobSnapshotResolvesAndRetriesSuccessfully() {
		TaskDefinition snapshot = snapshot(TASK_ID, "coder");
		when(jobRepository.getAll()).thenReturn(List.of(job(snapshot)));
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("fp-resolve"));
		ExecutionResult success = new ExecutionResult();
		success.setSuccess(true);
		success.setMessage("ok");
		when(executionEngine.execute(any(TaskDefinition.class))).thenReturn(success);

		ExecutionTaskDefinitionResolver resolver = new ExecutionTaskDefinitionResolver(
			jobRepository, taskCenterService);
		coordinator.setTaskDefinitionResolver(resolver);

		TaskDefinition resolved = resolver.apply(TASK_ID);
		assertNotNull(resolved);
		assertEquals(TASK_ID, resolved.getId());
		assertEquals("coder", resolved.getAgentName());
		assertEquals("deepseek-v4-flash", resolved.getMetadata().get("requestedModelId"));
		assertEquals("READ_WRITE", resolved.getMetadata().get("executionMode"));
		assertEquals("fix the bug", resolved.getParameters().get("goal"));

		RecoveryAttempt attempt = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.SUCCEEDED, attempt.status());
		assertEquals(1, attempt.attemptNumber());
		verify(executionEngine, times(1)).execute(any(TaskDefinition.class));
	}

	/** 2. 缺关键字段 → fail closed → 人工介入（executor 不执行） */
	@Test
	void missingCriticalFieldsFailsClosedToHuman() {
		// agentName 为空 → 不可信
		TaskDefinition missingAgent = snapshot(TASK_ID, " ");
		when(jobRepository.getAll()).thenReturn(List.of(job(missingAgent)));
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("fp-missing"));

		ExecutionTaskDefinitionResolver resolver = new ExecutionTaskDefinitionResolver(
			jobRepository, taskCenterService);
		coordinator.setTaskDefinitionResolver(resolver);

		assertNull(resolver.apply(TASK_ID), "agentName 缺失 → 不猜字段 → 人工");
		RecoveryAttempt attempt = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.FAILED, attempt.status());
		verify(executionEngine, never()).execute(any(TaskDefinition.class));

		// 无 job 快照 → null
		when(jobRepository.getAll()).thenReturn(List.of());
		assertNull(resolver.apply(TASK_ID), "无 job 快照 → 人工");

		// snapshot.id 与 taskId 不匹配 → null
		TaskDefinition mismatched = snapshot("task-other", "coder");
		when(jobRepository.getAll()).thenReturn(List.of(job(mismatched)));
		assertNull(resolver.apply(TASK_ID), "快照不属于该 task → 人工");

		// task 已删除 → null
		when(taskCenterService.getTask(TASK_ID)).thenReturn(Optional.empty());
		TaskDefinition valid = snapshot(TASK_ID, "coder");
		when(jobRepository.getAll()).thenReturn(List.of(job(valid)));
		assertNull(resolver.apply(TASK_ID), "task 不存在 → 人工");
	}

	/** 3. 同 fingerprint 仍受 1/1 budget：resolve + retry 失败后再次触发 → EXHAUSTED，不重复执行 */
	@Test
	void sameFingerprintBudgetStillEnforcedWithResolver() {
		TaskDefinition snapshot = snapshot(TASK_ID, "coder");
		when(jobRepository.getAll()).thenReturn(List.of(job(snapshot)));
		when(diagnosisService.diagnose(TASK_ID)).thenReturn(diagnosis("fp-budget"));
		ExecutionResult failed = new ExecutionResult();
		failed.setSuccess(false);
		failed.setMessage("still down");
		when(executionEngine.execute(any(TaskDefinition.class))).thenReturn(failed);

		ExecutionTaskDefinitionResolver resolver = new ExecutionTaskDefinitionResolver(
			jobRepository, taskCenterService);
		coordinator.setTaskDefinitionResolver(resolver);

		RecoveryAttempt first = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.FAILED, first.status());
		assertEquals(1, first.attemptNumber());

		RecoveryAttempt second = coordinator.evaluate(TASK_ID);
		assertEquals(AttemptStatus.EXHAUSTED, second.status(),
			"同 fingerprint 1/1 budget 不重复执行");
		verify(executionEngine, times(1)).execute(any(TaskDefinition.class));
	}
}
