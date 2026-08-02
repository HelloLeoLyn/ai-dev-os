package com.aidevos.orchestrator.tool.mcp;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ToolExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.tool.DefaultToolArtifactMapper;
import com.aidevos.orchestrator.tool.ToolArtifactMapper;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.tool.ToolRouter;
import com.aidevos.orchestrator.tool.policy.AllowRegisteredToolPolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolExecutionIntegrationTest {

	@Test
	void shouldPersistMcpArtifactThroughToolRouter() {
		McpToolProvider provider = provider();
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			new AllowRegisteredToolPolicy());
		ToolArtifactMapper mapper = new DefaultToolArtifactMapper(new ArtifactContentLimiter(10_000));
		AgentExecutor executor = new ToolExecutor(router, mapper);
		AgentDefinition agent = new AgentDefinition();
		agent.setName("mcp-test-agent");
		agent.setExecutor("test-mcp");
		TaskDefinition task = new TaskDefinition();
		task.setId("mcp-tool-task");
		task.setDescription("Call an explicitly selected read-only MCP tool");
		task.setAgentName("mcp-test-agent");
		task.setParameters(Map.of("tool", Map.of("provider", "filesystem", "name", "echo",
			"arguments", Map.of("value", "READY"), "invocationId", "mcp-invocation-1",
			"timeout", "PT2S")));
		AgentResolver resolver = mock(AgentResolver.class);
		when(resolver.resolve(task)).thenReturn(new ResolvedAgent(agent, executor));
		ExecutionRecordManager records = new ExecutionRecordManager();

		ExecutionResult result = new ExecutionEngine(resolver, records).execute(task);

		assertTrue(result.isSuccess());
		assertEquals("READY", result.getOutput());
		ExecutionRecord record = records.getAll().getFirst();
		assertEquals("SUCCESS", record.getStatus());
		assertEquals(2, record.getArtifacts().size());
		assertEquals(record.getExecutionId(),
			record.getArtifacts().getFirst().getMetadata().get("executionId"));
		assertEquals("mcp-invocation-1",
			record.getArtifacts().getFirst().getMetadata().get("invocationId"));
		router.close();
		provider.close();
	}

	private McpToolProvider provider() {
		ObjectMapper objectMapper = new ObjectMapper();
		String script = Path.of("src/test/resources/mcp/fake-mcp-server.js")
			.toAbsolutePath().normalize().toString();
		McpSession session = new McpStdioSession(List.of("node", script), null, objectMapper);
		return new McpToolProvider("filesystem",
			new McpClient(session, objectMapper, Duration.ofSeconds(2)), objectMapper);
	}

}
