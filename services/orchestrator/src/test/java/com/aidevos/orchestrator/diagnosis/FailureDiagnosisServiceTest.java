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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
		return new TaskFailureEvidence(task, execution, pipeline, null, List.of());
	}

	/** 3. 正常 Maven banner（from pom.xml）+ surefire 无匹配 → 不误判 WRONG_WORKING_DIRECTORY */
	@Test
	void normalPomBannerDoesNotDiagnoseWrongWorkingDirectory() {
		String output = "[INFO] Scanning for projects...\n"
			+ "[INFO] Building orchestrator 1.2.2\n"
			+ "[INFO]   from pom.xml\n"
			+ "[INFO] --- surefire:3.5.6:test (default-test) @ orchestrator ---\n"
			+ "[ERROR] BUILD FAILURE\n"
			+ "[ERROR] No tests matching pattern \"WRIT\" were executed!";
		TaskFailureEvidence evidence = evidence(failedTask("task-1", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven build failed", output, 1), null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertTrue(!"WRONG_WORKING_DIRECTORY".equals(diagnosis.code()),
			"普通 pom banner + BUILD FAILURE 不得误判 WRONG_WORKING_DIRECTORY，实际="
				+ diagnosis.code());
	}

	/** 4. 真实 Maven no-project 错误 → 仍判 WRONG_WORKING_DIRECTORY */
	@Test
	void realMissingPomStillDiagnosesWrongWorkingDirectory() {
		TaskFailureEvidence evidence = evidence(failedTask("task-1", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven build failed",
				"[ERROR] The goal you specified requires a project to execute but there is no POM in this directory (/tmp/worktree). Please verify you invoked Maven from the correct directory.",
				1), null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("WRONG_WORKING_DIRECTORY", diagnosis.code());
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

	/** A. EXECUTOR_FAILED + 401 Unauthorized + Authentication Fails → PROVIDER_AUTHENTICATION_FAILED */
	@Test
	void providerAuthenticationFailureDiagnosesGenericRule() {
		TaskFailureEvidence evidence = evidence(failedTask("task-auth", "Provider error"),
			execution("EXECUTOR_FAILED", "unexpected status 401 Unauthorized",
				"Authentication Fails. Check provider/model endpoint credentials.", 1), null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("PROVIDER_AUTHENTICATION_FAILED", diagnosis.code());
		assertEquals(FailureCategory.CONFIGURATION, diagnosis.category());
		assertEquals(RecommendedAction.FIX_CONFIGURATION, diagnosis.recommendedAction());
		assertEquals(false, diagnosis.retryable());
		// evidence 提炼：HTTP status / 认证消息 / endpoint / exitCode
		assertTrue(diagnosis.evidence().stream().anyMatch(item -> item.contains("401")));
		assertTrue(diagnosis.evidence().stream().anyMatch(item ->
			item.contains("Unauthorized") || item.contains("Authentication")));
		assertTrue(diagnosis.evidence().stream().anyMatch(item -> item.startsWith("exitCode=1")));
	}

	/** B. taskError/errorMessage/message 重复 → evidence 去重，不出现三份相同长文本 */
	@Test
	void unknownEvidenceDeduplicatesRepeatedRawError() {
		String repeated = "Mystery error: widget exploded while applying flux capacitor";
		TaskFailureEvidence evidence = evidence(failedTask("task-dup", repeated),
			execution(null, repeated, repeated, 7), null);

		FailureDiagnosis diagnosis = service.diagnose(evidence);

		assertNotNull(diagnosis);
		assertEquals("UNKNOWN", diagnosis.code());
		long occurrences = diagnosis.evidence().stream()
			.filter(item -> item.contains("Mystery error"))
			.count();
		assertEquals(1, occurrences, "同一原始错误不得重复展示三遍");
	}

	// ==================== KNOWN-FAILURE-AND-DIAGNOSIS-HISTORY-V1 ====================

	private FailureDiagnosisService knownService(com.aidevos.orchestrator.audit.AuditService audit) {
		com.aidevos.orchestrator.diagnosis.InMemoryKnownFailureRepository repository =
			new com.aidevos.orchestrator.diagnosis.InMemoryKnownFailureRepository();
		KnownFailureService knownFailureService = new KnownFailureService(repository);
		knownFailureService.setAuditService(audit);
		FailureDiagnosisService diagnosed = new FailureDiagnosisService(null);
		diagnosed.setKnownFailureService(knownFailureService);
		return diagnosed;
	}

	private TaskFailureEvidence mavenEvidence(String taskId) {
		return evidence(failedTask(taskId, "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven build failed", "No POM in this directory.", 1), null);
	}

	/** 1. 第一次 diagnosis → 创建 KnownFailure → occurrenceCount=1 → knownFailure=false */
	@Test
	void firstDiagnosisCreatesKnownFailureWithCountOne() {
		FailureDiagnosisService diagnosed = knownService(com.aidevos.orchestrator.audit.AuditService.noop());

		FailureDiagnosis diagnosis = diagnosed.diagnose(mavenEvidence("task-1"));

		assertNotNull(diagnosis);
		assertEquals(false, diagnosis.knownFailure());
		assertEquals(1, diagnosis.occurrenceCount());
		assertNotNull(diagnosis.firstSeenAt());
		assertNotNull(diagnosis.lastSeenAt());
	}

	/** 2. 不同 taskId 同 fingerprint → 复用同一 KnownFailure → occurrenceCount=2 → knownFailure=true */
	@Test
	void secondTaskWithSameFingerprintReusesKnownFailure() {
		FailureDiagnosisService diagnosed = knownService(com.aidevos.orchestrator.audit.AuditService.noop());
		diagnosed.diagnose(mavenEvidence("task-1"));

		FailureDiagnosis second = diagnosed.diagnose(mavenEvidence("task-2"));

		assertNotNull(second);
		assertEquals(true, second.knownFailure());
		assertEquals(2, second.occurrenceCount());
	}

	/** 3. 同一个 task 重复 GET diagnosis → occurrenceCount 不增加 */
	@Test
	void repeatedDiagnosisForSameTaskDoesNotIncreaseCount() {
		FailureDiagnosisService diagnosed = knownService(com.aidevos.orchestrator.audit.AuditService.noop());
		diagnosed.diagnose(mavenEvidence("task-1"));
		diagnosed.diagnose(mavenEvidence("task-2")); // count=2

		for (int i = 0; i < 10; i++) {
			FailureDiagnosis again = diagnosed.diagnose(mavenEvidence("task-1"));
			assertEquals(2, again.occurrenceCount(), "同 task 重复诊断（如 UI 刷新）不得增加计数");
		}
	}

	/** 4. 同类错误含不同 UUID/path/timestamp → fingerprint 相同 */
	@Test
	void fingerprintIgnoresUuidPathAndTimestamp() {
		TaskFailureEvidence first = evidence(failedTask("task-a", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven failed",
				"No POM in /tmp/task-11111111-2222-3333-4444-555555555555/repo at 2026-08-01T00:00:00Z", 1),
			null);
		TaskFailureEvidence second = evidence(failedTask("task-b", "BUILD FAILURE"),
			execution("BUILD_FAILED", "Maven failed",
				"No POM in /home/other-user/workspace/proj at 2026-08-02T12:00:00Z", 1),
			null);

		FailureDiagnosis d1 = service.diagnose(first);
		FailureDiagnosis d2 = service.diagnose(second);

		assertNotNull(d1);
		assertNotNull(d2);
		assertEquals(d1.fingerprint(), d2.fingerprint(),
			"不同 UUID/路径/时间戳的同类错误 fingerprint 必须一致");
	}

	/** 5. Timeline 有 STEP_FAILED/EXECUTION_FAILED → evidence 包含关键 timeline 事件，不含整份 */
	@Test
	void timelineEvidenceIncludesOnlyKeyFailureEvents() {
		com.aidevos.orchestrator.timeline.TimelineService timeline =
			mock(com.aidevos.orchestrator.timeline.TimelineService.class);
		com.aidevos.orchestrator.taskcenter.TaskCenterService tasks =
			mock(com.aidevos.orchestrator.taskcenter.TaskCenterService.class);
		com.aidevos.orchestrator.execution.query.ExecutionRecordQueryService executions =
			mock(com.aidevos.orchestrator.execution.query.ExecutionRecordQueryService.class);
		com.aidevos.orchestrator.delivery.DeliveryPipelineService delivery =
			mock(com.aidevos.orchestrator.delivery.DeliveryPipelineService.class);
		com.aidevos.orchestrator.plan.run.PlanRunRepository planRuns =
			mock(com.aidevos.orchestrator.plan.run.PlanRunRepository.class);
		when(tasks.getTask("task-t")).thenReturn(java.util.Optional.of(failedTask("task-t", "x")));
		when(executions.getAll(any(), any())).thenReturn(List.of());
		when(timeline.timeline("task-t")).thenReturn(new com.aidevos.orchestrator.timeline.UnifiedTimeline(
			"TASK", "task-t", List.of(
				new com.aidevos.orchestrator.timeline.TimelineEventDTO("e1", "STEP_SUCCEEDED",
					"STEP", "s1", "SUCCESS", "ok", NOW),
				new com.aidevos.orchestrator.timeline.TimelineEventDTO("e2", "STEP_FAILED",
					"STEP", "s1", "FAILED", "Maven build failed", NOW),
				new com.aidevos.orchestrator.timeline.TimelineEventDTO("e3", "EXECUTION_FAILED",
					"EXECUTION", "e1", "FAILED", "executor failed", NOW),
				new com.aidevos.orchestrator.timeline.TimelineEventDTO("e4", "STEP_FAILED",
					"STEP", "s2", "FAILED", "Maven build failed", NOW))));
		FailureEvidenceCollector collector = new FailureEvidenceCollector(tasks, executions,
			delivery, planRuns, timeline);

		TaskFailureEvidence evidence = collector.collect("task-t");

		// STEP_FAILED 去重后 1 条 + EXECUTION_FAILED 1 条 = 2 条；成功事件不进入
		assertEquals(2, evidence.timelineEvidence().size());
		assertTrue(evidence.timelineEvidence().stream().anyMatch(item -> item.startsWith("STEP_FAILED")));
		assertTrue(evidence.timelineEvidence().stream().anyMatch(item -> item.startsWith("EXECUTION_FAILED")));
		assertTrue(evidence.timelineEvidence().stream().noneMatch(item -> item.contains("STEP_SUCCEEDED")));

		// diagnosis evidence 合并关键 timeline 事件
		FailureDiagnosis diagnosis = service.diagnose(evidence);
		assertNotNull(diagnosis);
		assertTrue(diagnosis.evidence().stream().anyMatch(item -> item.startsWith("STEP_FAILED")));
	}

	/** 6. 同一 task/fingerprint 重复 diagnosis → FAILURE_DIAGNOSED timeline event 只产生一次 */
	@Test
	void diagnosedEventEmittedOnlyOncePerTaskFingerprint() {
		com.aidevos.orchestrator.audit.InMemoryAuditRepository auditRepository =
			new com.aidevos.orchestrator.audit.InMemoryAuditRepository();
		com.aidevos.orchestrator.audit.AuditService audit =
			new com.aidevos.orchestrator.audit.AuditService(auditRepository);
		FailureDiagnosisService diagnosed = knownService(audit);

		diagnosed.diagnose(mavenEvidence("task-1"));
		diagnosed.diagnose(mavenEvidence("task-1"));
		diagnosed.diagnose(mavenEvidence("task-1"));

		long events = auditRepository.query(com.aidevos.orchestrator.audit.EventQuery.all()).stream()
			.filter(event -> event.type() == com.aidevos.orchestrator.audit.EventType.FAILURE_DIAGNOSED
				&& "task-1".equals(event.taskId()))
			.count();
		assertEquals(1, events, "同一 task/fingerprint 的 FAILURE_DIAGNOSED 事件只能产生一次");

		diagnosed.diagnose(mavenEvidence("task-2"));
		long total = auditRepository.query(com.aidevos.orchestrator.audit.EventQuery.all()).stream()
			.filter(event -> event.type() == com.aidevos.orchestrator.audit.EventType.FAILURE_DIAGNOSED)
			.count();
		assertEquals(2, total, "新 task 计入时再产生一次事件");
	}
}
