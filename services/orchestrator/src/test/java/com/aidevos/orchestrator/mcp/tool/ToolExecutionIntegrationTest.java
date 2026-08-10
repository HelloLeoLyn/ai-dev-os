package com.aidevos.orchestrator.mcp.tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.AgentExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNode;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 16-C: Task -> Agent Node -> McpToolRouter -> Git tool -> Audit. Runs
 * a one-node CODEX graph whose agent calls the git tool through the router
 * against a real temporary git repository.
 */
class ToolExecutionIntegrationTest {

	@TempDir
	Path tempDir;

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private McpToolRouter router;
	private TaskCenterService taskCenterService;
	private String taskId;

	@BeforeEach
	void setUp() throws Exception {
		git(tempDir, "init", "-b", "main");
		git(tempDir, "config", "user.email", "test@example.com");
		git(tempDir, "config", "user.name", "Test");
		Files.writeString(tempDir.resolve("a.txt"), "one", StandardCharsets.UTF_8);
		git(tempDir, "add", "a.txt");
		git(tempDir, "commit", "-m", "init");
		Files.writeString(tempDir.resolve("b.txt"), "new", StandardCharsets.UTF_8);

		PlannerService plannerService = mock(PlannerService.class);
		PlanApprovalService approvalService = mock(PlanApprovalService.class);
		PlanRunRepository planRunRepository = mock(PlanRunRepository.class);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(), null,
					java.time.Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1",
			new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(), null,
				java.time.Instant.parse("2026-08-01T00:00:00Z")), "hash",
			java.time.Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", java.time.Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);

		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		ToolRegistry registry = new InMemoryToolRegistry(List.of(
			new GitToolExecutor(new ProcessGitCommandExecutor(new CommandExecutor())),
			new FilesystemToolExecutor(),
			new BrowserToolExecutor(mock(com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor.class)),
			new DockerToolExecutor(),
			new TerminalToolExecutor()));
		router = new McpToolRouter(registry, auditService);
	}

	@Test
	void shouldExecuteGitToolThroughRouterInsideAgentNode() {
		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"检查仓库状态", "检查工作区 git 状态", "检查工作区 git 状态", "hermes", "project-x", null));
		task = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		taskId = task.getTaskId();
		assertEquals(TaskStatus.APPROVED, task.getStatus());

		ExecutionNode node = new ExecutionNode("CODEX_IMPLEMENTATION", AgentType.CODEX);
		ExecutionGraph graph = new ExecutionGraph("graph-1", taskId, List.of(node), null, null, 1);
		AgentExecutor codex = new GitCallingAgent(router, tempDir.toString());
		ExecutionGraphExecutor executor = new ExecutionGraphExecutor(List.of(codex),
			auditService, taskCenterService, router);

		AgentExecutionContext context = new AgentExecutionContext();
		context.setTaskId(taskId);
		context.setTask(task);
		context.setGraphId("graph-1");
		context.setNodeId(node.getNodeId());
		context.setAgentType(AgentType.CODEX);

		ExecutionGraph result = executor.execute(graph, context);

		assertEquals(ExecutionNodeStatus.COMPLETED,
			result.getNode("CODEX_IMPLEMENTATION").getStatus());
		assertTrue(result.getNode("CODEX_IMPLEMENTATION").getResult().contains("branch=main"));
		assertTrue(events(EventType.TOOL_SELECTED).stream().anyMatch(event ->
			taskId.equals(event.taskId())));
		assertTrue(events(EventType.TOOL_STARTED).stream().anyMatch(event ->
			"git".equals(event.metadata().get("toolId")) && taskId.equals(event.taskId())));
		assertTrue(events(EventType.TOOL_COMPLETED).stream().anyMatch(event ->
			"git".equals(event.metadata().get("toolId")) && taskId.equals(event.taskId())));
		EventRecord completed = events(EventType.TOOL_COMPLETED).stream()
			.filter(event -> "git".equals(event.metadata().get("toolId")))
			.findFirst().orElseThrow();
		assertTrue((Long) completed.metadata().get("duration") >= 0);
	}

	private List<EventRecord> events(EventType type) {
		return auditRepository.query(EventQuery.all()).stream()
			.filter(event -> event.type() == type).toList();
	}

	private void git(Path dir, String... args) throws Exception {
		List<String> command = new java.util.ArrayList<>(List.of("git"));
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).directory(dir.toFile())
			.redirectErrorStream(true).start();
		process.getInputStream().readAllBytes();
		assertEquals(0, process.waitFor(), "git " + String.join(" ", args));
	}

	/** CODEX agent that calls the git status tool through the MCP router. */
	private static final class GitCallingAgent implements AgentExecutor {

		private final McpToolRouter router;
		private final String repoPath;

		private GitCallingAgent(McpToolRouter router, String repoPath) {
			this.router = router;
			this.repoPath = repoPath;
		}

		@Override
		public AgentType type() {
			return AgentType.CODEX;
		}

		@Override
		public AgentExecutionResult execute(AgentExecutionContext context) {
			ToolExecutionResult toolResult = router.route(new ToolExecutionRequest("git",
				AgentType.CODEX, context.getTaskId(),
				Map.of("path", repoPath, "operation", "status")));
			if (toolResult.success()) {
				return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
					toolResult.output(), null);
			}
			return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null,
				toolResult.error());
		}
	}
}
