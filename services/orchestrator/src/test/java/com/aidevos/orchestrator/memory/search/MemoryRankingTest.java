package com.aidevos.orchestrator.memory.search;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRankingTest {

	private final MemoryRankingService ranking = new MemoryRankingService();

	@Test
	void shouldRankResolvedBugAboveUnresolvedBug() {
		List<MemoryMatch> matches = ranking.rank(List.of(
			record("unresolved", MemoryType.BUG_RECORD, "事务失效", "仍存在", null, false,
				Instant.now()),
			record("resolved", MemoryType.BUG_RECORD, "事务失效", "已修复", "检查 self invocation",
				true, Instant.now())), query("事务"));

		assertEquals("resolved", matches.get(0).memoryId());
		assertTrue(matches.get(0).score() > matches.get(1).score());
	}

	@Test
	void shouldRankAgentExperienceAboveHistoryTask() {
		List<MemoryMatch> matches = ranking.rank(List.of(
			record("task-1", MemoryType.HISTORY_TASK, "事务失效", "任务完成", null, null,
				Instant.now()),
			record("exp-1", MemoryType.AGENT_EXPERIENCE, "事务失效", "经验", "self invocation",
				null, Instant.now())), query("事务"));

		assertEquals("exp-1", matches.get(0).memoryId());
	}

	@Test
	void shouldRankRecentExperienceAboveOldExperience() {
		Instant now = Instant.parse("2026-08-01T00:00:00Z");
		List<MemoryMatch> matches = ranking.rank(List.of(
			record("old", MemoryType.AGENT_EXPERIENCE, "事务失效", "经验", "方案A", null,
				now.minusSeconds(180L * 24 * 3600)),
			record("recent", MemoryType.AGENT_EXPERIENCE, "事务失效", "经验", "方案B", null,
				now.minusSeconds(5L * 24 * 3600))), query("事务"));

		assertEquals("recent", matches.get(0).memoryId());
	}

	@Test
	void shouldGiveContextBonusForAgentType() {
		List<MemoryMatch> matches = ranking.rank(List.of(
			record("with-context", MemoryType.AGENT_EXPERIENCE, "事务失效", "经验 CODEX", "方案A",
				null, Instant.now()),
			record("without", MemoryType.AGENT_EXPERIENCE, "事务失效", "经验", "方案B", null,
				Instant.now())), new MemoryQuery("事务", null, "CODEX", null, 10));

		assertEquals("with-context", matches.get(0).memoryId());
	}

	private MemoryQuery query(String text) {
		return new MemoryQuery(text, null, null, null, 10);
	}

	private MemoryRecord record(String id, MemoryType type, String key, String content,
			String solution, Boolean resolved, Instant createdAt) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId("default");
		record.setType(type);
		record.setKey(key);
		record.setContent(content);
		record.setSolution(solution);
		record.setResolved(resolved);
		record.setCreatedAt(createdAt);
		record.setUpdatedAt(createdAt);
		return record;
	}
}
