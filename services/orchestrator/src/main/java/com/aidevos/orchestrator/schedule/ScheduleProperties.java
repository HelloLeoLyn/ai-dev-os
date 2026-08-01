package com.aidevos.orchestrator.schedule;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "execution.schedules")
public class ScheduleProperties {

	private List<ScheduledTask> tasks = new ArrayList<>();

	public List<ScheduledTask> getTasks() {
		return tasks;
	}

	public void setTasks(List<ScheduledTask> tasks) {
		this.tasks = tasks;
	}
}
