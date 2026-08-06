package com.aidevos.orchestrator.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryMemoryRepository implements MemoryRepository {

	private final Map<String, MemoryRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized void save(MemoryRecord record) {
		records.put(record.getId(), record);
	}

	@Override
	public synchronized MemoryRecord get(String id) {
		return records.get(id);
	}

	@Override
	public synchronized List<MemoryRecord> list(String projectId, MemoryType type) {
		List<MemoryRecord> result = new ArrayList<>();
		for (MemoryRecord record : records.values()) {
			if (projectId != null && !projectId.equals(record.getProjectId())) {
				continue;
			}
			if (type != null && type != record.getType()) {
				continue;
			}
			result.add(record);
		}
		return result;
	}

	@Override
	public synchronized boolean delete(String id) {
		return records.remove(id) != null;
	}
}
