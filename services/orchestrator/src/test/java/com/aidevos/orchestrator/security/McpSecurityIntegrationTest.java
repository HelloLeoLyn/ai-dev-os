package com.aidevos.orchestrator.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.mcp.tool.GitToolExecutor;
import com.aidevos.orchestrator.mcp.tool.InMemoryToolRegistry;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.TerminalToolExecutor;
import com.aidevos.orchestrator.mcp.tool.ToolExecutionRequest;
import com.aidevos.orchestrator.mcp.tool.ToolExecutionResult;
import com.aidevos.orchestrator.mcp.tool.ToolRegistry;
import com.aidevos.orchestrator.security.sandbox.SandboxManager;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 17-A: Agent -> Tool Router -> Security policy -> Allow / Deny. The
 * sandbox is created per task and the policy decides whether the tool
 * executes; approvals unlock require-approval permissions.
 */
class McpSecurityIntegrationTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private SandboxManager sandboxManager;
	private ApprovalService approvalService;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		sandboxManager = new SandboxManager(auditService);
		approvalService = new ApprovalService(auditService);
	}

	@Test
	void shouldAllowCodexGitAccessThroughSecurityGate() {
		McpToolRouter router = router(new InMemorySecurityPolicyRegistry());

		ToolExecutionResult result = router.route(new ToolExecutionRequest("git",
			AgentType.CODEX, "task-1", Map.of("path", "/repo", "operation", "status")));

		assertTrue(result.success());
		assertTrue(events(EventType.SECURITY_CHECK).stream()
			.anyMatch(event -> "task-1".equals(event.taskId())));
		assertTrue(events(EventType.PERMISSION_GRANTED).stream()
			.anyMatch(event -> "task-1".equals(event.taskId())));
		assertTrue(events(EventType.TOOL_COMPLETED).stream()
			.anyMatch(event -> "git".equals(event.metadata().get("toolId"))));
		assertTrue(sandboxManager.getSandbox("task-1").isPresent());
	}

	@Test
	void shouldDenyOpenClawGitAccess() {
		McpToolRouter router = router(new InMemorySecurityPolicyRegistry());

		ToolExecutionResult result = router.route(new ToolExecutionRequest("git",
			AgentType.OPENCLAW, "task-2", Map.of("path", "/repo", "operation", "status")));

		assertFalse(result.success());
		assertTrue(events(EventType.PERMISSION_DENIED).stream()
			.anyMatch(event -> "task-2".equals(event.taskId())));
		assertTrue(events(EventType.TOOL_DENIED).stream()
			.anyMatch(event -> "git".equals(event.metadata().get("toolId"))));
		assertFalse(events(EventType.TOOL_STARTED).stream()
			.anyMatch(event -> "task-2".equals(event.taskId())));
	}

	@Test
	void shouldDenyToolOutsideSandboxScope() {
		McpToolRouter router = router(new InMemorySecurityPolicyRegistry());

		ToolExecutionResult result = router.route(new ToolExecutionRequest("terminal",
			AgentType.CODEX, "task-3", Map.of("command", "rm -rf", "permission", "DANGEROUS")));

		assertFalse(result.success());
		assertTrue(result.error().contains("sandbox"));
		assertTrue(events(EventType.TOOL_DENIED).stream()
			.anyMatch(event -> "terminal".equals(event.metadata().get("toolId"))));
	}

	@Test
	void shouldRequireApprovalForDangerousPermission() {
		McpToolRouter router = router(policyRegistryWithApproval());

		ToolExecutionResult denied = router.route(new ToolExecutionRequest("terminal",
			AgentType.TEST_AGENT, "task-4", Map.of("command", "ls", "permission", "DANGEROUS")));
		assertFalse(denied.success());
		assertTrue(denied.error().contains("human approval"));

		ApprovalRequest approval = approvalService.request("task-4", AgentType.TEST_AGENT,
			SecurityPermission.EXECUTE_COMMAND, "run terminal");
		approvalService.approve(approval.getRequestId());

		ToolExecutionResult allowed = router.route(new ToolExecutionRequest("terminal",
			AgentType.TEST_AGENT, "task-4", Map.of("command", "ls", "permission", "DANGEROUS")));
		assertFalse(allowed.success());
		assertTrue(events(EventType.PERMISSION_GRANTED).stream()
			.anyMatch(event -> "task-4".equals(event.taskId())));
	}

	private McpToolRouter router(SecurityPolicyRegistry policyRegistry) {
		GitCommandExecutor git = mock(GitCommandExecutor.class);
		when(git.status("/repo")).thenReturn(new GitStatus("main", 1, 0, 0));
		ToolRegistry registry = new InMemoryToolRegistry(List.of(
			new GitToolExecutor(git), new TerminalToolExecutor()));
		return new McpToolRouter(registry, auditService, policyRegistry, sandboxManager,
			approvalService);
	}

	private SecurityPolicyRegistry policyRegistryWithApproval() {
		SecurityPolicyRegistry registry = new MapBackedPolicyRegistry();
		registry.register(new SecurityPolicy("test-agent-policy", AgentType.TEST_AGENT,
			Set.of(SecurityPermission.READ_FILE, SecurityPermission.EXECUTE_COMMAND),
			Set.of(), Set.of(SecurityPermission.EXECUTE_COMMAND)));
		return registry;
	}

	private java.util.List<EventRecord> events(EventType type) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type).toList();
	}

	private static final class MapBackedPolicyRegistry implements SecurityPolicyRegistry {

		private final Map<AgentType, SecurityPolicy> policies = new LinkedHashMap<>();

		@Override
		public void register(SecurityPolicy policy) {
			policies.put(policy.agentType(), policy);
		}

		@Override
		public SecurityPolicy getPolicy(AgentType agentType) {
			return policies.get(agentType);
		}

		@Override
		public boolean checkPermission(AgentType agentType, SecurityPermission permission) {
			SecurityPolicy policy = getPolicy(agentType);
			return policy != null && policy.allows(permission);
		}

		@Override
		public List<SecurityPolicy> listPolicies() {
			return List.copyOf(policies.values());
		}
	}
}
