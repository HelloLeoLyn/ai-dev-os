package com.aidevos.orchestrator.orchestrator;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionLimits;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.optimization.OptimizationRecord;
import com.aidevos.orchestrator.optimization.OptimizationType;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionNode;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dynamic graph planning verification: buildDynamic turns a code task with
 * learned failure recommendations into a bounded repair graph, keeps the
 * browser/test topologies stable and attaches the memory context.
 */
class DynamicGraphPlanningTest {

	private final ExecutionGraphBuilder builder = new ExecutionGraphBuilder();

	@Test
	void cleanCodeTaskBuildsPlainCodeFlow() {
		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.CODE_GENERATION,
			new MemoryContext(), List.of());

		assertEquals(3, graph.getNodes().size());
		assertEquals(List.of("HERMES_PLANNING", "CODEX_IMPLEMENTATION",
			"TEST_AGENT_VERIFY"), graph.getTopologicalOrder());
		assertFalse(graph.hasLoop());
		assertEquals("task-1", graph.getTaskId());
		assertNotNull(graph.getGraphId());
	}

	@Test
	void failurePatternRecommendationBuildsRepairGraph() {
		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.CODE_GENERATION,
			new MemoryContext(), List.of(
				new OptimizationRecord("opt-1", "task-1", null,
					OptimizationType.FAILURE_PATTERN, "failed node detected", 0.7,
					Instant.now())));

		assertTrue(graph.hasLoop());
		assertEquals("REPAIR_AGENT_ANALYZE", graph.getLoopStartNodeId());
		assertEquals("TEST_AGENT_VERIFY", graph.getLoopEndNodeId());
		assertEquals(ExecutionLimits.DEFAULT_MAX_REPAIR_ATTEMPTS, graph.getMaxAttempts());
		assertTrue(graph.getTopologicalOrder().contains("CODEX_FIX"));
	}

	@Test
	void graphFlowRecommendationBuildsRepairGraph() {
		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.CODE_GENERATION,
			new MemoryContext(), List.of(
				new OptimizationRecord("opt-1", "task-1", null,
					OptimizationType.GRAPH_FLOW, "reorder nodes", 0.6, Instant.now())));

		assertTrue(graph.hasLoop());
	}

	@Test
	void browserTaskKeepsBrowserShapeDespiteRecommendations() {
		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.BROWSER_TEST,
			new MemoryContext(), List.of(
				new OptimizationRecord("opt-1", "task-1", null,
					OptimizationType.FAILURE_PATTERN, "failed", 0.8, Instant.now())));

		assertTrue(graph.getTopologicalOrder().contains("OPENCLAW_EXECUTE"));
		assertFalse(graph.hasLoop());
	}

	@Test
	void testTaskBuildsSingleVerifyNode() {
		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.TEST_VERIFY,
			new MemoryContext(), List.of());

		assertEquals(List.of("TEST_AGENT_VERIFY"), graph.getTopologicalOrder());
	}

	@Test
	void memoryContextIsAttachedToDynamicGraph() {
		MemoryContext memory = new MemoryContext(List.of(), List.of(),
			List.of("known flaky tests"), List.of());

		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.CODE_GENERATION,
			memory, List.of());

		assertNotNull(graph.getMemoryContext());
		assertEquals(List.of("known flaky tests"), graph.getMemoryContext().getWarnings());
	}

	@Test
	void dynamicGraphNodesArePending() {
		ExecutionGraph graph = builder.buildDynamic(task("task-1"), TaskType.CODE_GENERATION,
			new MemoryContext(), List.of());

		assertTrue(graph.getNodes().stream()
			.allMatch(node -> node.getStatus()
				== com.aidevos.orchestrator.orchestration.ExecutionNodeStatus.PENDING));
	}

	private TaskRecord task(String taskId) {
		return new TaskRecord(taskId, "Implement login", "Append a line to a.txt",
			"project-x", "workspace-1");
	}
}
