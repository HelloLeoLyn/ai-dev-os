package com.aidevos.orchestrator.schedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryScheduleRepository implements ScheduleRepository {
	private final Map<String, ScheduledTask> schedules = new ConcurrentHashMap<>();
	public void save(ScheduledTask schedule) { schedules.put(schedule.getId(), schedule); }
	public ScheduledTask get(String id) { return schedules.get(id); }
	public List<ScheduledTask> getAll() {
		List<ScheduledTask> result = new ArrayList<>(schedules.values());
		result.sort(Comparator.comparing(ScheduledTask::getId));
		return result;
	}
	public void remove(String id) { schedules.remove(id); }
}
