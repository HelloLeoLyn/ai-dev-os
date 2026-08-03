package com.aidevos.orchestrator.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryTaskRepository implements TaskRepository {
	private final Map<String, TaskDefinition> tasks = new LinkedHashMap<>();
	public synchronized void save(TaskDefinition task) { tasks.put(task.getId(), task); }
	public synchronized TaskDefinition get(String id) { return tasks.get(id); }
	public synchronized List<TaskDefinition> getAll() { return new ArrayList<>(tasks.values()); }
	public synchronized void remove(String id) { tasks.remove(id); }
}
