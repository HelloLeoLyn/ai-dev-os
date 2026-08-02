package com.aidevos.orchestrator.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.tool.policy.ToolPolicyDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolExecutionIntegrationTest {

	@Test
	void shouldExecuteFakeToolAndPersistMappedArtifact() {
		FakeToolProvider provider = new FakeToolProvider("fake", "echo",
			invocation -> ToolResult.success("READY",
				List.of(ToolContent.text("fake-result.txt", "READY"))));
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			(definition, invocation) -> ToolPolicyDecision.allow());
		ToolArtifactMapper artifactMapper = new DefaultToolArtifactMapper(
			new ArtifactContentLimiter(10_000));
		AgentExecutor executor = new RouterBackedExecutor(router, artifactMapper);
		AgentDefinition agent = new AgentDefinition();
		agent.setName("tool-agent");
		agent.setExecutor("test-tool");
		AgentResolver resolver = mock(AgentResolver.class);
		TaskDefinition task = new TaskDefinition();
		task.setId("tool-task");
		task.setDescription("Call fake echo tool");
		task.setAgentName("tool-agent");
		when(resolver.resolve(task)).thenReturn(new ResolvedAgent(agent, executor));
		ExecutionRecordManager records = new ExecutionRecordManager();

		ExecutionResult result = new ExecutionEngine(resolver, records).execute(task);

		assertTrue(result.isSuccess());
		assertEquals("READY", result.getOutput());
		assertEquals(1, result.getArtifacts().size());
		ExecutionRecord record = records.getAll().getFirst();
		assertEquals("SUCCESS", record.getStatus());
		assertEquals(result.getArtifacts(), record.getArtifacts());
		assertEquals(record.getExecutionId(),
			record.getArtifacts().getFirst().getMetadata().get("executionId"));
		assertEquals("invocation-fixed",
			record.getArtifacts().getFirst().getMetadata().get("invocationId"));
		router.close();
	}

	private static class RouterBackedExecutor implements AgentExecutor {

		private final ToolRouter router;
		private final ToolArtifactMapper artifactMapper;

		RouterBackedExecutor(ToolRouter router, ToolArtifactMapper artifactMapper) {
			this.router = router;
			this.artifactMapper = artifactMapper;
		}

		@Override
		public String getType() {
			return "test-tool";
		}

		@Override
		public ExecutionResult execute(ExecutionContext context) {
			ToolInvocation invocation = new ToolInvocation(context.getExecutionId(),
				"invocation-fixed", "fake", "echo", Map.of("value", "READY"),
				Duration.ofSeconds(1));
			ToolResult toolResult = router.invoke(invocation);
			ExecutionResult result = new ExecutionResult();
			result.setSuccess(toolResult.success());
			result.setMessage(toolResult.message());
			result.setOutput(toolResult.output());
			result.setArtifacts(artifactMapper.map(invocation, toolResult));
			return result;
		}
	}
}
