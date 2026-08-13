package com.aidevos.orchestrator.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryValidationRepository implements ValidationRepository {
	private final Map<String, ValidationRun> runs = new ConcurrentHashMap<>();

	@Override public void save(ValidationRun run) { runs.put(run.getValidationRunId(), run); }
	@Override public ValidationRun get(String id) { return runs.get(id); }
	@Override public List<ValidationRun> findByTaskId(String taskId) {
		return sorted(runs.values().stream().filter(run -> taskId.equals(run.getTaskId())).toList());
	}
	@Override public List<ValidationRun> list() { return sorted(runs.values()); }

	private List<ValidationRun> sorted(java.util.Collection<ValidationRun> values) {
		List<ValidationRun> result = new ArrayList<>(values);
		result.sort(Comparator.comparing(ValidationRun::getStartedAt,
			Comparator.nullsLast(Comparator.naturalOrder())).reversed());
		return result;
	}
}
