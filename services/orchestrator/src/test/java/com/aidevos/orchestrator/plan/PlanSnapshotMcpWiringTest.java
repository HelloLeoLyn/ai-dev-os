package com.aidevos.orchestrator.plan;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcp.tool.InMemoryToolRegistry;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.McpToolExecutor;
import com.aidevos.orchestrator.mcp.tool.ToolPermission;
import com.aidevos.orchestrator.mcp.tool.ToolType;
import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.tool.UnifiedMcpToolProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanSnapshotMcpWiringTest {

	@Test
	void includesUnifiedMcpToolsInPlanningSnapshot() {
		McpToolExecutor filesystem = mock(McpToolExecutor.class);
		when(filesystem.definition()).thenReturn(new com.aidevos.orchestrator.mcp.tool.ToolDefinition(
			"filesystem", "Filesystem", ToolType.FILESYSTEM, "Read workspace", Map.of(),
			Set.of(ToolPermission.READ)));
		McpToolExecutor git = mock(McpToolExecutor.class);
		when(git.definition()).thenReturn(new com.aidevos.orchestrator.mcp.tool.ToolDefinition(
			"git", "Git", ToolType.GIT, "Inspect git", Map.of(),
			Set.of(ToolPermission.READ, ToolPermission.WRITE)));
		ExecutorRegistry executors = mock(ExecutorRegistry.class);
		when(executors.getTypes()).thenReturn(Set.of());
		InMemoryToolRegistry unified = new InMemoryToolRegistry(List.of(filesystem, git));
		UnifiedMcpToolProvider bridge = new UnifiedMcpToolProvider(unified,
			mock(McpToolRouter.class));

		PlanSnapshot snapshot = new PlanSnapshotFactory(new AgentManager(),
			new ToolRegistry(List.of(bridge)), executors).capture("v1", Map.of());

		assertEquals(2, snapshot.tools().size());
		assertTrue(snapshot.tools().stream().anyMatch(tool -> "unified-mcp".equals(tool.providerId())
			&& "filesystem".equals(tool.name())
			&& tool.access() == ToolAccess.READ_ONLY));
		assertTrue(snapshot.tools().stream().anyMatch(tool -> "unified-mcp".equals(tool.providerId())
			&& "git".equals(tool.name())
			&& tool.access() == ToolAccess.WORKSPACE_WRITE));
	}
}
