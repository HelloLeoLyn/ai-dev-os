package com.aidevos.orchestrator.diagnosis;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.delivery.DeliveryFailureClass;
import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryStage;
import com.aidevos.orchestrator.delivery.DeliveryStatus;
import com.aidevos.orchestrator.execution.query.ExecutionRecordDetail;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1 Failure Diagnosis 确定性规则测试（不调用 LLM / collector）。
 */
class FailureDiagnosisServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private final FailureDiagnosisService service = new FailureDiagnosisService(null);

	private ExecutionRecordDetail execution(String errorCode, String message, String output,
			Integer exitCode) {
		return new ExecutionRecordDetail("exec-1", "task-x", "coder", "codex", "FAILED",
			message, output, null, List.of(), "execution-1", "job-1", "run-1", "step-1",
			"attempt-1", "/home/user/repo", "sandbox", null, "ai-dev-os/task/task-x",
			"before", "after", exitCode, "thread-1", null, null, null, null, errorCode,
			message, "AI_STEP", "STANDARD", NOW, NOW);
	}

	private TaskRecord failedTask(String taskId, String error) {
		TaskRecord task = new TaskRecord(taskId, "T", "D");
		task.markFailed(error);
		return task;
	}

	private TaskFailureEvidence evidence(TaskRecord task, ExecutionRecordDetail execution,
			DeliveryPipeline pipeline) {
		return new TaskFailureEvidence(task, execution, pipeline, null);
	}

	/** 1. Maven wrong working directory → BUILD_FAILED + WRONG_WORKING_DIRECTORY */
	@Test
	void mavenNoPomDiagnosesWrongWorkingDirectory() {
		TaskFailureEvidence evidence = evidence(failedTask("task-1", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven build failed", "No POM in this directory. Please verify you invoked Maven from the correct directory.", 1),
			null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("WRONG_WORKING_DIRECTORY", diagnosis.code());
		assertEquals("BUILD_FAILED", diagnosis.errorCode());
		assertEquals(FailureCategory.CONFIGURATION, diagnosis.category());
		assertEquals("Execution/Maven", diagnosis.stage());
		assertTrue(diagnosis.evidence().stream().anyMatch(item -> item.startsWith("workingDirectory=")));
		assertTrue(diagnosis.evidence().stream().anyMatch(item -> item.startsWith("exitCode=1")));
		assertEquals(RecommendedAction.RETRY, diagnosis.recommendedAction());
		assertTrue(diagnosis.retryable());
	}

	/** 2. analyst/default model missing → AGENT_DEFAULT_MODEL_MISSING */
	@Test
	void missingAgentDefaultModelDiagnosesAgentDefaultModelMissing() {
		TaskFailureEvidence evidence = evidence(failedTask("task-2", "Model resolution failed"),
			execution("MODEL_NOT_FOUND",
				"Model resolution failed: No model requested and the agent has no default model",
				null, null), null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("AGENT_DEFAULT_MODEL_MISSING", diagnosis.code());
		assertEquals(FailureCategory.MODEL, diagnosis.category());
		assertEquals(RecommendedAction.FIX_CONFIGURATION, diagnosis.recommendedAction());
	}

	/** 3. MODE_CONFLICT → TASK_MODE_CONFLICT */
	@Test
	void modeConflictDiagnosesTaskModeConflict() {
		TaskFailureEvidence evidence = evidence(failedTask("task-3", "MODE_CONFLICT"),
			execution(null,
				"MODE_CONFLICT: READ_ONLY task cannot run CODE_GENERATION (write required).",
				null, null), null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("TASK_MODE_CONFLICT", diagnosis.code());
		assertEquals(FailureCategory.CONFIGURATION, diagnosis.category());
		assertEquals(RecommendedAction.FIX_CONFIGURATION, diagnosis.recommendedAction());
	}

	/** 4. Delivery CI / PR reference failure → stage=CI + PERSISTED_REFERENCE_NOT_RESOLVABLE */
	@Test
	void ciPullRequestReferenceFailureDiagnosesPersistedReferenceNotResolvable() {
		DeliveryPipeline pipeline = new DeliveryPipeline("task-4", NOW);
		pipeline.bindCiRun("ci-1");
		pipeline.advanceTo(DeliveryStage.CI_CHECKING);
		pipeline.markFailed(DeliveryFailureClass.RECOVERABLE,
			"CI check failed: PullRequest not found: pr-abc-123");
		TaskFailureEvidence evidence = evidence(failedTask("task-4", "Delivery failed"),
			null, pipeline);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("PERSISTED_REFERENCE_NOT_RESOLVABLE", diagnosis.code());
		assertEquals("CI", diagnosis.stage());
		assertEquals(FailureCategory.DELIVERY, diagnosis.category());
		assertTrue(diagnosis.evidence().stream().anyMatch(item -> item.contains("PullRequest not found")));
		assertEquals(RecommendedAction.RETRY, diagnosis.recommendedAction());
	}

	/** 5. WAITING_APPROVAL → no failure diagnosis */
	@Test
	void waitingApprovalIsNotAFailure() {
		DeliveryPipeline pipeline = new DeliveryPipeline("task-5", NOW);
		pipeline.bindQualityGate("gate-1");
		pipeline.advanceTo(DeliveryStage.QUALITY_GATE);
		pipeline.markWaitingApproval();
		TaskFailureEvidence evidence = evidence(failedTask("task-5", "waiting"), null, pipeline);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNull(diagnosis, "正常人工 Gate 不得显示为失败");
	}

	/** 6. fingerprint：两个不同 taskId 的同类 Maven 错误 → fingerprint 相同 */
	@Test
	void fingerprintIsStableAcrossTasksForSameError() {
		TaskFailureEvidence first = evidence(failedTask("task-a", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven build failed", "No POM in this directory.", 1), null);
		TaskFailureEvidence second = evidence(failedTask("task-b", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven build failed", "No POM in this directory.", 1), null);

		FailureDiagnosis d1 = service.diagnose(first);
		FailureDiagnosis d2 = service.diagnose(second);

		assertNotNull(d1);
		assertNotNull(d2);
		assertEquals(d1.fingerprint(), d2.fingerprint(),
			"同类错误在不同 task 必须产生相同 fingerprint");
		assertEquals(16, d1.fingerprint().length());
	}
}
