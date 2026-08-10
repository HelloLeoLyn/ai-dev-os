package com.aidevos.orchestrator.pr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory pull request record store. This phase is intentionally in-memory
 * only (no database migration is introduced); a PostgreSQL-backed
 * implementation can be added later without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", matchIfMissing = true)
public class InMemoryPullRequestRepository implements PullRequestRepository {

	private final Map<String, PullRequestRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized void save(PullRequestRecord record) {
		records.put(record.getPullRequestId(), record);
	}

	@Override
	public synchronized PullRequestRecord get(String pullRequestId) {
		return records.get(pullRequestId);
	}

	@Override
	public synchronized PullRequestRecord getByCommitId(String commitId) {
		for (PullRequestRecord record : records.values()) {
			if (commitId != null && commitId.equals(record.getCommitId())) {
				return record;
			}
		}
		return null;
	}

	@Override
	public synchronized List<PullRequestRecord> getByTaskId(String taskId) {
		List<PullRequestRecord> result = new ArrayList<>();
		for (PullRequestRecord record : records.values()) {
			if (taskId != null && taskId.equals(record.getTaskId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<PullRequestRecord> list() {
		return new ArrayList<>(records.values());
	}
}
