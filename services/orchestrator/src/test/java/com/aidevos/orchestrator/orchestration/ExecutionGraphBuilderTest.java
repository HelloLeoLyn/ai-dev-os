package com.aidevos.orchestrator.orchestration;

import java.util.List;

import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.repair.RepairPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionGraphBuilderTest {

	private final ExecutionGraphBuilder builder = new ExecutionGraphBuilder();

	@Test
	void shouldBuildCodeGraphHermesCodexTest() {
		ExecutionGraph graph = builder.build("task-1", "CODE_TASK");

		assertEquals("task-1", graph.getTaskId());
		assertNotNull(graph.getGraphId());
		List<String> order = graph.getTopologicalOrder();
		assertEquals(List.of("HERMES_PLANNING", "CODEX_IMPLEMENTATION",
			"TEST_AGENT_VERIFY"), order);
		assertEquals(ExecutionNodeStatus.PENDING,
			graph.getNode("HERMES_PLANNING").getStatus());
		assertEquals("HERMES_PLANNING",
			graph.getNode("CODEX_IMPLEMENTATION").getDependencies().get(0));
		assertEquals("CODEX_IMPLEMENTATION",
			graph.getNode("TEST_AGENT_VERIFY").getDependencies().get(0));
		assertFalse(graph.hasLoop());
	}

	@Test
	void shouldBuildRepairGraphWithBoundedLoop() {
		ExecutionGraph graph = builder.build("task-1", "REPAIR_TASK");

		List<String> order = graph.getTopologicalOrder();
		assertEquals(List.of("TEST_AGENT_ANALYZE", "REPAIR_AGENT_ANALYZE",
			"CODEX_FIX", "TEST_AGENT_VERIFY"), order);
		assertTrue(graph.hasLoop());
		assertEquals("REPAIR_AGENT_ANALYZE", graph.getLoopStartNodeId());
		assertEquals("TEST_AGENT_VERIFY", graph.getLoopEndNodeId());
		assertEquals(RepairPolicy.MAX_RETRY, graph.getMaxAttempts());
	}

	@Test
	void shouldBuildBrowserGraph() {
		ExecutionGraph graph = builder.build("task-1", TaskType.BROWSER_TEST);
		assertEquals(List.of("HERMES_PLANNING", "OPENCLAW_EXECUTE", "TEST_AGENT_VERIFY"),
			graph.getTopologicalOrder());
	}

	@Test
	void shouldBuildTestGraph() {
		ExecutionGraph graph = builder.build("task-1", TaskType.TEST_VERIFY);
		assertEquals(List.of("TEST_AGENT_VERIFY"), graph.getTopologicalOrder());
	}

	@Test
	void shouldDefaultToCodeGraphForGeneralTasks() {
		ExecutionGraph graph = builder.build("task-1", TaskType.GENERAL);
		assertEquals(List.of("HERMES_PLANNING", "CODEX_IMPLEMENTATION", "TEST_AGENT_VERIFY"),
			graph.getTopologicalOrder());
	}
}
