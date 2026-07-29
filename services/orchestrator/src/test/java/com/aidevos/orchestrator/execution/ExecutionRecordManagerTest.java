package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.model.ExecutionRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionRecordManagerTest {

	@Test
	void shouldSaveAndQueryRecordsInInsertionOrder() {
		ExecutionRecordManager manager = new ExecutionRecordManager();
		ExecutionRecord firstRecord = createRecord("record-1", "task-1");
		ExecutionRecord secondRecord = createRecord("record-2", "task-2");

		manager.save(firstRecord);
		manager.save(secondRecord);

		assertSame(firstRecord, manager.get("record-1"));
		assertSame(secondRecord, manager.get("record-2"));
		List<ExecutionRecord> records = manager.getAll();
		assertEquals(2, records.size());
		assertSame(firstRecord, records.get(0));
		assertSame(secondRecord, records.get(1));
	}

	private ExecutionRecord createRecord(String id, String taskId) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setTaskId(taskId);
		return record;
	}
}
