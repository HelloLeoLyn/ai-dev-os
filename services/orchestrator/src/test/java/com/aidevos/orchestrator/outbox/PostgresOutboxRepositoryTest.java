package com.aidevos.orchestrator.outbox;

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
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresOutboxRepositoryTest extends OutboxRepositoryContract {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PGSimpleDataSource dataSource;
	private PostgresOutboxRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE audit_outbox RESTART IDENTITY");
		}
		repository = new PostgresOutboxRepository(dataSource);
	}

	@Override
	OutboxRepository repository() { return repository; }

	@Override
	Instant now() { return Instant.now().plusSeconds(60); }

	@Test
	void storesJsonbPayloadWithRelayControlColumns() throws Exception {
		repository.enqueue("audit", "key-json", "{\"phase\":\"8-E\"}");
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT pg_typeof(event_payload)::text,"
					+ "topic,next_attempt_at IS NOT NULL,dead_lettered_at IS NULL "
					+ "FROM audit_outbox WHERE idempotency_key='key-json'")) {
			assertTrue(result.next());
			assertEquals("jsonb", result.getString(1));
			assertEquals("audit", result.getString(2));
			assertTrue(result.getBoolean(3));
			assertTrue(result.getBoolean(4));
		}
	}

	@Test
	void migrationAddsRelayClaimIndex() throws Exception {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT indexname FROM pg_indexes "
					+ "WHERE tablename='audit_outbox' AND indexname='idx_audit_outbox_claim'")) {
			assertTrue(result.next());
		}
	}
}
