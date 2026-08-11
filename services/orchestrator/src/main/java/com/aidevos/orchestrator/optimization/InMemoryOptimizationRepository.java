package com.aidevos.orchestrator.optimization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory optimization store. Optimizations are kept in-memory in this
 * phase (no database migration), so this repository is registered regardless
 * of the persistence type, like the runtime session and collaboration
 * repositories.
 */
@Repository
public class InMemoryOptimizationRepository implements OptimizationRepository {

	private final Map<String, OptimizationRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized void save(OptimizationRecord record) {
		records.put(record.getId(), record);
	}

	@Override
	public synchronized OptimizationRecord get(String id) {
		return id == null ? null : records.get(id);
	}

	@Override
	public synchronized List<OptimizationRecord> listByTask(String taskId) {
		if (taskId == null) {
			return List.of();
		}
		return records.values().stream()
			.filter(record -> taskId.equals(record.getTaskId()))
			.toList();
	}

	@Override
	public synchronized List<OptimizationRecord> list() {
		return List.copyOf(records.values());
	}
}
