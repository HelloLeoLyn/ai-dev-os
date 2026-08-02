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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpFilesystemAcceptanceTest {

	@Test
	void shouldReadFileThroughRealFilesystemMcpServer() {
		Assumptions.assumeTrue(Boolean.getBoolean("mcp.filesystem.acceptance"));
		Path workspace = Path.of("").toAbsolutePath().normalize();
		ObjectMapper objectMapper = new ObjectMapper();
		McpSession session = new McpStdioSession(List.of("npx", "--yes",
			"@modelcontextprotocol/server-filesystem", workspace.toString()),
			workspace.toString(), objectMapper);
		McpToolProvider provider = new McpToolProvider("filesystem",
			new McpClient(session, objectMapper, Duration.ofSeconds(30)), objectMapper);
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			new AllowRegisteredToolPolicy());
		ToolArtifactMapper mapper = new DefaultToolArtifactMapper(
			new ArtifactContentLimiter(100_000));
		AgentExecutor executor = new ToolExecutor(router, mapper);
		AgentDefinition agent = new AgentDefinition();
		agent.setName("filesystem-reader");
		agent.setExecutor("test-filesystem-mcp");
		TaskDefinition task = new TaskDefinition();
		task.setId("filesystem-read-task");
		task.setDescription("Read pom.xml using an explicitly selected read-only MCP tool");
		task.setAgentName("filesystem-reader");
		task.setParameters(Map.of("tool", Map.of("provider", "filesystem",
			"name", "read_text_file", "arguments",
			Map.of("path", workspace.resolve("pom.xml").toString()),
			"invocationId", "filesystem-invocation-1", "timeout", "PT30S")));
		AgentResolver resolver = mock(AgentResolver.class);
		when(resolver.resolve(task)).thenReturn(new ResolvedAgent(agent, executor));
		ExecutionRecordManager records = new ExecutionRecordManager();

		ExecutionResult result = new ExecutionEngine(resolver, records).execute(task);

		assertTrue(result.isSuccess(), result.getMessage());
		assertTrue(result.getOutput().contains("<artifactId>orchestrator</artifactId>"));
		ExecutionRecord record = records.getAll().getFirst();
		assertEquals("SUCCESS", record.getStatus());
		assertTrue(record.getArtifacts().stream()
			.anyMatch(artifact -> "mcp-text".equals(artifact.getType())
				&& artifact.getContent().contains("<artifactId>orchestrator</artifactId>")));
		assertTrue(record.getArtifacts().stream()
			.allMatch(artifact -> record.getExecutionId().equals(
				artifact.getMetadata().get("executionId"))));
		router.close();
		provider.close();
	}

}
