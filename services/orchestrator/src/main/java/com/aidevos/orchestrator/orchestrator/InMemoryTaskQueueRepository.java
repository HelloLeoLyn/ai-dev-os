package com.aidevos.orchestrator.orchestrator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Repository;

/**
 * In-memory task queue of the autonomous orchestrator, ordered by priority
 * (CRITICAL first) and creation time (FIFO within a priority). Kept
 * in-memory in this phase (no database migration), like the runtime and
 * collaboration stores.
 */
@Repository
public class InMemoryTaskQueueRepository implements TaskQueueRepository {

	private final List<OrchestrationTask> queue = new ArrayList<>();

	@Override
	public synchronized void add(OrchestrationTask task) {
		if (task != null && get(task.getTaskId()) == null) {
			queue.add(task);
		}
	}

	@Override
	public synchronized boolean remove(String taskId) {
		return queue.removeIf(task -> task.getTaskId().equals(taskId));
	}

	@Override
	public synchronized OrchestrationTask next() {
		return queue.stream()
			.min(Comparator.comparingInt((OrchestrationTask task) ->
					task.getPriority() == null ? 0 : task.getPriority().ordinal())
				.reversed()
				.thenComparing(OrchestrationTask::getCreatedAt))
			.orElse(null);
	}

	@Override
	public synchronized OrchestrationTask get(String taskId) {
		return queue.stream()
			.filter(task -> task.getTaskId().equals(taskId))
			.findFirst()
			.orElse(null);
	}

	@Override
	public synchronized List<OrchestrationTask> list() {
		return List.copyOf(queue);
	}
}
