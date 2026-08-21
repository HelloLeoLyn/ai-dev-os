package com.aidevos.orchestrator.validationplan;

import java.util.List;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Postgres ValidationRun 结果存储：复用 PostgresDocumentStore（type=validation-plan-run）。
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
final class PostgresValidationRunResultRepository implements ValidationRunResultRepository {

	private static final String TYPE = "validation-plan-run";

	private final PostgresDocumentStore store;

	PostgresValidationRunResultRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(ValidationRunResult run) {
		store.put(TYPE, run.runId(), run, "task:" + run.taskId());
	}

	@Override
	public ValidationRunResult findReusable(String taskId, String changeFingerprint,
			String planFingerprint) {
		return store.all(TYPE, ValidationRunResult.class).stream()
			.filter(run -> run.taskId().equals(taskId)
				&& run.changeFingerprint() != null
				&& run.changeFingerprint().equals(changeFingerprint)
				&& run.planFingerprint() != null
				&& run.planFingerprint().equals(planFingerprint)
				&& run.status() == ValidationExecutionModels.ValidationStatus.SUCCESS)
			.findFirst().orElse(null);
	}

	@Override
	public List<ValidationRunResult> list() {
		return store.all(TYPE, ValidationRunResult.class);
	}
}
