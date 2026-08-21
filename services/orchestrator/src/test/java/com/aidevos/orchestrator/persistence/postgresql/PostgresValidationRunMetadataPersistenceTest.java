package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.validation.ValidationCheck;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationDecision;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1-C-PERSISTENCE-CLOSEOUT：ValidationRun.metadata（planMode/planProfile/planRisk/
 * planConfidence/planFingerprint/checksPassed/checksTotal/reused + check 级失败信息）
 * 完整 PostgreSQL snapshot round-trip。
 *
 * 复用 PostgresDocumentStore JSON/document persistence，不新增 migration；
 * FakeDocumentDataSource 跨实例共享 = 模拟重启。
 */
class PostgresValidationRunMetadataPersistenceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private ValidationRun runWithMetadata(ValidationStatus status, boolean reused) {
		ValidationRun run = new ValidationRun("validation-meta-1", "task-1", "project-1",
			"workspace-1", "plan-run-1", "execution-1");
		run.setChangeSetId("change-1");
		run.setDelivery(true);
		run.setValidatedChangeFingerprint("change-fp-1");
		run.setStartedAt(NOW);
		run.setCompletedAt(NOW);
		run.setStatus(status);
		run.setDecision(status == ValidationStatus.FAILED
			? ValidationDecision.FAIL : ValidationDecision.PASS);
		run.setSummary(status == ValidationStatus.FAILED
			? "Validation failed: MAVEN_TARGETED_TEST / TEST_FAILED / FooServiceTest / exitCode=1"
			: "Validation passed 2/3");
		run.setMetadata(Map.of(
			"planMode", "AUTO",
			"planProfile", "TARGETED",
			"planRisk", "MEDIUM",
			"planConfidence", "HIGH",
			"planFingerprint", "fp-123",
			"checksPassed", status == ValidationStatus.FAILED ? 1 : 2,
			"checksTotal", 3,
			"reused", reused));
		ValidationCheck compile = new ValidationCheck("check-1", ValidationCheckType.BACKEND_BUILD,
			"BACKEND_COMPILE", true, true);
		compile.setStatus(ValidationStatus.SUCCESS);
		compile.setSummary("mvn compile");
		compile.setStartedAt(NOW);
		compile.setCompletedAt(NOW);
		ValidationCheck test = new ValidationCheck("check-2", ValidationCheckType.BACKEND_TEST,
			"MAVEN_TARGETED_TEST", true, true);
		if (status == ValidationStatus.FAILED) {
			test.setStatus(ValidationStatus.FAILED);
			test.setErrorMessage("TEST_FAILED");
			test.setMetadata(Map.of(
				"planCheckType", "MAVEN_TARGETED_TEST",
				"errorCode", "TEST_FAILED",
				"selectedTest", "FooServiceTest",
				"exitCode", "1",
				"workingDirectory", "services/orchestrator",
				"outputSnippet", "AssertionError: expected 1 but was 2"));
		}
		else {
			test.setStatus(ValidationStatus.SUCCESS);
		}
		test.setStartedAt(NOW);
		test.setCompletedAt(NOW);
		ValidationCheck skipped = new ValidationCheck("check-3", ValidationCheckType.GENERIC,
			"GIT_DIFF_CHECK", true, true);
		skipped.setStatus(ValidationStatus.SKIPPED);
		run.setChecks(List.of(compile, test, skipped));
		return run;
	}

	/** 1. 完整 plan metadata → snapshot → JSON → restore → metadata 完整一致（含 check 级失败信息） */
	@Test
	void fullPlanMetadataRoundTripsThroughJson() {
		FakeDocumentDataSource dataSource = new FakeDocumentDataSource();
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource, new ObjectMapper());
		PostgresValidationRepository repository = new PostgresValidationRepository(store);
		ValidationRun run = runWithMetadata(ValidationStatus.FAILED, false);
		repository.save(run);

		ValidationRun loaded = repository.get("validation-meta-1");

		assertEquals("AUTO", loaded.getMetadata().get("planMode"));
		assertEquals("TARGETED", loaded.getMetadata().get("planProfile"));
		assertEquals("MEDIUM", loaded.getMetadata().get("planRisk"));
		assertEquals("HIGH", loaded.getMetadata().get("planConfidence"));
		assertEquals("fp-123", loaded.getMetadata().get("planFingerprint"));
		assertEquals(1, loaded.getMetadata().get("checksPassed"));
		assertEquals(3, loaded.getMetadata().get("checksTotal"));
		assertEquals(Boolean.FALSE, loaded.getMetadata().get("reused"));
		assertEquals(3, loaded.getChecks().size());
		ValidationCheck failedCheck = loaded.getChecks().stream()
			.filter(check -> check.getStatus() == ValidationStatus.FAILED)
			.findFirst().orElseThrow();
		assertEquals("MAVEN_TARGETED_TEST", failedCheck.getMetadata().get("planCheckType"));
		assertEquals("TEST_FAILED", failedCheck.getMetadata().get("errorCode"));
		assertEquals("FooServiceTest", failedCheck.getMetadata().get("selectedTest"));
		assertEquals("1", failedCheck.getMetadata().get("exitCode"));
		assertEquals("services/orchestrator", failedCheck.getMetadata().get("workingDirectory"));
		assertTrue(((String) failedCheck.getMetadata().get("outputSnippet"))
			.contains("AssertionError"));
	}

	/** 2. SUCCESS / FAILED / REUSED 各状态 restore 后不丢 metadata */
	@Test
	void allStatusesKeepMetadataAfterRestore() {
		FakeDocumentDataSource dataSource = new FakeDocumentDataSource();
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource, new ObjectMapper());
		PostgresValidationRepository repository = new PostgresValidationRepository(store);
		ValidationRun success = runWithMetadata(ValidationStatus.SUCCESS, false);
		ValidationRun failed = runWithMetadata(ValidationStatus.FAILED, false);
		ValidationRun reused = runWithMetadata(ValidationStatus.SUCCESS, true);
		success.setValidationRunId("validation-success");
		failed.setValidationRunId("validation-failed");
		reused.setValidationRunId("validation-reused");
		repository.save(success);
		repository.save(failed);
		repository.save(reused);

		ValidationRun loadedSuccess = repository.get("validation-success");
		ValidationRun loadedFailed = repository.get("validation-failed");
		ValidationRun loadedReused = repository.get("validation-reused");

		assertEquals(ValidationStatus.SUCCESS, loadedSuccess.getStatus());
		assertEquals("fp-123", loadedSuccess.getMetadata().get("planFingerprint"));
		assertEquals(Boolean.FALSE, loadedSuccess.getMetadata().get("reused"));
		assertEquals(ValidationStatus.FAILED, loadedFailed.getStatus());
		assertEquals("MAVEN_TARGETED_TEST", loadedFailed.getChecks().stream()
			.filter(check -> check.getStatus() == ValidationStatus.FAILED)
			.findFirst().orElseThrow().getMetadata().get("planCheckType"));
		assertEquals(ValidationStatus.SUCCESS, loadedReused.getStatus());
		assertEquals(Boolean.TRUE, loadedReused.getMetadata().get("reused"));
		assertEquals(2, loadedReused.getMetadata().get("checksPassed"));
		assertEquals(3, loadedReused.getMetadata().get("checksTotal"));
	}

	/** 3. 模拟 restart：新 repository/store 实例读取 → planFingerprint/profile/reused/check counts 仍存在 */
	@Test
	void simulatedRestartPreservesPlanMetadata() {
		FakeDocumentDataSource dataSource = new FakeDocumentDataSource();
		PostgresDocumentStore firstStore = new PostgresDocumentStore(dataSource, new ObjectMapper());
		PostgresValidationRepository first = new PostgresValidationRepository(firstStore);
		ValidationRun run = runWithMetadata(ValidationStatus.SUCCESS, true);
		first.save(run);

		// 重启：同一数据源（同一 repository_documents 表）+ 全新 store/repository 实例
		PostgresDocumentStore restartedStore = new PostgresDocumentStore(dataSource, new ObjectMapper());
		PostgresValidationRepository restarted = new PostgresValidationRepository(restartedStore);
		ValidationRun loaded = restarted.findByTaskId("task-1").stream()
			.filter(candidate -> "validation-meta-1".equals(candidate.getValidationRunId()))
			.findFirst().orElseThrow();

		assertEquals("fp-123", loaded.getMetadata().get("planFingerprint"));
		assertEquals("TARGETED", loaded.getMetadata().get("planProfile"));
		assertEquals(Boolean.TRUE, loaded.getMetadata().get("reused"));
		assertEquals(2, loaded.getMetadata().get("checksPassed"));
		assertEquals(3, loaded.getMetadata().get("checksTotal"));
		assertEquals("change-1", loaded.getChangeSetId());
		assertEquals("change-fp-1", loaded.getValidatedChangeFingerprint());
	}
}
