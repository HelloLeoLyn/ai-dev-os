package com.aidevos.orchestrator.ci;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory CI run record store. This phase is intentionally in-memory only
 * (no database migration is introduced); a PostgreSQL-backed implementation
 * can be added later without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", matchIfMissing = true)
public class InMemoryCiRepository implements CiRepository {

	private final Map<String, CiRunRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized void save(CiRunRecord record) {
		records.put(record.getCiRunId(), record);
	}

	@Override
	public synchronized CiRunRecord get(String ciRunId) {
		return records.get(ciRunId);
	}

	@Override
	public synchronized List<CiRunRecord> getByTaskId(String taskId) {
		List<CiRunRecord> result = new ArrayList<>();
		for (CiRunRecord record : records.values()) {
			if (taskId != null && taskId.equals(record.getTaskId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<CiRunRecord> getByPullRequestId(String pullRequestId) {
		List<CiRunRecord> result = new ArrayList<>();
		for (CiRunRecord record : records.values()) {
			if (pullRequestId != null && pullRequestId.equals(record.getPullRequestId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<CiRunRecord> list() {
		return new ArrayList<>(records.values());
	}
}
