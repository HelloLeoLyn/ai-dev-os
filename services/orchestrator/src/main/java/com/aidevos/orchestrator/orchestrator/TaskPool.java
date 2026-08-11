package com.aidevos.orchestrator.orchestrator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The task pool of the autonomous orchestrator: every orchestrated task with
 * its pool lifecycle status. The pending subset lives in the task queue; the
 * pool keeps the full history including running and finished tasks.
 */
public class TaskPool {

	private final String poolId;
	private final List<OrchestrationTask> tasks = new ArrayList<>();
	private volatile TaskPoolStatus status = TaskPoolStatus.CREATED;
	private final Instant createdAt;

	public TaskPool(String poolId) {
		this.poolId = poolId;
		this.createdAt = Instant.now();
	}

	public synchronized void addTask(OrchestrationTask task) {
		if (task != null && !contains(task.getTaskId())) {
			tasks.add(task);
		}
	}

	public synchronized void removeTask(String taskId) {
		tasks.removeIf(task -> task.getTaskId().equals(taskId));
	}

	public synchronized boolean contains(String taskId) {
		return tasks.stream().anyMatch(task -> task.getTaskId().equals(taskId));
	}

	public synchronized OrchestrationTask get(String taskId) {
		return tasks.stream()
			.filter(task -> task.getTaskId().equals(taskId))
			.findFirst()
			.orElse(null);
	}

	public synchronized void markRunning() {
		this.status = TaskPoolStatus.RUNNING;
	}

	public synchronized void markPaused() {
		this.status = TaskPoolStatus.PAUSED;
	}

	public synchronized void markCompleted() {
		this.status = TaskPoolStatus.COMPLETED;
	}

	public String getPoolId() {
		return poolId;
	}

	public synchronized List<OrchestrationTask> getTasks() {
		return List.copyOf(tasks);
	}

	public TaskPoolStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
