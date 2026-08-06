package com.aidevos.orchestrator.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryServiceTest {

	private MemoryService service;
	private InMemoryMemoryRepository repository;

	@BeforeEach
	void setUp() {
		repository = new InMemoryMemoryRepository();
		service = new MemoryService(repository);
	}

	@Test
	void shouldCreateWithGeneratedIdAndTimestamps() {
		MemoryRecord record = record(null, null, MemoryType.PROJECT_RULE, "rule-1", "keep API stable");

		MemoryRecord saved = service.create(record);

		assertNotNull(saved.getId());
		assertEquals("default", saved.getProjectId());
		assertEquals(MemoryType.PROJECT_RULE, saved.getType());
		assertNotNull(saved.getCreatedAt());
		assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
		assertNotNull(service.get(saved.getId()));
	}

	@Test
	void shouldCreateWithProvidedIdAndProject() {
		MemoryRecord record = record("mem-1", "project-a", MemoryType.BUG_RECORD, "bug-1", "memory leak on retry");

		MemoryRecord saved = service.create(record);

		assertEquals("mem-1", saved.getId());
		assertEquals("project-a", saved.getProjectId());
	}

	@Test
	void shouldRejectInvalidRecord() {
		assertThrows(IllegalArgumentException.class,
			() -> service.create(record(null, null, null, "k", "content")));
		assertThrows(IllegalArgumentException.class,
			() -> service.create(record(null, null, MemoryType.PROJECT_RULE, "  ", "content")));
		assertThrows(IllegalArgumentException.class,
			() -> service.create(record(null, null, MemoryType.PROJECT_RULE, "k", " ")));
	}

	@Test
	void shouldUpdateExistingRecord() {
		MemoryRecord created = service.create(
			record(null, null, MemoryType.PROJECT_RULE, "rule-1", "old content"));

		MemoryRecord changes = new MemoryRecord();
		changes.setContent("new content");
		changes.setType(MemoryType.AGENT_EXPERIENCE);
		MemoryRecord updated = service.update(created.getId(), changes);

		assertEquals("new content", updated.getContent());
		assertEquals(MemoryType.AGENT_EXPERIENCE, updated.getType());
		assertEquals("rule-1", updated.getKey());
		assertTrue(updated.getUpdatedAt().isAfter(created.getCreatedAt()));
	}

	@Test
	void shouldRejectUpdateForMissingRecord() {
		assertThrows(IllegalArgumentException.class,
			() -> service.update("missing", new MemoryRecord()));
	}

	@Test
	void shouldListFilteredByProjectAndType() {
		service.create(record(null, "project-a", MemoryType.PROJECT_RULE, "r1", "rule one"));
		service.create(record(null, "project-a", MemoryType.BUG_RECORD, "b1", "bug one"));
		service.create(record(null, "project-b", MemoryType.PROJECT_RULE, "r2", "rule two"));

		assertEquals(3, service.list(null, null).size());
		assertEquals(2, service.list("project-a", null).size());
		assertEquals(1, service.list("project-a", MemoryType.PROJECT_RULE).size());
		assertEquals(1, service.list(null, MemoryType.BUG_RECORD).size());
		assertEquals(0, service.list("project-x", null).size());
	}

	@Test
	void shouldDeleteRecord() {
		MemoryRecord created = service.create(
			record(null, null, MemoryType.HISTORY_TASK, "h1", "task done"));

		assertTrue(service.delete(created.getId()));
		assertNull(service.get(created.getId()));
		assertFalse(service.delete(created.getId()));
	}

	private MemoryRecord record(String id, String projectId, MemoryType type,
			String key, String content) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId(projectId);
		record.setType(type);
		record.setKey(key);
		record.setContent(content);
		return record;
	}
}
