package com.aidevos.orchestrator.observability.usage;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory usage store.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryUsageRepository implements UsageRepository {

	private final List<UsageRecord> records = new ArrayList<>();

	@Override
	public synchronized void save(UsageRecord record) {
		records.add(record);
	}

	@Override
	public synchronized List<UsageRecord> list() {
		return List.copyOf(records);
	}
}
