package com.aidevos.orchestrator.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskSchedulerTest {

	private final TaskScheduler taskScheduler = new TaskScheduler();

	@Test
	void shouldAcceptValidCron() {
		assertDoesNotThrow(() -> taskScheduler.validate(scheduledTask("0 */5 * * * *", true)));
	}

	@Test
	void shouldRejectInvalidCron() {
		assertThrows(IllegalArgumentException.class,
			() -> taskScheduler.validate(scheduledTask("invalid-cron", true)));
	}

	private ScheduledTask scheduledTask(String cron, boolean enabled) {
		ScheduledTask scheduledTask = new ScheduledTask();
		scheduledTask.setId("schedule-1");
		scheduledTask.setTaskId("task-1");
		scheduledTask.setCron(cron);
		scheduledTask.setEnabled(enabled);
		return scheduledTask;
	}
}
