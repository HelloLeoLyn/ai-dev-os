package com.aidevos.orchestrator.persistence.postgresql;

import javax.sql.DataSource;

import com.aidevos.orchestrator.validation.ValidationArtifact;
import com.aidevos.orchestrator.validation.ValidationArtifactRepository;

final class PostgresValidationArtifactRepository implements ValidationArtifactRepository {
	private static final String TYPE = "validation-artifact";
	private volatile PostgresDocumentStore store;
	private final DataSource dataSource;
	private final tools.jackson.databind.ObjectMapper mapper;
	PostgresValidationArtifactRepository(PostgresDocumentStore store) {
		this.store = store; this.dataSource = null; this.mapper = null;
	}
	PostgresValidationArtifactRepository(DataSource dataSource, tools.jackson.databind.ObjectMapper mapper) {
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
	@Override public void save(ValidationArtifact artifact) {
		store().put(TYPE, artifact.getArtifactId(), artifact, artifact.getValidationRunId());
	}
	@Override public ValidationArtifact get(String id) {
		return store().get(TYPE, id, ValidationArtifact.class);
	}
}
