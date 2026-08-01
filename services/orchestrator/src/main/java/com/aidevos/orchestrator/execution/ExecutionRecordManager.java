package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.model.ExecutionRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class ExecutionRecordManager {

	private final Map<String, ExecutionRecord> records = new LinkedHashMap<>();
	private final ThreadLocal<AtomicReference<ExecutionRecord>> capture = new ThreadLocal<>();

	public synchronized void save(ExecutionRecord executionRecord) {
		records.put(executionRecord.getId(), executionRecord);
		AtomicReference<ExecutionRecord> capturedRecord = capture.get();
		if (capturedRecord != null) {
			capturedRecord.set(executionRecord);
		}
	}

	public synchronized ExecutionRecord get(String id) {
		return records.get(id);
	}

	public synchronized List<ExecutionRecord> getAll() {
		return new ArrayList<>(records.values());
	}

	public <T> ExecutionCapture<T> capture(Supplier<T> execution) {
		if (capture.get() != null) {
			throw new IllegalStateException("Execution record capture is already active");
		}
		AtomicReference<ExecutionRecord> capturedRecord = new AtomicReference<>();
		capture.set(capturedRecord);
		try {
			return new ExecutionCapture<>(execution.get(), capturedRecord.get());
		}
		finally {
			capture.remove();
		}
	}
}
