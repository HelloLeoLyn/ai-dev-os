package com.aidevos.orchestrator.memory.search;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 16-B: MemorySearchService keyword matching, type filtering and
 * rule-based ordering over the in-memory repository.
 */
class MemorySearchServiceTest {

	private InMemoryMemoryRepository repository;
	private MemorySearchService searchService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryMemoryRepository();
		searchService = new MemorySearchService(repository, new MemoryRankingService());
		seed();
	}

	@Test
	void shouldMatchByKeywordAcrossBugHistoryAndExperience() {
		List<MemoryMatch> matches = searchService.search(
			new MemoryQuery("事务", null, null, "project-x", 10));

		assertTrue(matches.stream().anyMatch(match -> match.type() == MemoryType.BUG_RECORD));
		assertTrue(matches.stream().anyMatch(match -> match.type() == MemoryType.HISTORY_TASK));
		assertTrue(matches.stream().anyMatch(match -> match.type() == MemoryType.AGENT_EXPERIENCE));
	}

	@Test
	void shouldFilterByMemoryType() {
		List<MemoryMatch> matches = searchService.search(
			new MemoryQuery("事务", null, null, "project-x", 10), MemoryType.BUG_RECORD);

		assertEquals(1, matches.size());
		assertEquals(MemoryType.BUG_RECORD, matches.get(0).type());
		assertEquals("bug-1", matches.get(0).memoryId());
	}

	@Test
	void shouldRespectProjectScope() {
		assertTrue(searchService.search(
			new MemoryQuery("事务", null, null, "other-project", 10)).isEmpty());
	}

	@Test
	void shouldLimitResults() {
		List<MemoryMatch> matches = searchService.search(
			new MemoryQuery("事务", null, null, "project-x", 2));

		assertTrue(matches.size() <= 2);
	}

	@Test
	void shouldReturnSolutionsFromResolvedBugRecords() {
		List<MemoryMatch> matches = searchService.search(
			new MemoryQuery("事务", null, null, "project-x", 10));

		MemoryMatch bug = matches.stream().filter(match -> "bug-1".equals(match.memoryId()))
			.findFirst().orElseThrow();
		assertEquals("检查 self invocation", bug.solution());
	}

	private void seed() {
		memory("bug-1", MemoryType.BUG_RECORD, "bug:spring-transaction",
			"Spring Boot 事务失效: 自调用导致代理失效", "检查 self invocation", true,
			Instant.parse("2026-07-20T00:00:00Z"));
		memory("task-1", MemoryType.HISTORY_TASK, "history:task-spring",
			"历史任务: 处理事务失效", "使用 @Transactional 代理调用", null,
			Instant.parse("2026-07-10T00:00:00Z"));
		memory("exp-1", MemoryType.AGENT_EXPERIENCE, "experience:transaction",
			"经验: 拆分为独立事务方法", null, null, Instant.parse("2026-07-01T00:00:00Z"));
	}

	private void memory(String id, MemoryType type, String key, String content,
			String solution, Boolean resolved, Instant createdAt) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId("project-x");
		record.setType(type);
		record.setKey(key);
		record.setContent(content);
		record.setSolution(solution);
		record.setResolved(resolved);
		record.setCreatedAt(createdAt);
		record.setUpdatedAt(createdAt);
		repository.save(record);
	}
}
