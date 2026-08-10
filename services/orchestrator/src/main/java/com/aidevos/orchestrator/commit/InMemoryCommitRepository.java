package com.aidevos.orchestrator.commit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory commit record store. This phase is intentionally in-memory only
 * (no database migration is introduced); a PostgreSQL-backed implementation
 * can be added in a later phase without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryCommitRepository implements CommitRepository {

	private final Map<String, CommitRecord> commits = new LinkedHashMap<>();

	@Override
	public synchronized void save(CommitRecord record) {
		commits.put(record.getCommitId(), record);
	}

	@Override
	public synchronized CommitRecord get(String commitId) {
		return commits.get(commitId);
	}

	@Override
	public synchronized List<CommitRecord> getByTaskId(String taskId) {
		List<CommitRecord> result = new ArrayList<>();
		for (CommitRecord record : commits.values()) {
			if (taskId != null && taskId.equals(record.getTaskId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<CommitRecord> list() {
		return new ArrayList<>(commits.values());
	}
}
