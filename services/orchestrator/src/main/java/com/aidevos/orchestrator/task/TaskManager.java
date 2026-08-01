package com.aidevos.orchestrator.task;

import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskManager {

	private final Map<String, TaskDefinition> tasks = new LinkedHashMap<>();

	public synchronized void register(TaskDefinition taskDefinition) {
		tasks.put(taskDefinition.getId(), taskDefinition);
	}

	public synchronized TaskDefinition getTask(String id) {
		return tasks.get(id);
	}

	public synchronized List<TaskDefinition> getAllTasks() {
		return new ArrayList<>(tasks.values());
	}

	public synchronized TaskDefinition removeTask(String id) {
		return tasks.remove(id);
	}

	public synchronized void updateStatus(String id, String status) {
		TaskDefinition taskDefinition = tasks.get(id);
		if (taskDefinition != null) {
			taskDefinition.setStatus(status);
		}
	}
}
