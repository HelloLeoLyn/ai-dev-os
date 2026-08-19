package com.aidevos.orchestrator.metrics.agent;

import com.aidevos.orchestrator.modelregistry.ModelTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.executor.codex.CodexApprovalPolicy;
import com.aidevos.orchestrator.executor.codex.CodexCommandBuilder;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.executor.codex.CodexOutputSchemaProvider;
import com.aidevos.orchestrator.executor.codex.CodexProperties;
import com.aidevos.orchestrator.executor.codex.CodexResultMapper;
import com.aidevos.orchestrator.executor.codex.CoderPromptBuilder;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import com.aidevos.orchestrator.modelrouter.ResolvedModel;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import com.aidevos.orchestrator.testfixture.ExecutionWorkspaceTestFixture;
import com.aidevos.orchestrator.testfixture.ExecutionAwareWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

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
 * End-to-end agent observability: Task -> Agent Execution -> Metrics -> API.
 * A closed-loop coding task runs a fake codex CLI, then the metrics service
 * aggregates the execution and the change review outcome, and the metrics API
 * serves both agent and task statistics.
 */
class AgentMetricsIntegrationTest {

	@TempDir
	Path tempDir;

	private Path repo;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private ChangeService changeService;
	private AgentMetricsService agentMetricsService;
	private AgentManager agentManager;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private String workspaceId;

	@BeforeEach
	void setUp() throws Exception {
		repo = tempDir.resolve("repo");
		Files.createDirectories(repo);
		git(repo, "init", "-b", "main");
		git(repo, "config", "user.email", "test@example.com");
		git(repo, "config", "user.name", "Test");
		write(repo.resolve("a.txt"), "one\n");
		git(repo, "add", "a.txt");
		git(repo, "commit", "-m", "init");

		CommandExecutor commandExecutor = new CommandExecutor();
		WorkspaceService workspaceService = new WorkspaceService(new ExecutionAwareWorkspaceRepository(tempDir.resolve("execution-workspaces")),
			new ProcessGitCommandExecutor(commandExecutor));
		ExecutionWorkspaceService executionWorkspaceService = ExecutionWorkspaceTestFixture.service(workspaceService, tempDir);
		workspaceId = workspaceService.createWorkspace("project-x", repo.toString()).getWorkspaceId();

		CodexProperties codexProperties = new CodexProperties();
		codexProperties.setExecutable(executable("codex",
			"#!/usr/bin/env bash\nprintf 'change\\n' >> a.txt\necho 'codex executed'\nexit 0\n").toString());
		codexProperties.setApprovalPolicy(CodexApprovalPolicy.NEVER);
		codexProperties.setTimeout(Duration.ofMinutes(1));
		CodingWorkspaceProperties workspaceProperties = new CodingWorkspaceProperties();
		workspaceProperties.setAllowedRoots(List.of(tempDir.toString()));
		CodexOutputSchemaProvider schemaProvider = mock(CodexOutputSchemaProvider.class);
		when(schemaProvider.path()).thenReturn(tempDir.resolve("schema.json").toString());
		ArtifactContentLimiter limiter = new ArtifactContentLimiter(100_000);
		CodexExecutor codexExecutor = new CodexExecutor(commandExecutor,
			com.aidevos.orchestrator.execution.workspace.TestWorkspaceResolvers.create(workspaceProperties, new GitExecutor(commandExecutor)),
			new GitInspector(new GitExecutor(commandExecutor)),
			new CodexResultMapper(new ObjectMapper()), mock(CodingApprovalService.class), limiter,
			codexProperties, new CodexCommandBuilder(codexProperties, new CoderPromptBuilder(),
				schemaProvider), new UntrackedArtifactCollector(limiter, 100_000),
			null, ModelTestSupport.defaultResolver());

		PlannerService plannerService = mock(PlannerService.class);
		approvalService = mock(PlanApprovalService.class);
		planRunRepository = mock(PlanRunRepository.class);
		ModelRouterService modelRouterService = mock(ModelRouterService.class);

		agentManager = new AgentManager();
		agentManager.register(agent("planner", "mock", List.of("planning", "analysis")));
		agentManager.register(agent("coder", "codex", List.of("coding", "git")));
		agentManager.register(agent("tester", "mock", List.of("testing", "browser")));
		agentManager.register(agent("browser-agent", "mock", List.of("browser")));
		ExecutorManager executorManager = new ExecutorManager(agentManager,
			new ExecutorRegistry(List.of(new MockAgentExecutor(), codexExecutor)));

		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		MemoryService memoryService = new MemoryService(new InMemoryMemoryRepository());
		InMemoryExecutionRecordRepository recordRepository = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			recordRepository, auditService);

		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		TestAgentService testAgentService = new TestAgentService(new FakeRunner(),
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			workspaceService, changeService, null, null, null, executionWorkspaceService);
		taskCenterService.setAgentCoordinatorService(coordinator);
		RepairCoordinator repairCoordinator = new RepairCoordinator(taskCenterService,
			testAgentService, plannerService, codexExecutor, workspaceService, memoryService,
			auditService, changeService);
		agentMetricsService = new AgentMetricsService(executionRecordManager, agentManager,
			auditService, repairCoordinator, changeService, taskCenterService);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
	}

	@Test
	void shouldExposeAgentAndTaskMetricsAfterClosedLoop() throws Exception {
		TaskRecord task = createApprovedTask();
		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);
		assertEquals(TaskStatus.COMPLETED, executed.getStatus());

		// Agent metrics: coder executed successfully with a measurable duration.
		AgentMetrics coder = agentMetricsService.listAgentMetrics().stream()
			.filter(metrics -> "coder".equals(metrics.agentId()))
			.findFirst().orElseThrow();
		assertTrue(coder.taskCount() >= 1);
		assertTrue(coder.successCount() >= 1);
		assertEquals(0, coder.failedCount());
		assertEquals(0, coder.repairCount());
		assertNotNull(coder.lastExecutedAt());

		// Task metrics: executions + the change set from the coding step.
		TaskExecutionMetrics taskMetrics = agentMetricsService.getTaskMetrics(task.getTaskId());
		assertEquals(TaskStatus.COMPLETED.name(), taskMetrics.taskStatus());
		assertTrue(taskMetrics.executionCount() >= 1);
		assertTrue(taskMetrics.successCount() >= 1);
		assertEquals(1, taskMetrics.changeCount());
		assertEquals(0, taskMetrics.approvedChanges());

		// Review the change: pass rate becomes 1.0.
		ChangeSet change = changeService.getChangesByTask(task.getTaskId()).get(0);
		changeService.startReview(change.getChangeId());
		changeService.approve(change.getChangeId(), "user-1");
		assertEquals(ChangeStatus.APPROVED, changeService.getChange(change.getChangeId())
			.orElseThrow().getStatus());
		TaskExecutionMetrics reviewed = agentMetricsService.getTaskMetrics(task.getTaskId());
		assertEquals(1, reviewed.approvedChanges());
		assertEquals(1.0, reviewed.reviewPassRate());

		// API: standalone MockMvc against the real controller + service.
		MockMvc mockMvc = standaloneSetup(new AgentMetricsController(agentMetricsService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
		mockMvc.perform(get("/api/metrics/agents"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.agentId == 'coder')].successCount")
				.value(org.hamcrest.Matchers.hasItem(1)));
		mockMvc.perform(get("/api/metrics/agents/coder"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.metrics.agentId").value("coder"));
		mockMvc.perform(get("/api/metrics/tasks/" + task.getTaskId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskStatus").value("COMPLETED"))
			.andExpect(jsonPath("$.reviewPassRate").value(1.0));
	}

	private TaskRecord createApprovedTask() {
		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.parse("2026-08-01T00:00:00Z"));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", plan,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);

		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"Implement login", "Append a line to a.txt", "Append a line to a.txt", "hermes",
			"project-x", workspaceId));
		TaskRecord approved = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.APPROVED, approved.getStatus());
		return approved;
	}

	private AgentDefinition agent(String name, String executor, List<String> capabilities) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		definition.setVersion("1.0.0");
		definition.setExecutor(executor);
		definition.setCapabilities(capabilities);
		return definition;
	}

	private Path executable(String name, String script) throws Exception {
		Path file = tempDir.resolve(name);
		Files.writeString(file, script, StandardCharsets.UTF_8);
		Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
		return file;
	}

	private void write(Path file, String content) throws Exception {
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private void git(Path directory, String... args) throws Exception {
		String[] command = new String[args.length + 1];
		command[0] = "git";
		System.arraycopy(args, 0, command, 1, args.length);
		Process process = new ProcessBuilder(command)
			.directory(directory.toFile())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = process.waitFor();
		assertEquals(0, exitCode, "git " + String.join(" ", args) + " failed: " + output);
	}

	private static final class FakeRunner implements TestCommandRunner {

		@Override
		public TestCommandResult run(String command, String workdir) {
			return new TestCommandResult(0, "BUILD SUCCESS", "");
		}
	}

	private static final class FakeBrowserExecutor implements BrowserTestExecutor {

		@Override
		public BrowserTestResult execute(String testId, String command) {
			return BrowserTestResult.success("ok", null);
		}
	}
}
