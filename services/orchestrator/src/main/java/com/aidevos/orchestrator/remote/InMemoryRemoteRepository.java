package com.aidevos.orchestrator.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory remote push record store. This phase is intentionally in-memory
 * only (no database migration is introduced); a PostgreSQL-backed
 * implementation can be added later without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", matchIfMissing = true)
public class InMemoryRemoteRepository implements RemoteRepository {

	private final Map<String, RemoteBranchRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized void save(RemoteBranchRecord record) {
		records.put(record.getRemoteId(), record);
	}

	@Override
	public synchronized RemoteBranchRecord get(String remoteId) {
		return records.get(remoteId);
	}

	@Override
	public synchronized List<RemoteBranchRecord> getByTaskId(String taskId) {
		List<RemoteBranchRecord> result = new ArrayList<>();
		for (RemoteBranchRecord record : records.values()) {
			if (taskId != null && taskId.equals(record.getTaskId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<RemoteBranchRecord> list() {
		return new ArrayList<>(records.values());
	}
}
