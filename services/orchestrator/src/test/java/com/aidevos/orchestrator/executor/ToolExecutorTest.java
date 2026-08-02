package com.aidevos.orchestrator.executor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.tool.DefaultToolArtifactMapper;
import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolContent;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolProvider;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.tool.ToolResult;
import com.aidevos.orchestrator.tool.ToolRouter;
import com.aidevos.orchestrator.tool.policy.AllowRegisteredToolPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

	private ToolRouter router;

	@AfterEach
	void closeRouter() {
		if (router != null) {
			router.close();
		}
	}

	@Test
	void shouldExecuteExplicitToolAndMapArtifact() {
		ToolExecutor executor = executor(invocation -> ToolResult.success("READY",
			List.of(ToolContent.text("result.txt", "READY"))));

		ExecutionResult result = executor.execute(context("echo", Map.of("value", "READY"), "PT1S"));

		assertTrue(result.isSuccess());
		assertEquals("READY", result.getOutput());
		assertEquals(1, result.getArtifacts().size());
		assertEquals("execution-1", result.getMetadata().get("toolExecutionId"));
		assertNotNull(result.getMetadata().get("toolInvocationId"));
	}

	@Test
	void shouldSupportServerAlias() {
		ToolExecutor executor = executor(invocation -> ToolResult.success("READY", List.of()));
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId("execution-1");
		context.setParameters(Map.of("tool", Map.of("server", "filesystem", "name", "echo",
			"arguments", Map.of(), "timeout", "PT1S")));

		ExecutionResult result = executor.execute(context);

		assertTrue(result.isSuccess());
		assertEquals("filesystem", result.getMetadata().get("toolProviderId"));
	}

	@Test
	void shouldReturnProviderFailureAndUnknownTool() {
		ToolExecutor executor = executor(invocation -> ToolResult.failure("MCP_TOOL_ERROR",
			"invalid arguments"));

		ExecutionResult failure = executor.execute(context("echo", Map.of(), "PT1S"));
		ExecutionResult missing = executor.execute(context("missing", Map.of(), "PT1S"));

		assertFalse(failure.isSuccess());
		assertEquals("MCP_TOOL_ERROR", failure.getMetadata().get("toolResultCode"));
		assertEquals("TOOL_NOT_FOUND", missing.getMetadata().get("toolResultCode"));
	}

	@Test
	void shouldRejectInvalidParameters() {
		ToolExecutor executor = executor(invocation -> ToolResult.success("READY", List.of()));
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId("execution-1");
		context.setParameters(Map.of("tool", Map.of("provider", "filesystem",
			"name", "echo", "arguments", "invalid", "timeout", "one second")));

		ExecutionResult result = executor.execute(context);

		assertFalse(result.isSuccess());
		assertTrue(result.getMessage().startsWith("Invalid tool invocation:"));
		assertEquals("INVALID_TOOL_INVOCATION", result.getMetadata().get("toolResultCode"));
	}

	@Test
	void shouldEnforceInvocationTimeout() {
		ToolExecutor executor = executor(invocation -> {
			try {
				Thread.sleep(Duration.ofSeconds(2));
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return ToolResult.success("late", List.of());
		});

		ExecutionResult result = executor.execute(context("echo", Map.of(), "PT0.02S"));

		assertFalse(result.isSuccess());
		assertEquals("TOOL_TIMEOUT", result.getMetadata().get("toolResultCode"));
	}

	private ToolExecutor executor(Function<ToolInvocation, ToolResult> handler) {
		ToolProvider provider = new ToolProvider() {
			@Override public String getId() { return "filesystem"; }
			@Override public List<ToolDefinition> getTools() {
				return List.of(new ToolDefinition("filesystem", "echo", "Echo", Map.of(),
					ToolAccess.READ_ONLY));
			}
			@Override public ToolResult invoke(ToolInvocation invocation) { return handler.apply(invocation); }
		};
		router = new ToolRouter(new ToolRegistry(List.of(provider)), new AllowRegisteredToolPolicy());
		return new ToolExecutor(router,
			new DefaultToolArtifactMapper(new ArtifactContentLimiter(10_000)));
	}

	private ExecutionContext context(String toolName, Map<String, Object> arguments, String timeout) {
		ExecutionContext context = new ExecutionContext();
		context.setExecutionId("execution-1");
		context.setParameters(Map.of("tool", Map.of("provider", "filesystem",
			"name", toolName, "arguments", arguments, "timeout", timeout)));
		return context;
	}
}
