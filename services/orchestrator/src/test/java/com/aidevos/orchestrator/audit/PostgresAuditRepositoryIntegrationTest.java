package com.aidevos.orchestrator.audit;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresAuditRepositoryIntegrationTest extends AuditRepositoryContract {
	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
	private PGSimpleDataSource dataSource;
	private AuditRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE audit_events RESTART IDENTITY");
		}
		repository = new PostgresAuditRepository(dataSource, new ObjectMapper());
	}

	@Override
	AuditRepository repository() { return repository; }

	@Test
	void storesNativeJsonbAndRunsAllMigrationsIdempotently() throws Exception {
		EventRecord stored = repository.append(event("event-json", "key-json",
			EventType.EXECUTION_RECORD_SAVED, Instant.parse("2026-08-03T02:00:01Z"),
			"job-json", "run-json"));

		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT pg_typeof(payload)::text,"
					+ "payload->'metadata'->'nested'->>'phase',payload->>'id' "
					+ "FROM audit_events WHERE id='event-json'")) {
			assertTrue(result.next());
			assertEquals("jsonb", result.getString(1));
			assertEquals("7-B1", result.getString(2));
			assertEquals(stored.id(), result.getString(3));
		}

		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT to_regclass('repository_documents')::text,"
					+ "to_regclass('audit_events')::text,to_regclass('idx_audit_events_job')::text")) {
			assertTrue(result.next());
			assertEquals("repository_documents", result.getString(1));
			assertEquals("audit_events", result.getString(2));
			assertEquals("idx_audit_events_job", result.getString(3));
		}
	}
}
