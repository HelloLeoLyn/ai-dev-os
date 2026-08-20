package com.aidevos.orchestrator.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryExecutionStateRepository implements ExecutionStateRepository {
	private final Map<String, RunExecutionState> states = new ConcurrentHashMap<>();
	public synchronized void save(RunExecutionState state) { states.put(state.getRunId(), state); }
	public synchronized RunExecutionState get(String id) { return states.get(id); }
	public synchronized List<RunExecutionState> getAll() { return new ArrayList<>(states.values()); }
}
