package com.aidevos.orchestrator.optimization;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Optimization integration verification: running a real execution graph
 * through the runtime, then analyzing the task produces recommendations, the
 * OPTIMIZATION_STARTED / OPTIMIZATION_RECOMMENDED / OPTIMIZATION_COMPLETED
 * audit trail and the AGENT_EXPERIENCE memory record.
 */
class OptimizationIntegrationTest extends OptimizationTestBase {

	@Test
	void analyzeAfterSuccessfulGraphRunProducesRecommendationsAndExperience() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		runtime.startSession("task-1");

		List<OptimizationRecord> records = optimizationService.analyzeTask("task-1");

		assertFalse(records.isEmpty());
		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.TOOL_USAGE));
		assertTrue(records.stream().allMatch(record ->
			"task-1".equals(record.getTaskId())));
		assertEvent(EventType.OPTIMIZATION_STARTED);
		assertEvent(EventType.OPTIMIZATION_RECOMMENDED);
		assertEvent(EventType.OPTIMIZATION_COMPLETED);

		List<MemoryRecord> experience = memoryService.list("project-x",
			MemoryType.AGENT_EXPERIENCE);
		assertFalse(experience.isEmpty());
		MemoryRecord learned = experience.stream()
			.filter(record -> record.getKey() != null
				&& record.getKey().startsWith("agent-experience:optimization:"))
			.findFirst()
			.orElseThrow();
		assertTrue(learned.getContent().contains("任务: task-1"));
		assertTrue(learned.getContent().contains("recommendation"));
	}

	@Test
	void recommendationsAreScopedPerTask() {
		task("task-1");
		task("task-2");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		runtime.startSession("task-1");
		runtime.startSession("task-2");
		optimizationService.analyzeTask("task-1");
		optimizationService.analyzeTask("task-2");

		assertFalse(optimizationService.getRecommendations("task-1").isEmpty());
		assertFalse(optimizationService.getRecommendations("task-2").isEmpty());
		assertTrue(optimizationService.getRecommendations("task-1").stream()
			.allMatch(record -> "task-1".equals(record.getTaskId())));
		assertEquals(0, optimizationService.getRecommendations("other-task").size());
	}

	@Test
	void getRecommendationByIdReturnsRecordedOptimization() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		runtime.startSession("task-1");
		List<OptimizationRecord> records = optimizationService.analyzeTask("task-1");

		OptimizationRecord record = optimizationService
			.getRecommendation(records.get(0).getId()).orElseThrow();

		assertEquals(records.get(0).getId(), record.getId());
		assertEquals("task-1", record.getTaskId());
	}
}
