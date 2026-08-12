package com.aidevos.orchestrator.mcp.tool;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 16-C: McpToolRouter normal execution, permission denial and failure
 * handling with audit events.
 */
class McpToolRouterTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private McpToolRouter router;

	@BeforeEach
	void setUp() {
		GitCommandExecutor git = mock(GitCommandExecutor.class);
		when(git.status("/repo")).thenReturn(new GitStatus("main", 3, 1, 0));
		ToolRegistry registry = new InMemoryToolRegistry(List.of(
			new GitToolExecutor(git),
			new FilesystemToolExecutor(),
			new TerminalToolExecutor()));
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		router = new McpToolRouter(registry, auditService);
	}

	@Test
	void shouldExecuteToolAndAuditStartAndCompletion() {
		ToolExecutionResult result = router.route(new ToolExecutionRequest("git",
			AgentType.CODEX, "task-1", Map.of("path", "/repo", "operation", "status")));

		assertTrue(result.success());
		assertTrue(result.output().contains("branch=main"));
		assertTrue(result.duration() >= 0);
		assertTrue(events(EventType.TOOL_STARTED).stream()
			.anyMatch(event -> "task-1".equals(event.taskId())));
		assertTrue(events(EventType.TOOL_COMPLETED).stream()
			.anyMatch(event -> "git".equals(event.metadata().get("toolId"))));
	}

	@Test
	void shouldDenyRequestWhenPermissionNotGranted() {
		ToolExecutionResult result = router.route(new ToolExecutionRequest("git",
			AgentType.CODEX, "task-2", Map.of("path", "/repo", "operation", "status",
				"permission", "EXECUTE")));

		assertFalse(result.success());
		assertTrue(result.error().contains("Permission denied"));
		assertTrue(events(EventType.TOOL_DENIED).stream()
			.anyMatch(event -> "git".equals(event.metadata().get("toolId"))
				&& "task-2".equals(event.taskId())));
	}

	@Test
	void shouldDenyDangerousToolWithoutExplicitPermission() {
		ToolExecutionResult result = router.route(new ToolExecutionRequest("terminal",
			AgentType.TEST_AGENT, "task-3", Map.of("command", "ls")));

		assertFalse(result.success());
		assertTrue(events(EventType.TOOL_DENIED).stream()
			.anyMatch(event -> "terminal".equals(event.metadata().get("toolId"))));
	}

	@Test
	void shouldReportFailureWhenExecutorThrows() {
		ToolRegistry registry = new InMemoryToolRegistry(List.of(new ThrowingExecutor()));
		McpToolRouter failingRouter = new McpToolRouter(registry, auditService);

		ToolExecutionResult result = failingRouter.route(new ToolExecutionRequest("boom",
			AgentType.CODEX, "task-4", Map.of()));

		assertFalse(result.success());
		assertTrue(result.error().contains("boom"));
		assertTrue(events(EventType.TOOL_FAILED).stream()
			.anyMatch(event -> "boom".equals(event.metadata().get("toolId"))));
	}

	@Test
	void shouldFailWhenToolNotFound() {
		ToolExecutionResult result = router.route(new ToolExecutionRequest("missing",
			AgentType.CODEX, "task-5", Map.of()));

		assertFalse(result.success());
		assertTrue(result.error().contains("Tool not found"));
	}

	@Test
	void readOnlyTaskAllowsReadsAndRejectsFilesystemWrites() throws Exception {
		java.nio.file.Path file = java.nio.file.Files.createTempFile("readonly-tool", ".txt");
		java.nio.file.Files.writeString(file, "content");
		ToolExecutionResult read = router.route(new ToolExecutionRequest("filesystem",
			AgentType.CODEX, "task-read", Map.of("path", file.toString(), "operation", "read",
				"permission", "READ", "executionMode", "READ_ONLY")));
		ToolExecutionResult write = router.route(new ToolExecutionRequest("filesystem",
			AgentType.CODEX, "task-write", Map.of("path", file.toString(), "operation", "write",
				"permission", "WRITE", "executionMode", "READ_ONLY")));

		assertTrue(read.success());
		assertFalse(write.success());
		assertTrue(write.error().contains("READ_ONLY"));
	}

	@Test
	void readOnlyTaskRejectsGitCommitAndPushButAllowsStatus() {
		ToolExecutionResult status = router.route(new ToolExecutionRequest("git",
			AgentType.CODEX, "task-status", Map.of("path", "/repo", "operation", "status",
				"permission", "READ", "executionMode", "READ_ONLY")));
		ToolExecutionResult commit = router.route(new ToolExecutionRequest("git",
			AgentType.CODEX, "task-commit", Map.of("path", "/repo", "operation", "commit",
				"permission", "WRITE", "executionMode", "READ_ONLY")));
		ToolExecutionResult push = router.route(new ToolExecutionRequest("git",
			AgentType.CODEX, "task-push", Map.of("path", "/repo", "operation", "push",
				"permission", "WRITE", "executionMode", "READ_ONLY")));

		assertTrue(status.success());
		assertFalse(commit.success());
		assertFalse(push.success());
	}

	private List<EventRecord> events(EventType type) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type).toList();
	}

	private static final class ThrowingExecutor implements McpToolExecutor {

		@Override
		public ToolDefinition definition() {
			return new ToolDefinition("boom", "Boom", ToolType.TERMINAL, "always throws",
				Map.of(), java.util.Set.of(ToolPermission.EXECUTE));
		}

		@Override
		public ToolExecutionResult execute(ToolExecutionRequest request) {
			throw new IllegalStateException("boom");
		}
	}
}
