package com.aidevos.orchestrator.observability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory trace store, sorted newest first for the trace views.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryTraceRepository implements TraceRepository {

	private final Map<String, TraceRecord> traces = new LinkedHashMap<>();

	@Override
	public synchronized void save(TraceRecord trace) {
		traces.put(trace.getTraceId(), trace);
	}

	@Override
	public synchronized TraceRecord get(String traceId) {
		return traces.get(traceId);
	}

	@Override
	public synchronized List<TraceRecord> listByTask(String taskId) {
		return sorted(traces.values().stream()
			.filter(trace -> trace.getTaskId() != null && trace.getTaskId().equals(taskId))
			.toList());
	}

	@Override
	public synchronized List<TraceRecord> listByProject(String projectId) {
		return sorted(traces.values().stream()
			.filter(trace -> trace.getProjectId() != null && trace.getProjectId().equals(projectId))
			.toList());
	}

	@Override
	public synchronized List<TraceRecord> listByAgent(String agentType) {
		return sorted(traces.values().stream()
			.filter(trace -> trace.getAgentType() != null && trace.getAgentType().equals(agentType))
			.toList());
	}

	private List<TraceRecord> sorted(List<TraceRecord> records) {
		List<TraceRecord> result = new ArrayList<>(records);
		result.sort(Comparator.comparing(TraceRecord::getStartTime).reversed());
		return List.copyOf(result);
	}
}
