package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMemoryRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresMemoryRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM memory_records");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
		repository = new PostgresMemoryRepository(dataSource);
	}

	@Test
	void saveAndGetRoundTrip() {
		repository.save(record("mem-1", "project-a", MemoryType.PROJECT_RULE,
			"rule-1", "keep API stable", NOW));

		MemoryRecord stored = repository.get("mem-1");
		assertEquals("mem-1", stored.getId());
		assertEquals("project-a", stored.getProjectId());
		assertEquals(MemoryType.PROJECT_RULE, stored.getType());
		assertEquals("rule-1", stored.getKey());
		assertEquals("keep API stable", stored.getContent());
		assertEquals(NOW, stored.getCreatedAt());
		assertEquals(NOW, stored.getUpdatedAt());
		assertNull(repository.get("missing"));
	}

	@Test
	void saveUpdatesExistingRecord() {
		repository.save(record("mem-1", "project-a", MemoryType.PROJECT_RULE,
			"rule-1", "old", NOW));
		repository.save(record("mem-1", "project-a", MemoryType.AGENT_EXPERIENCE,
			"rule-1", "new", NOW.plusSeconds(60)));

		MemoryRecord stored = repository.get("mem-1");
		assertEquals(MemoryType.AGENT_EXPERIENCE, stored.getType());
		assertEquals("new", stored.getContent());
		assertEquals(NOW.plusSeconds(60), stored.getUpdatedAt());
	}

	@Test
	void listFiltersByProjectAndType() {
		repository.save(record("mem-1", "project-a", MemoryType.PROJECT_RULE, "r1", "a", NOW));
		repository.save(record("mem-2", "project-a", MemoryType.BUG_RECORD, "b1", "b", NOW));
		repository.save(record("mem-3", "project-b", MemoryType.PROJECT_RULE, "r2", "c", NOW));

		assertEquals(3, repository.list(null, null).size());
		assertEquals(2, repository.list("project-a", null).size());
		assertEquals(1, repository.list("project-a", MemoryType.PROJECT_RULE).size());
		assertEquals(0, repository.list("project-x", null).size());
	}

	@Test
	void deleteRemovesRecord() {
		repository.save(record("mem-1", "project-a", MemoryType.PROJECT_RULE, "r1", "a", NOW));

		assertTrue(repository.delete("mem-1"));
		assertNull(repository.get("mem-1"));
		assertFalse(repository.delete("mem-1"));
	}

	@Test
	void listReturnsRecordsOrderedByCreatedAt() {
		repository.save(record("mem-2", "project-a", MemoryType.PROJECT_RULE, "r2", "b",
			NOW.plusSeconds(10)));
		repository.save(record("mem-1", "project-a", MemoryType.PROJECT_RULE, "r1", "a", NOW));

		List<MemoryRecord> records = repository.list("project-a", null);
		assertEquals(List.of("mem-1", "mem-2"),
			records.stream().map(MemoryRecord::getId).toList());
	}

	private MemoryRecord record(String id, String projectId, MemoryType type,
			String key, String content, Instant timestamp) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId(projectId);
		record.setType(type);
		record.setKey(key);
		record.setContent(content);
		record.setCreatedAt(timestamp);
		record.setUpdatedAt(timestamp);
		return record;
	}
}
