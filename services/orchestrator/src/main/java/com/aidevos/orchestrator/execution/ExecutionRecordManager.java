package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.model.ExecutionRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExecutionRecordManager {

	private final Map<String, ExecutionRecord> records = new LinkedHashMap<>();

	public void save(ExecutionRecord executionRecord) {
		records.put(executionRecord.getId(), executionRecord);
	}

	public ExecutionRecord get(String id) {
		return records.get(id);
	}

	public List<ExecutionRecord> getAll() {
		return new ArrayList<>(records.values());
	}
}
