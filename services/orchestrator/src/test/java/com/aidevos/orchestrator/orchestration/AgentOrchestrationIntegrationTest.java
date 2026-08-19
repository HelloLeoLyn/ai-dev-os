package com.aidevos.orchestrator.orchestration;

import com.aidevos.orchestrator.modelregistry.ModelTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import com.aidevos.orchestrator.agent.InMemoryAgentRegistry;
import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.controller.AgentGraphController;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
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
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
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
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.timeline.TimelineEventDTO;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import com.aidevos.orchestrator.workspace.WorkspaceService;
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
 * Phase 16-A orchestration verification: Task -> ExecutionGraph -> Agents ->
 * ExecutionRecord -> ChangeSet -> Memory -> Audit -> Timeline. Uses real
 * services with in-memory repositories, a fake codex script and a fake test
 * runner (no real model, Codex CLI or Docker).
 */
class AgentOrchestrationIntegrationTest {

	@TempDir
	Path tempDir;

	private Path repo;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private ExecutionGraphExecutor graphExecutor;
	private TestAgentService testAgentService;
	private InMemoryAuditRepository auditRepository;
	private InMemoryMemoryRepository memoryRepository;
	private InMemoryExecutionRecordRepository recordRepository;
	private ChangeService changeService;
	private WorkspaceService workspaceService;
	private String workspaceId;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private ConfigurableRunner runner;

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
		workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(),
			new ProcessGitCommandExecutor(commandExecutor));
		workspaceId = workspaceService.createWorkspace("project-x", repo.toString())
			.getWorkspaceId();

		CodexProperties codexProperties = new CodexProperties();
		codexProperties.setExecutable(executable("codex",
			"#!/usr/bin/env bash\nprintf 'two\\n' >> a.txt\necho 'codex executed'\nexit 0\n").toString());
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

		AgentManager agentManager = new AgentManager();
		agentManager.register(agent("planner", "mock", List.of("planning", "analysis")));
		agentManager.register(agent("coder", "codex", List.of("coding", "git")));
		agentManager.register(agent("tester", "mock", List.of("testing", "browser")));
		agentManager.register(agent("browser-agent", "mock", List.of("browser")));
		ExecutorManager executorManager = new ExecutorManager(agentManager,
			new ExecutorRegistry(List.of(new MockAgentExecutor(), codexExecutor)));

		auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		memoryRepository = new InMemoryMemoryRepository();
		MemoryService memoryService = new MemoryService(memoryRepository);
		recordRepository = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			recordRepository, auditService);

		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		runner = new ConfigurableRunner();
		testAgentService = new TestAgentService(runner, new FakeBrowserExecutor(),
			taskCenterService, auditService, memoryService);
		RepairCoordinator repairCoordinator = new RepairCoordinator(taskCenterService,
			testAgentService, plannerService, codexExecutor, workspaceService, memoryService,
			auditService, changeService);

		ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
		graphExecutor = new ExecutionGraphExecutor(List.of(
			new HermesAgentExecutor(plannerService),
			new CodexAgentExecutor(executorManager, workspaceService, executionRecordManager,
				changeService, auditService),
			new OpenClawAgentExecutor(executorManager, executionRecordManager),
			new TestAgentExecutor(testAgentService),
			new RepairAgentExecutor(repairCoordinator)), auditService, taskCenterService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			workspaceService, changeService, graphBuilder, graphExecutor);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
	}

	@Test
	void shouldRunTaskThroughExecutionGraphToCompletion() throws Exception {
		TaskRecord task = createApprovedTask();

		ExecutionGraph graph = coordinator.executeGraph(task.getTaskId(),
			TaskType.CODE_GENERATION);

		assertNotNull(graph);
		assertTrue(graph.getNodes().stream().allMatch(
			node -> node.getStatus() == ExecutionNodeStatus.COMPLETED),
			"all nodes must complete: " + graph.getNodes());
		TaskRecord executed = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.COMPLETED, executed.getStatus());

		// Codex modified the workspace through the workspace path.
		assertTrue(Files.readString(repo.resolve("a.txt")).contains("two"));

		// Execution record for the coding node (HERMES/TEST nodes do not
		// persist execution records).
		List<ExecutionRecord> records = recordRepository.getAll();
		assertEquals(1, records.size());
		assertTrue(records.stream().allMatch(record ->
			task.getTaskId().equals(record.getTaskId())
				&& "SUCCESS".equals(record.getStatus())));

		// ChangeSet snapshot after the coding node.
		List<ChangeSet> changes = changeService.getChangesByTask(task.getTaskId());
		assertEquals(1, changes.size());
		assertEquals(ChangeStatus.CREATED, changes.get(0).getStatus());
		assertEquals(workspaceId, changes.get(0).getWorkspaceId());

		// Memory: HISTORY_TASK on success.
		List<MemoryRecord> history = memoryRepository.list(task.getProjectId(),
			MemoryType.HISTORY_TASK);
		assertEquals(1, history.size());
		assertEquals("history:task:" + task.getTaskId(), history.getFirst().getKey());

		// Audit: graph + node lifecycle events with metadata.
		assertEvent(EventType.GRAPH_CREATED, task.getTaskId());
		assertEvent(EventType.AGENT_SELECTED, task.getTaskId());
		assertEvent(EventType.NODE_STARTED, task.getTaskId());
		assertEvent(EventType.NODE_COMPLETED, task.getTaskId());
		assertTrue(events().stream().noneMatch(event -> event.type() == EventType.NODE_FAILED));
		EventRecord graphEvent = events().stream()
			.filter(event -> event.type() == EventType.GRAPH_CREATED).findFirst().orElseThrow();
		assertEquals(graph.getGraphId(), graphEvent.metadata().get("graphId"));

		// Timeline: Task -> Graph -> Node -> Agent -> Result.
		TimelineService timelineService = new TimelineService(auditRepository,
			mock(PlanRunRepository.class), new JobStore(), recordRepository,
			new TaskManager(), taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		for (String expected : List.of("GRAPH_CREATED", "AGENT_SELECTED", "NODE_STARTED",
			"NODE_COMPLETED", "EXECUTION_RECORD_SAVED", "TEST_SUCCEEDED")) {
			assertTrue(eventTypes.contains(expected), "missing " + expected + ": " + eventTypes);
		}

		// API: agent registry + task graph.
		MockMvc mockMvc = standaloneSetup(new AgentGraphController(
			new InMemoryAgentRegistry(), coordinator))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
		mockMvc.perform(get("/api/agents/registry"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].agentId").value("hermes"))
			.andExpect(jsonPath("$[0].status").value("ACTIVE"));
		mockMvc.perform(get("/api/tasks/" + task.getTaskId() + "/graph"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskId").value(task.getTaskId()))
			.andExpect(jsonPath("$.nodes[0].nodeId").value("HERMES_PLANNING"))
			.andExpect(jsonPath("$.nodes[2].status").value("COMPLETED"));
	}

	@Test
	void shouldMarkTaskFailedAndPersistBugRecordWhenTestFails() {
		TaskRecord task = createApprovedTask();
		runner.setFail(true);

		ExecutionGraph graph = coordinator.executeGraph(task.getTaskId(),
			TaskType.CODE_GENERATION);

		assertEquals(ExecutionNodeStatus.FAILED,
			graph.getNode("TEST_AGENT_VERIFY").getStatus());
		TaskRecord executed = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.FAILED, executed.getStatus());
		assertTrue(executed.getErrorMessage().contains("Tests failed"));
		assertEvent(EventType.NODE_FAILED, task.getTaskId());
		List<MemoryRecord> bugs = memoryRepository.list(task.getProjectId(),
			MemoryType.BUG_RECORD);
		assertEquals(1, bugs.size());
	}

	private void assertEvent(EventType type, String taskId) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type
			&& taskId.equals(event.taskId())), "missing audit event " + type);
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
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
		return taskCenterService.getTask(task.getTaskId()).orElseThrow();
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

	private static final class ConfigurableRunner implements TestCommandRunner {

		private volatile boolean fail;

		void setFail(boolean fail) {
			this.fail = fail;
		}

		@Override
		public TestCommandResult run(String command, String workdir) {
			if (fail) {
				return new TestCommandResult(1, "FAIL", "BUILD FAILURE");
			}
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
