package com.aidevos.orchestrator.schedule;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.job.JobQueueFullException;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService implements ApplicationRunner {

	private static final Log logger = LogFactory.getLog(ScheduleService.class);

	private final TaskScheduler taskScheduler;
	private final TaskManager taskManager;
	private final JobService jobService;
	private final ScheduleProperties properties;
	private final Map<String, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

	public ScheduleService(TaskScheduler taskScheduler, TaskManager taskManager,
			JobService jobService, ScheduleProperties properties) {
		this.taskScheduler = taskScheduler;
		this.taskManager = taskManager;
		this.jobService = jobService;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		properties.getTasks().forEach(this::register);
	}

	public synchronized ScheduledTask register(ScheduledTask scheduledTask) {
		ScheduledTask snapshot = snapshot(scheduledTask);
		taskScheduler.validate(snapshot);
		scheduledTasks.put(snapshot.getId(), snapshot);
		if (snapshot.isEnabled()) {
			taskScheduler.schedule(snapshot, () -> trigger(snapshot.getId()));
		}
		else {
			taskScheduler.cancel(snapshot.getId());
		}
		return snapshot(snapshot);
	}

	public synchronized List<ScheduledTask> getAll() {
		return scheduledTasks.values().stream()
			.sorted(Comparator.comparing(ScheduledTask::getId))
			.map(this::snapshot)
			.toList();
	}

	public synchronized boolean remove(String scheduleId) {
		ScheduledTask removed = scheduledTasks.remove(scheduleId);
		if (removed == null) {
			return false;
		}
		taskScheduler.cancel(scheduleId);
		return true;
	}

	private synchronized void trigger(String scheduleId) {
		ScheduledTask scheduledTask = scheduledTasks.get(scheduleId);
		if (scheduledTask == null || !scheduledTask.isEnabled()) {
			return;
		}
		TaskDefinition taskDefinition = taskManager.getTask(scheduledTask.getTaskId());
		if (taskDefinition == null) {
			return;
		}
		try {
			jobService.submit(taskDefinition);
		}
		catch (JobQueueFullException ex) {
			logger.warn("Job queue is full for schedule: " + scheduleId);
		}
	}

	private ScheduledTask snapshot(ScheduledTask source) {
		ScheduledTask snapshot = new ScheduledTask();
		snapshot.setId(source.getId());
		snapshot.setTaskId(source.getTaskId());
		snapshot.setCron(source.getCron());
		snapshot.setEnabled(source.isEnabled());
		snapshot.setZoneId(source.getZoneId());
		return snapshot;
	}
}
