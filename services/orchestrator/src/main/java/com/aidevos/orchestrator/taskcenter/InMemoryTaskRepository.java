package com.aidevos.orchestrator.taskcenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory task store used for the default and test persistence mode.
 */
@Repository("inMemoryTaskCenterRepository")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryTaskRepository implements TaskRepository {

	private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();

	@Override
	public synchronized void save(TaskRecord task) {
		tasks.put(task.getTaskId(), task);
	}

	@Override
	public synchronized TaskRecord get(String taskId) {
		return tasks.get(taskId);
	}

	@Override
	public synchronized List<TaskRecord> list() {
		List<TaskRecord> result = new ArrayList<>(tasks.values());
		result.sort(Comparator.comparing(TaskRecord::getCreatedAt).reversed());
		return result;
	}

	@Override
	public synchronized List<TaskRecord> listByProject(String projectId) {
		return list().stream()
			.filter(task -> projectId == null || projectId.equals(task.getProjectId()))
			.toList();
	}
}
