package com.aidevos.orchestrator.observability;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.controller.ObservabilityController;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcp.tool.BrowserToolExecutor;
import com.aidevos.orchestrator.mcp.tool.DockerToolExecutor;
import com.aidevos.orchestrator.mcp.tool.FilesystemToolExecutor;
import com.aidevos.orchestrator.mcp.tool.GitToolExecutor;
import com.aidevos.orchestrator.mcp.tool.InMemoryToolRegistry;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.TerminalToolExecutor;
import com.aidevos.orchestrator.mcp.tool.ToolExecutionRequest;
import com.aidevos.orchestrator.mcp.tool.ToolExecutionResult;
import com.aidevos.orchestrator.mcp.tool.ToolRegistry;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.metrics.tool.ToolMetricsService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.observability.usage.InMemoryUsageRepository;
import com.aidevos.orchestrator.observability.usage.UsageService;
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
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Phase 17-C: Task -> Graph -> Agent -> Tool -> Trace -> Observability API.
 * A one-node CODEX graph runs a real git tool against a temporary repository
 * while usage is recorded; the observability service then exposes the trace,
 * timeline, agent statistics, tool statistics and cost figures.
 */
class ObservabilityIntegrationTest {

	@TempDir
	Path tempDir;

	private AuditService auditService;
	private InMemoryAuditRepository auditRepository;
	private ExecutionTraceService traceService;
	private UsageService usageService;
	private ExecutionRecordManager executionRecordManager;
	private TaskCenterService taskCenterService;
	private ObservabilityService observabilityService;
	private ToolMetricsService toolMetricsService;
	private String taskId;

	@BeforeEach
	void setUp() throws Exception {
		git(tempDir, "init", "-b", "main");
		git(tempDir, "config", "user.email", "test@example.com");
		git(tempDir, "config", "user.name", "Test");
		Files.writeString(tempDir.resolve("a.txt"), "one", StandardCharsets.UTF_8);
		git(tempDir, "add", "a.txt");
		git(tempDir, "commit", "-m", "init");

		PlannerService plannerService = mock(PlannerService.class);
		PlanApprovalService approvalService = mock(PlanApprovalService.class);
		PlanRunRepository planRunRepository = mock(PlanRunRepository.class);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(), null,
					Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1",
			new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(), null,
				Instant.parse("2026-08-01T00:00:00Z")), "hash",
			Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);

		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		executionRecordManager = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository(), auditService);
		usageService = new UsageService(new InMemoryUsageRepository(), auditService);
		traceService = new ExecutionTraceService(new InMemoryTraceRepository(), auditService);
		toolMetricsService = new ToolMetricsService(auditService);

		ToolRegistry registry = new InMemoryToolRegistry(List.of(
			new GitToolExecutor(new ProcessGitCommandExecutor(new CommandExecutor())),
			new FilesystemToolExecutor(),
			new BrowserToolExecutor(mock(com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor.class)),
			new DockerToolExecutor(),
			new TerminalToolExecutor()));
		McpToolRouter router = new McpToolRouter(registry, auditService, null, null, null,
			traceService);

		AgentManager agentManager = mock(AgentManager.class);
		when(agentManager.getAllAgents()).thenReturn(List.of(agent("CODEX")));
		RepairCoordinator repairCoordinator = mock(RepairCoordinator.class);
		when(repairCoordinator.listRepairs()).thenReturn(List.of());
		ChangeService changeService = mock(ChangeService.class);
		when(changeService.listChanges()).thenReturn(List.of());
		AgentMetricsService agentMetricsService = new AgentMetricsService(executionRecordManager,
			agentManager, auditService, repairCoordinator, changeService, taskCenterService,
			usageService);

		TimelineService timelineService = new TimelineService(auditRepository, planRunRepository,
			mock(JobRepository.class), new InMemoryExecutionRecordRepository(),
			mock(TaskManager.class), taskCenterService);

		observabilityService = new ObservabilityService(taskCenterService,
			executionRecordManager, agentMetricsService, traceService, usageService,
			toolMetricsService, timelineService);
	}

	@Test
	void shouldExposeTaskTraceAgentToolAndUsageThroughApi() throws Exception {
		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"开发任务", "实现功能", "实现功能", "hermes", "project-1", null));
		task = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		taskId = task.getTaskId();
		assertEquals(TaskStatus.APPROVED, task.getStatus());

		ExecutionNode node = new ExecutionNode("CODEX_IMPLEMENTATION", AgentType.CODEX);
		ExecutionGraph graph = new ExecutionGraph("graph-1", taskId, List.of(node), null, null, 1);
		ExecutionGraphExecutor executor = new ExecutionGraphExecutor(List.of(
			new GitCallingAgent(new McpToolRouter(mockToolRegistry(),
				auditService, null, null, null, traceService), tempDir.toString())),
			auditService, taskCenterService,
			new McpToolRouter(mockToolRegistry(), auditService, null, null, null, traceService),
			traceService);

		AgentExecutionContext context = new AgentExecutionContext();
		context.setTaskId(taskId);
		context.setTask(task);
		context.setGraphId("graph-1");
		context.setNodeId(node.getNodeId());
		context.setAgentType(AgentType.CODEX);

		ExecutionGraph result = executor.execute(graph, context);
		assertEquals(ExecutionNodeStatus.COMPLETED,
			result.getNode("CODEX_IMPLEMENTATION").getStatus());

		executionRecordManager.save(record("exec-1", taskId, "CODEX", "SUCCESS"));
		usageService.recordUsage(taskId, "project-1", "CODEX", "codex-test", 1000, 500);

		TaskObservability observability = observabilityService.taskObservability(taskId);
		assertEquals(taskId, observability.taskId());
		assertEquals("CODING", observability.taskStatus());
		assertTrue(observability.traces().stream()
			.anyMatch(trace -> "CODEX_IMPLEMENTATION".equals(trace.getNodeId())
				&& trace.getStatus() == TraceStatus.SUCCESS));
		assertTrue(observability.toolTraces().stream()
			.anyMatch(trace -> "git".equals(trace.getToolId())));
		assertNotNull(observability.timeline());
		assertEquals(1, observability.usage().recordCount());
		assertEquals(1500, observability.usage().totalTokens());
		assertEquals(1, observability.agent().executionCount());

		AgentObservability agent = observabilityService.agentObservability("CODEX");
		assertEquals(1, agent.executionCount());
		assertEquals(1, agent.successCount());
		assertEquals(1500, agent.totalTokens());

		ProjectObservability project = observabilityService.projectObservability("project-1");
		assertEquals(1, project.taskCount());
		assertEquals(1500, project.totalTokens());
		assertTrue(project.estimatedCost() > 0);

		MockMvc mockMvc = standaloneSetup(new ObservabilityController(observabilityService,
			toolMetricsService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

		mockMvc.perform(get("/api/observability/tasks/{id}", taskId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value(taskId))
			.andExpect(jsonPath("$.usage.totalTokens").value(1500));

		mockMvc.perform(get("/api/observability/agents/{agentType}", "CODEX"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.executionCount").value(1));

		mockMvc.perform(get("/api/observability/projects/{projectId}", "project-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskCount").value(1));

		mockMvc.perform(get("/api/observability/tools"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.toolId == 'git')]").exists());
	}

	private ToolRegistry mockToolRegistry() {
		return new InMemoryToolRegistry(List.of(
			new GitToolExecutor(new ProcessGitCommandExecutor(new CommandExecutor())),
			new FilesystemToolExecutor(),
			new BrowserToolExecutor(mock(com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor.class)),
			new DockerToolExecutor(),
			new TerminalToolExecutor()));
	}

	private AgentDefinition agent(String name) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		return definition;
	}

	private ExecutionRecord record(String id, String taskId, String agent, String status) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setExecutionId(id);
		record.setTaskId(taskId);
		record.setAgentName(agent);
		record.setStatus(status);
		record.setStartedAt(Instant.parse("2026-08-01T00:00:00Z"));
		record.setCompletedAt(Instant.parse("2026-08-01T00:00:10Z"));
		return record;
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
