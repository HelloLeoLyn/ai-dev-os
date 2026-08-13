package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;
import javax.sql.DataSource;

import com.aidevos.orchestrator.validation.ValidationRepository;
import com.aidevos.orchestrator.validation.ValidationRun;

final class PostgresValidationRepository implements ValidationRepository {
	private static final String TYPE = "validation-run";
	private volatile PostgresDocumentStore store;
	private final DataSource dataSource;
	private final tools.jackson.databind.ObjectMapper mapper;
	PostgresValidationRepository(PostgresDocumentStore store) {
		this.store = store; this.dataSource = null; this.mapper = null;
	}
	PostgresValidationRepository(DataSource dataSource, tools.jackson.databind.ObjectMapper mapper) {
		this.dataSource = dataSource; this.mapper = mapper;
	}
	private PostgresDocumentStore store() {
		PostgresDocumentStore current = store;
		if (current == null) synchronized (this) {
			if (store == null) store = new PostgresDocumentStore(dataSource, mapper);
			current = store;
		}
		return current;
	}
	@Override public void save(ValidationRun run) {
		store().put(TYPE, run.getValidationRunId(), run, run.getTaskId());
	}
	@Override public ValidationRun get(String id) { return store().get(TYPE, id, ValidationRun.class); }
	@Override public List<ValidationRun> findByTaskId(String taskId) {
		return store().allBySecondary(TYPE, taskId, ValidationRun.class);
	}
	@Override public List<ValidationRun> list() { return store().all(TYPE, ValidationRun.class); }
}
