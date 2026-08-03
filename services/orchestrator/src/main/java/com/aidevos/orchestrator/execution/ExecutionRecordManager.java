package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.model.ExecutionRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class ExecutionRecordManager {

	private final ExecutionRecordRepository repository;
	private final ThreadLocal<AtomicReference<ExecutionRecord>> capture = new ThreadLocal<>();

	public ExecutionRecordManager() { this(new InMemoryExecutionRecordRepository()); }

	@Autowired
	public ExecutionRecordManager(ExecutionRecordRepository repository) {
		this.repository = repository;
	}

	public synchronized void save(ExecutionRecord executionRecord) {
		repository.save(executionRecord);
		AtomicReference<ExecutionRecord> capturedRecord = capture.get();
		if (capturedRecord != null) {
			capturedRecord.set(executionRecord);
		}
	}

	public synchronized ExecutionRecord get(String id) {
		return repository.get(id);
	}

	public synchronized List<ExecutionRecord> getAll() {
		return repository.getAll();
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
