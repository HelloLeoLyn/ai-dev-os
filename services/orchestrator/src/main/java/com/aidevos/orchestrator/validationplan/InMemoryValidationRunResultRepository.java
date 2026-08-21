package com.aidevos.orchestrator.validationplan;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory ValidationRun 结果存储。与 Postgres 版互斥装配。
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type",
	havingValue = "in-memory", matchIfMissing = true)
public class InMemoryValidationRunResultRepository implements ValidationRunResultRepository {

	private final List<ValidationRunResult> runs = new ArrayList<>();

	@Override
	public synchronized void save(ValidationRunResult run) {
		runs.removeIf(existing -> existing.runId().equals(run.runId()));
		runs.add(run);
	}

	@Override
	public synchronized ValidationRunResult findReusable(String taskId, String changeFingerprint,
			String planFingerprint) {
		for (int i = runs.size() - 1; i >= 0; i--) {
			ValidationRunResult run = runs.get(i);
			if (run.taskId().equals(taskId)
					&& run.changeFingerprint() != null
					&& run.changeFingerprint().equals(changeFingerprint)
					&& run.planFingerprint() != null
					&& run.planFingerprint().equals(planFingerprint)
					&& run.status() == ValidationExecutionModels.ValidationStatus.SUCCESS) {
				return run;
			}
		}
		return null;
	}

	@Override
	public synchronized List<ValidationRunResult> list() {
		return List.copyOf(runs);
	}
}
