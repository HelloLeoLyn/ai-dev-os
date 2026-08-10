package com.aidevos.orchestrator.project;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.memory.search.MemoryQuery;
import com.aidevos.orchestrator.memory.search.MemoryRankingService;
import com.aidevos.orchestrator.memory.search.MemorySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-B: memory records are scoped by project; cross-project search does
 * not mix experience.
 */
class ProjectMemoryIsolationTest {

	private MemorySearchService searchService;

	@BeforeEach
	void setUp() {
		InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
		memory(repository, "mem-a", "project-a", "Spring 事务失效", "方案A");
		memory(repository, "mem-b", "project-b", "Spring 事务失效", "方案B");
		searchService = new MemorySearchService(repository, new MemoryRankingService());
	}

	@Test
	void shouldNotMixMemoryAcrossProjects() {
		List<com.aidevos.orchestrator.memory.search.MemoryMatch> projectA = searchService.search(
			new MemoryQuery("事务", null, null, "project-a", 10));

		assertEquals(1, projectA.size());
		assertEquals("mem-a", projectA.get(0).memoryId());
	}

	@Test
	void shouldReturnEmptyForProjectWithoutMemory() {
		assertTrue(searchService.search(
			new MemoryQuery("事务", null, null, "project-c", 10)).isEmpty());
	}

	private void memory(InMemoryMemoryRepository repository, String id, String projectId,
			String content, String solution) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId(projectId);
		record.setType(MemoryType.HISTORY_TASK);
		record.setKey(id);
		record.setContent(content);
		record.setSolution(solution);
		record.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
		record.setUpdatedAt(record.getCreatedAt());
		repository.save(record);
	}
}
