package com.aidevos.orchestrator.schedule;

import com.aidevos.orchestrator.job.JobQueueFullException;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleServiceTest {

	@Test
	void shouldNotRegisterDisabledSchedule() {
		TaskScheduler scheduler = mock(TaskScheduler.class);
		JobService jobService = mock(JobService.class);
		ScheduleService service = service(scheduler, new TaskManager(), jobService);
		ScheduledTask scheduledTask = scheduledTask(false);

		service.register(scheduledTask);

		verify(scheduler).validate(any(ScheduledTask.class));
		verify(scheduler, never()).schedule(any(), any());
		verify(scheduler).cancel("schedule-1");
		verify(jobService, never()).submit(any());
	}

	@Test
	void shouldCreateJobWhenCronTriggers() {
		TaskScheduler scheduler = mock(TaskScheduler.class);
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = task();
		taskManager.register(taskDefinition);
		JobService jobService = mock(JobService.class);
		ScheduleService service = service(scheduler, taskManager, jobService);
		ScheduledTask scheduledTask = scheduledTask(true);
		ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);

		service.register(scheduledTask);
		verify(scheduler).schedule(any(ScheduledTask.class), action.capture());
		action.getValue().run();

		verify(jobService).submit(taskDefinition);
	}

	@Test
	void shouldSkipMissingTask() {
		TaskScheduler scheduler = mock(TaskScheduler.class);
		JobService jobService = mock(JobService.class);
		ScheduleService service = service(scheduler, new TaskManager(), jobService);
		ScheduledTask scheduledTask = scheduledTask(true);
		ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);

		service.register(scheduledTask);
		verify(scheduler).schedule(any(ScheduledTask.class), action.capture());
		action.getValue().run();

		verify(jobService, never()).submit(any());
	}

	@Test
	void shouldContinueAfterQueueIsFull() {
		TaskScheduler scheduler = mock(TaskScheduler.class);
		TaskManager taskManager = new TaskManager();
		TaskDefinition taskDefinition = task();
		taskManager.register(taskDefinition);
		JobService jobService = mock(JobService.class);
		when(jobService.submit(taskDefinition))
			.thenThrow(new JobQueueFullException())
			.thenReturn(null);
		ScheduleService service = service(scheduler, taskManager, jobService);
		ScheduledTask scheduledTask = scheduledTask(true);
		ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);

		service.register(scheduledTask);
		verify(scheduler).schedule(any(ScheduledTask.class), action.capture());
		action.getValue().run();
		action.getValue().run();

		verify(jobService, times(2)).submit(taskDefinition);
	}

	@Test
	void shouldListAndRemoveSchedule() {
		TaskScheduler scheduler = mock(TaskScheduler.class);
		ScheduleService service = service(scheduler, new TaskManager(), mock(JobService.class));
		ScheduledTask scheduledTask = scheduledTask(true);

		service.register(scheduledTask);

		assertEquals(1, service.getAll().size());
		assertEquals("schedule-1", service.getAll().getFirst().getId());
		assertTrue(service.remove("schedule-1"));
		assertTrue(service.getAll().isEmpty());
		verify(scheduler).cancel("schedule-1");
	}

	private ScheduleService service(TaskScheduler scheduler, TaskManager taskManager,
			JobService jobService) {
		return new ScheduleService(scheduler, taskManager, jobService, new ScheduleProperties());
	}

	private ScheduledTask scheduledTask(boolean enabled) {
		ScheduledTask scheduledTask = new ScheduledTask();
		scheduledTask.setId("schedule-1");
		scheduledTask.setTaskId("task-1");
		scheduledTask.setCron("0 */5 * * * *");
		scheduledTask.setEnabled(enabled);
		return scheduledTask;
	}

	private TaskDefinition task() {
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId("task-1");
		return taskDefinition;
	}
}
