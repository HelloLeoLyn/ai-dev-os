package com.aidevos.orchestrator.change;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory change set store. This phase is intentionally in-memory only (no
 * database migration is introduced); a PostgreSQL-backed implementation can be
 * added in a later phase without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", matchIfMissing = true)
public class InMemoryChangeRepository implements ChangeRepository {

	private final Map<String, ChangeSet> changes = new LinkedHashMap<>();

	@Override
	public synchronized void save(ChangeSet changeSet) {
		changes.put(changeSet.getChangeId(), changeSet);
	}

	@Override
	public synchronized ChangeSet get(String changeId) {
		return changes.get(changeId);
	}

	@Override
	public synchronized List<ChangeSet> getByTaskId(String taskId) {
		List<ChangeSet> result = new ArrayList<>();
		for (ChangeSet changeSet : changes.values()) {
			if (taskId != null && taskId.equals(changeSet.getTaskId())) {
				result.add(changeSet);
			}
		}
		return result;
	}

	@Override
	public synchronized List<ChangeSet> list() {
		return new ArrayList<>(changes.values());
	}
}
