package com.aidevos.orchestrator.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.aidevos.orchestrator.model.ExecutionRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryExecutionRecordRepository implements ExecutionRecordRepository {
	private final Map<String, ExecutionRecord> records = new LinkedHashMap<>();
	public synchronized void save(ExecutionRecord record) { records.put(record.getId(), record); }
	public synchronized ExecutionRecord get(String id) { return records.get(id); }
	public synchronized List<ExecutionRecord> getAll() { return new ArrayList<>(records.values()); }
}
