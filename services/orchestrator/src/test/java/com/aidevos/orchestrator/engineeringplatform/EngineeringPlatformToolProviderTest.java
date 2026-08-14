package com.aidevos.orchestrator.engineeringplatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.tool.ToolResult;
import com.aidevos.orchestrator.tool.ToolRouter;
import com.aidevos.orchestrator.tool.policy.AllowRegisteredToolPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringPlatformToolProviderTest {

	@TempDir Path workspace;
	private RecordingAdapter adapter;
	private EngineeringPlatformToolProvider provider;

	@BeforeEach void setUp() throws Exception {
		Files.writeString(workspace.resolve("project.yaml"), "project: trial");
		adapter = new RecordingAdapter();
		EngineeringPlatformProperties properties = new EngineeringPlatformProperties();
		properties.setPlatformRoot(workspace.resolve("ep-root").toString());
		provider = new EngineeringPlatformToolProvider(adapter, properties);
	}

	@Test void allowsGenerateOnlyInsideWorkspace() {
		ToolResult allowed = provider.invoke(invocation("generate",
			Map.of("projectYaml", "project.yaml", "outputPath", "generated")));
		ToolResult denied = provider.invoke(invocation("generate",
			Map.of("projectYaml", "project.yaml", "outputPath", "../outside")));
		assertTrue(allowed.success());
		assertEquals(workspace.resolve("generated").toAbsolutePath().normalize(), adapter.output);
		assertFalse(denied.success());
		assertEquals("EP_SCOPE_DENIED", denied.code());
		ToolResult platformRootDenied = provider.invoke(invocation("generate",
			Map.of("projectYaml", "project.yaml", "outputPath", "ep-root/generated")));
		assertEquals("EP_SCOPE_DENIED", platformRootDenied.code());
	}

	@Test void readOnlyAndApprovalPolicyRemainEnforcedByToolRouter() {
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			new AllowRegisteredToolPolicy());
		try {
			ToolInvocation generate = invocation("generate",
				Map.of("projectYaml", "project.yaml", "outputPath", "generated"));
			assertEquals("TOOL_DENIED", router.invoke(generate, ExecutionMode.READ_ONLY).code());
			assertEquals("TOOL_APPROVAL_REQUIRED",
				router.invoke(generate, ExecutionMode.READ_WRITE).code());
			assertNull(adapter.output);
		}
		finally { router.close(); }
	}

	@Test void doesNotExposeArbitraryOperationOrRuntimeOverrides() {
		assertFalse(provider.getTools().stream().anyMatch(tool -> tool.name().equals("conformance")));
		assertFalse(provider.getTools().stream().anyMatch(tool ->
			tool.inputSchema().containsKey("executable") || tool.inputSchema().containsKey("platformRoot")
				|| tool.inputSchema().containsKey("subcommand")));
		assertEquals("EP_OPERATION_NOT_ALLOWED",
			provider.invoke(invocation("shell", Map.of("projectYaml", "project.yaml"))).code());
	}

	private ToolInvocation invocation(String operation, Map<String, Object> arguments) {
		return new ToolInvocation("execution-1", "invocation-1", null, workspace.toString(),
			EngineeringPlatformToolProvider.ID, operation, arguments, Duration.ofSeconds(1));
	}

	private static class RecordingAdapter implements EngineeringPlatformAdapter {
		private Path output;
		@Override public EngineeringPlatformResult validate(Path manifest, Path workspace, Duration timeout) {
			return success(EngineeringPlatformOperation.VALIDATE);
		}
		@Override public EngineeringPlatformResult resolve(Path manifest, Path workspace, Duration timeout) {
			return success(EngineeringPlatformOperation.RESOLVE);
		}
		@Override public EngineeringPlatformResult generate(Path manifest, Path output, Path workspace, Duration timeout) {
			this.output = output; return success(EngineeringPlatformOperation.GENERATE);
		}
		@Override public EngineeringPlatformResult conformance(Path manifest, Path projectDirectory, Path workspace, Duration timeout) {
			return success(EngineeringPlatformOperation.CONFORMANCE);
		}
		private EngineeringPlatformResult success(EngineeringPlatformOperation operation) {
			return new EngineeringPlatformResult(operation, 0, EngineeringPlatformStatus.SUCCESS,
				"ok", "", 1, Map.of("operation", operation.name(), "exitCode", 0, "durationMs", 1));
		}
	}
}
