package com.aidevos.orchestrator.schedule;

import java.util.Comparator;
import java.util.List;

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
	private final ScheduleRepository repository;

	public ScheduleService(TaskScheduler taskScheduler, TaskManager taskManager,
			JobService jobService, ScheduleProperties properties) {
		this(taskScheduler, taskManager, jobService, properties, new InMemoryScheduleRepository());
	}

	@org.springframework.beans.factory.annotation.Autowired
	public ScheduleService(TaskScheduler taskScheduler, TaskManager taskManager,
			JobService jobService, ScheduleProperties properties, ScheduleRepository repository) {
		this.taskScheduler = taskScheduler;
		this.taskManager = taskManager;
		this.jobService = jobService;
		this.properties = properties;
		this.repository = repository;
	}

	@Override
	public void run(ApplicationArguments args) {
		repository.getAll().forEach(this::register);
		properties.getTasks().forEach(this::register);
	}

	public synchronized ScheduledTask register(ScheduledTask scheduledTask) {
		ScheduledTask snapshot = snapshot(scheduledTask);
		taskScheduler.validate(snapshot);
		repository.save(snapshot);
		if (snapshot.isEnabled()) {
			taskScheduler.schedule(snapshot, () -> trigger(snapshot.getId()));
		}
		else {
			taskScheduler.cancel(snapshot.getId());
		}
		return snapshot(snapshot);
	}

	public synchronized List<ScheduledTask> getAll() {
		return repository.getAll().stream()
			.sorted(Comparator.comparing(ScheduledTask::getId))
			.map(this::snapshot)
			.toList();
	}

	public synchronized boolean remove(String scheduleId) {
		ScheduledTask removed = repository.get(scheduleId);
		if (removed == null) {
			return false;
		}
		repository.remove(scheduleId);
		taskScheduler.cancel(scheduleId);
		return true;
	}

	private synchronized void trigger(String scheduleId) {
		ScheduledTask scheduledTask = repository.get(scheduleId);
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
