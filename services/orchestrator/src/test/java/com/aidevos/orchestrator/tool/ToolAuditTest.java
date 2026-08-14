package com.aidevos.orchestrator.tool;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.tool.approval.ToolApprovalService;
import com.aidevos.orchestrator.tool.approval.ToolApprovalStore;
import com.aidevos.orchestrator.tool.policy.ToolPolicyDecision;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ToolAuditTest {
	@Test
	void recordsToolLifecycleWithoutArguments() throws Exception {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		FakeToolProvider provider = new FakeToolProvider("fake", "echo",
			invocation -> ToolResult.success("READY", List.of()));
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			(definition, invocation) -> ToolPolicyDecision.allow(),
			new ToolApprovalService(new ToolApprovalStore(), new ObjectMapper()),
			java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), audit);
		try {
			ToolResult result = router.invoke(new ToolInvocation("execution-1", "invocation-1",
				"fake", "echo", Map.of("token", "secret-value", "credential", "hidden"),
				Duration.ofSeconds(1)));

			assertTrue(result.success());
			assertEquals(List.of(EventType.TOOL_INVOCATION_CREATED, EventType.TOOL_STARTED,
				EventType.TOOL_COMPLETED), types(events));
			String json = new ObjectMapper().writeValueAsString(events.query(EventQuery.all()));
			assertFalse(json.contains("secret-value"));
			assertFalse(json.contains("hidden"));
		} finally {
			router.close();
		}
	}

	@Test
	void auditFailureDoesNotAffectToolResult() {
		AuditService audit = new AuditService(new AuditRepository() {
			public EventRecord append(EventRecord event) { throw new IllegalStateException("down"); }
			public EventRecord get(String id) { return null; }
			public List<EventRecord> query(EventQuery query) { return List.of(); }
		});
		FakeToolProvider provider = new FakeToolProvider("fake", "echo",
			invocation -> ToolResult.success("READY", List.of()));
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			(definition, invocation) -> ToolPolicyDecision.allow(),
			new ToolApprovalService(new ToolApprovalStore(), new ObjectMapper()),
			java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), audit);
		try {
			assertTrue(router.invoke(new ToolInvocation("execution-1", "invocation-1", "fake",
				"echo", Map.of(), Duration.ofSeconds(1))).success());
		} finally {
			router.close();
		}
	}

	@Test
	void recordsOnlyControlledEngineeringPlatformResultMetadata() {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		FakeToolProvider provider = new FakeToolProvider("engineering-platform", "validate",
			invocation -> new ToolResult(null, null, true, "EP_SUCCESS", "ok", "ok", List.of(),
				Map.of("operation", "VALIDATE", "exitCode", 0, "projectYaml", "project.yaml",
					"durationMs", 12, "credential", "must-not-be-audited")));
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			(definition, invocation) -> ToolPolicyDecision.allow(),
			new ToolApprovalService(new ToolApprovalStore(), new ObjectMapper()),
			java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), audit);
		try {
			router.invoke(new ToolInvocation("execution-1", "ep-invocation", "engineering-platform",
				"validate", Map.of(), Duration.ofSeconds(1)));
			EventRecord completed = events.query(EventQuery.all()).stream()
				.filter(event -> event.type() == EventType.TOOL_COMPLETED).findFirst().orElseThrow();
			assertEquals("VALIDATE", completed.metadata().get("operation"));
			assertEquals(0, completed.metadata().get("exitCode"));
			assertEquals("project.yaml", completed.metadata().get("projectYaml"));
			assertFalse(completed.metadata().containsKey("credential"));
		}
		finally { router.close(); }
	}

	private List<EventType> types(InMemoryAuditRepository events) {
		return events.query(EventQuery.all()).stream().map(EventRecord::type).toList();
	}
}
