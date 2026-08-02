package com.aidevos.orchestrator.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.tool.policy.ToolPolicyDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRouterTest {

	@Test
	void shouldRouteAndPreserveInvocationIdentity() {
		FakeToolProvider provider = new FakeToolProvider("fake", "echo",
			invocation -> ToolResult.success("READY", List.of(ToolContent.text("result.txt", "READY"))));
		ToolRouter router = router(provider);
		ToolInvocation invocation = invocation(Duration.ofSeconds(1));

		ToolResult result = router.invoke(invocation);

		assertTrue(result.success());
		assertEquals("execution-1", result.executionId());
		assertEquals("invocation-1", result.invocationId());
		assertEquals("READY", result.output());
		router.close();
	}

	@Test
	void shouldStandardizeUnknownToolAndDeniedCall() {
		FakeToolProvider provider = new FakeToolProvider("fake", "echo",
			invocation -> ToolResult.success("READY", List.of()));
		ToolRegistry registry = new ToolRegistry(List.of(provider));
		ToolRouter deniedRouter = new ToolRouter(registry,
			(definition, invocation) -> ToolPolicyDecision.deny("not allowed"));

		ToolResult denied = deniedRouter.invoke(invocation(Duration.ofSeconds(1)));
		ToolResult missing = deniedRouter.invoke(new ToolInvocation("execution-1", "invocation-2",
			"fake", "missing", Map.of(), Duration.ofSeconds(1)));

		assertFalse(denied.success());
		assertEquals("TOOL_DENIED", denied.code());
		assertEquals("not allowed", denied.message());
		assertEquals("TOOL_NOT_FOUND", missing.code());
		deniedRouter.close();
	}

	@Test
	void shouldStandardizeTimeoutAndProviderFailure() {
		FakeToolProvider slow = new FakeToolProvider("slow", "wait", invocation -> {
			try {
				Thread.sleep(5_000);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return ToolResult.success("late", List.of());
		});
		ToolRouter slowRouter = router(slow);
		ToolResult timeout = slowRouter.invoke(new ToolInvocation("execution-1", "invocation-1",
			"slow", "wait", Map.of(), Duration.ofMillis(25)));

		FakeToolProvider broken = new FakeToolProvider("broken", "fail", invocation -> {
			throw new IllegalStateException("provider unavailable");
		});
		ToolRouter brokenRouter = router(broken);
		ToolResult failure = brokenRouter.invoke(new ToolInvocation("execution-2", "invocation-2",
			"broken", "fail", Map.of(), Duration.ofSeconds(1)));

		assertEquals("TOOL_TIMEOUT", timeout.code());
		assertEquals("execution-1", timeout.executionId());
		assertEquals("TOOL_PROVIDER_ERROR", failure.code());
		assertEquals("provider unavailable", failure.message());
		slowRouter.close();
		brokenRouter.close();
	}

	private ToolRouter router(ToolProvider provider) {
		return new ToolRouter(new ToolRegistry(List.of(provider)),
			(definition, invocation) -> ToolPolicyDecision.allow());
	}

	private ToolInvocation invocation(Duration timeout) {
		return new ToolInvocation("execution-1", "invocation-1", "fake", "echo",
			Map.of("value", "READY"), timeout);
	}
}
