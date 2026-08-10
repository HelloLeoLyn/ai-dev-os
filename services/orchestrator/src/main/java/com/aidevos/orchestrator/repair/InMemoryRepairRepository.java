package com.aidevos.orchestrator.repair;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory repair task store used for the default and test persistence mode.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryRepairRepository implements RepairRepository {

	private final Map<String, RepairTask> tasks = new LinkedHashMap<>();

	@Override
	public synchronized void save(RepairTask task) {
		tasks.put(task.getRepairId(), task);
	}

	@Override
	public synchronized RepairTask get(String repairId) {
		return tasks.get(repairId);
	}

	@Override
	public synchronized List<RepairTask> getByTaskId(String taskId) {
		return list().stream()
			.filter(task -> taskId.equals(task.getTaskId()))
			.toList();
	}

	@Override
	public synchronized List<RepairTask> list() {
		return new ArrayList<>(tasks.values());
	}
}
