package com.aidevos.orchestrator.optimization;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentTeamStatus;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Learning loop verification: a failing graph run fails the runtime session
 * and the collaboration team, and the subsequent optimization analysis
 * records failure-pattern recommendations, scores the agents and persists an
 * AGENT_EXPERIENCE memory record carrying the failed pattern and the
 * recommendation.
 */
class LearningLoopTest extends OptimizationTestBase {

	@Test
	void failingRunIsLearnedIntoFailurePatternExperience() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			failure(AgentType.CODEX, "compile error"), success(AgentType.TEST_AGENT));
		runtime.startSession("task-1");

		AgentSession session = runtime.sessionsForTask("task-1").get(0);
		assertEquals(AgentSessionStatus.FAILED, session.getStatus());
		assertEvent(EventType.SESSION_FAILED);
		assertEvent(EventType.AGENT_COLLABORATION_FAILED);

		List<OptimizationRecord> records = optimizationService.analyzeTask("task-1");

		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.FAILURE_PATTERN
				&& record.getRecommendation().contains("failed")));
		assertTrue(records.stream().anyMatch(record ->
			record.getType() == OptimizationType.GRAPH_FLOW
				&& record.getRecommendation().contains("replacing")));
		assertEvent(EventType.AGENT_SCORE_UPDATED);
		assertEvent(EventType.OPTIMIZATION_STARTED);
		assertEvent(EventType.OPTIMIZATION_COMPLETED);

		List<MemoryRecord> experience = memoryService.list("project-x",
			MemoryType.AGENT_EXPERIENCE);
		assertFalse(experience.isEmpty());
		MemoryRecord learned = experience.stream()
			.filter(record -> record.getKey() != null
				&& record.getKey().startsWith("agent-experience:optimization:"))
			.findFirst()
			.orElseThrow();
		String content = learned.getContent();
		assertTrue(content.contains("failedPattern"));
		assertTrue(content.contains("FAILURE_PATTERN"));
	}

	@Test
	void analyzeTwiceAppendsRecommendationsAndExperience() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		runtime.startSession("task-1");

		optimizationService.analyzeTask("task-1");
		optimizationService.analyzeTask("task-1");

		long learned = memoryService.list("project-x", MemoryType.AGENT_EXPERIENCE).stream()
			.filter(record -> record.getKey() != null
				&& record.getKey().startsWith("agent-experience:optimization:"))
			.count();
		assertEquals(2, learned);
		assertFalse(optimizationService.getAllRecommendations().isEmpty());
		assertEquals(1, collaborationService.teamForTask("task-1")
			.filter(team -> team.getStatus() == AgentTeamStatus.COMPLETED).stream()
			.count());
	}
}
