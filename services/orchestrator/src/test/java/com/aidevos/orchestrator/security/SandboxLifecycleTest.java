package com.aidevos.orchestrator.security;

import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.security.sandbox.SandboxContext;
import com.aidevos.orchestrator.security.sandbox.SandboxManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-A: per-task sandbox creation, isolation and destruction.
 */
class SandboxLifecycleTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private SandboxManager manager;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		manager = new SandboxManager(auditService);
	}

	@Test
	void shouldCreateOneSandboxPerTask() {
		SandboxContext first = manager.createSandbox("task-1", "ws-1", AgentType.CODEX,
			List.of(), Set.of(SecurityPermission.GIT_WRITE));
		SandboxContext second = manager.createSandbox("task-1", "ws-1", AgentType.CODEX,
			List.of(), Set.of(SecurityPermission.GIT_WRITE));

		assertEquals(first.sandboxId(), second.sandboxId());
		assertEquals("task-1", first.taskId());
		assertTrue(events(EventType.SANDBOX_CREATED).stream()
			.anyMatch(event -> "task-1".equals(event.taskId())));
	}

	@Test
	void shouldIsolateSandboxesAcrossTasks() {
		SandboxContext task1 = manager.createSandbox("task-1", "ws-1", AgentType.CODEX,
			List.of(), Set.of());
		SandboxContext task2 = manager.createSandbox("task-2", "ws-2", AgentType.TEST_AGENT,
			List.of(), Set.of());

		assertNotEquals(task1.sandboxId(), task2.sandboxId());
		assertNotEquals(task1.taskId(), task2.taskId());
	}

	@Test
	void shouldDestroySandbox() {
		manager.createSandbox("task-1", "ws-1", AgentType.CODEX, List.of(), Set.of());
		assertTrue(manager.getSandbox("task-1").isPresent());

		manager.destroySandbox("task-1");

		assertTrue(manager.getSandbox("task-1").isEmpty());
		assertTrue(events(EventType.SANDBOX_DESTROYED).stream()
			.anyMatch(event -> "task-1".equals(event.taskId())));
	}

	@Test
	void shouldEnforceToolScopeWithinSandbox() {
		com.aidevos.orchestrator.mcp.tool.ToolDefinition git = new com.aidevos.orchestrator.mcp.tool.ToolDefinition(
			"git", "Git", com.aidevos.orchestrator.mcp.tool.ToolType.GIT, "git tool",
			java.util.Map.of(), Set.of(com.aidevos.orchestrator.mcp.tool.ToolPermission.READ));
		SandboxContext sandbox = manager.createSandbox("task-1", "ws-1", AgentType.CODEX,
			List.of(git), Set.of());

		assertTrue(sandbox.allowsTool("git"));
		assertTrue(!sandbox.allowsTool("terminal"));
	}

	private java.util.List<EventRecord> events(EventType type) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type).toList();
	}
}
