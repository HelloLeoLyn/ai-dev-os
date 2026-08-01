package com.aidevos.orchestrator.schedule;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class TaskScheduler {

	private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
	private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

	@PostConstruct
	public void start() {
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("scheduled-job-");
		scheduler.initialize();
	}

	public void validate(ScheduledTask scheduledTask) {
		if (scheduledTask.getId() == null || scheduledTask.getId().isBlank()) {
			throw new IllegalArgumentException("Schedule id must not be blank");
		}
		if (scheduledTask.getTaskId() == null || scheduledTask.getTaskId().isBlank()) {
			throw new IllegalArgumentException("Scheduled task id must not be blank");
		}
		if (scheduledTask.getCron() == null || scheduledTask.getCron().isBlank()) {
			throw new IllegalArgumentException("Schedule cron must not be blank");
		}
		if (scheduledTask.getZoneId() == null || scheduledTask.getZoneId().isBlank()) {
			throw new IllegalArgumentException("Schedule zone id must not be blank");
		}
		CronExpression.parse(scheduledTask.getCron());
		ZoneId.of(scheduledTask.getZoneId());
	}

	public void schedule(ScheduledTask scheduledTask, Runnable action) {
		validate(scheduledTask);
		CronTrigger trigger = new CronTrigger(scheduledTask.getCron(),
			ZoneId.of(scheduledTask.getZoneId()));
		ScheduledFuture<?> future = scheduler.schedule(action, trigger);
		if (future == null) {
			throw new IllegalStateException("Failed to schedule task: " + scheduledTask.getId());
		}
		ScheduledFuture<?> previous = scheduledTasks.put(scheduledTask.getId(), future);
		if (previous != null) {
			previous.cancel(false);
		}
	}

	public void cancel(String scheduleId) {
		ScheduledFuture<?> future = scheduledTasks.remove(scheduleId);
		if (future != null) {
			future.cancel(false);
		}
	}

	@PreDestroy
	public void stop() {
		scheduledTasks.values().forEach(future -> future.cancel(false));
		scheduledTasks.clear();
		scheduler.shutdown();
	}
}
